package bdsql.consensus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import bdsql.consensus.rpc.AppendEntriesRequest;
import bdsql.consensus.rpc.AppendEntriesResponse;
import bdsql.consensus.rpc.RaftGrpc;
import bdsql.consensus.rpc.RequestVoteRequest;
import bdsql.consensus.rpc.RequestVoteResponse;
import bdsql.storage.BTreeDocumentStore;
import bdsql.storage.KeyValueStore;
import bdsql.storage.PersistentStateStore;
import bdsql.storage.WriteAheadLog;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class RaftNode {
    private final String id;
    private final int port;
    private final String host;
    private final ClusterInfo clusterInfo;

    private final RaftStateManager stateManager;
    private final RaftLogManager logManager;
    private final RaftReplicationManager replicationManager;
    private final RaftElectionManager electionManager;
    private final RaftHttpServer httpServer;
    private final BTreeDocumentStore documentStore;
    private final KeyValueStore kvStore;

    private Server grpcServer;

    public RaftNode(String id, String host, int port, ClusterInfo clusterInfo, Consumer<LogEntry> applyFn)
            throws IOException {
        this.id = id;
        this.host = host;
        this.port = port;
        this.clusterInfo = clusterInfo;
        String safeId = HttpUtils.sanitizeForFilename(id);
        Path storageDir = Path.of("data").resolve(safeId);
        Files.createDirectories(storageDir);

        this.kvStore = new KeyValueStore(storageDir.resolve("state"));

        PersistentStateStore persistentStateStore = new PersistentStateStore(storageDir);
        WriteAheadLog wal = new WriteAheadLog(storageDir.resolve("wal"));

        this.documentStore = new BTreeDocumentStore(storageDir.resolve("documents"));

        this.stateManager = new RaftStateManager(id, host, port, persistentStateStore);

        this.replicationManager = new RaftReplicationManager(id, clusterInfo, stateManager);

        Consumer<LogEntry> finalApplyFn = applyFn != null ? applyFn : createDocumentApplyFunction();

        this.logManager = new RaftLogManager(id, wal, finalApplyFn, stateManager, replicationManager);

        replicationManager.setLogProvider(new ReplicationLogProvider());

        this.electionManager = new RaftElectionManager(id, clusterInfo, stateManager, replicationManager);
        electionManager.setLogProvider(new ElectionLogProvider());

        this.httpServer = new RaftHttpServer(id, stateManager, logManager, replicationManager, kvStore, documentStore,
                clusterInfo);
    }

    private Consumer<LogEntry> createDocumentApplyFunction() {
        return entry -> {
            try {
                String commandJson = new String(entry.data(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (commandJson.isEmpty()) {
                    return;
                }

                JsonObject command = JsonParser.parseString(commandJson).getAsJsonObject();
                String operation = command.get("op").getAsString();
                String collection = command.get("collection").getAsString();

                switch (operation) {
                    case "INSERT":
                        JsonObject document = command.get("document").getAsJsonObject();
                        documentStore.insert(collection, document);
                        break;

                    case "UPDATE":
                        String updateId = command.get("id").getAsString();
                        JsonObject updates = command.get("updates").getAsJsonObject();
                        documentStore.update(collection, updateId, updates);
                        break;

                    case "DELETE":
                        String deleteId = command.get("id").getAsString();
                        documentStore.delete(collection, deleteId);
                        break;

                    case "CREATE_INDEX":
                        String field = command.get("field").getAsString();
                        documentStore.createIndex(collection, field);
                        break;

                    default:
                        System.err.println(id + " unknown operation: " + operation);
                }
            } catch (Exception ex) {
                System.err.println(id + " applyFn exception: " + ex.getMessage());
                ex.printStackTrace();
            }
        };
    }

    public void start() throws IOException {
        grpcServer = ServerBuilder.forPort(port)
                .addService(new RaftGrpc.RaftImplBase() {
                    @Override
                    public void requestVote(RequestVoteRequest req, StreamObserver<RequestVoteResponse> respObs) {
                        RequestVoteResponse resp = handleRequestVote(req);
                        respObs.onNext(resp);
                        respObs.onCompleted();
                    }

                    @Override
                    public void appendEntries(AppendEntriesRequest req, StreamObserver<AppendEntriesResponse> respObs) {
                        AppendEntriesResponse resp = handleAppendEntries(req);
                        respObs.onNext(resp);
                        respObs.onCompleted();
                    }
                })
                .build()
                .start();

        System.out.println("Raft node " + id + " listening on " + port);

        electionManager.resetElectionTimeout();

        try {
            httpServer.start(port + 1000);
        } catch (IOException ioe) {
            System.err.println(id + " failed to start HTTP API: " + ioe.getMessage());
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }

    public void stop() {
        electionManager.shutdown();
        replicationManager.shutdown();

        if (grpcServer != null) {
            grpcServer.shutdown();
        }

        try {
            logManager.close();
        } catch (IOException ignored) {
        }

        httpServer.stop();

        documentStore.close();

    }

    private synchronized RequestVoteResponse handleRequestVote(RequestVoteRequest req) {
        long term = req.getTerm();
        boolean voteGranted = stateManager.grantVote(term, req.getCandidateId());

        if (voteGranted) {
            electionManager.resetElectionTimeout();
        }

        return RequestVoteResponse.newBuilder()
                .setTerm(stateManager.getCurrentTerm())
                .setVoteGranted(voteGranted)
                .build();
    }

    private synchronized AppendEntriesResponse handleAppendEntries(AppendEntriesRequest req) {
        long term = req.getTerm();

        if (term > stateManager.getCurrentTerm()) {
            stateManager.becomeFollower(term);
            logManager.clearPendingCommits();
        }

        if (term < stateManager.getCurrentTerm()) {
            return AppendEntriesResponse.newBuilder()
                    .setTerm(stateManager.getCurrentTerm())
                    .setSuccess(false)
                    .build();
        }

        electionManager.resetElectionTimeout();

        String leaderId = req.getLeaderId();
        if (leaderId != null && !leaderId.isEmpty()) {
            replicationManager.recordHeartbeat(leaderId);
        }

        RaftLogManager.AppendEntriesResult result = logManager.handleAppendEntries(
                req.getPrevLogIndex(),
                req.getPrevLogTerm(),
                req.getEntriesList(),
                req.getLeaderCommit());

        return AppendEntriesResponse.newBuilder()
                .setTerm(stateManager.getCurrentTerm())
                .setSuccess(result.isSuccess())
                .build();
    }

    private class ReplicationLogProvider implements RaftReplicationManager.LogProvider {
        @Override
        public int getLogSize() {
            return logManager.getLogSize();
        }

        @Override
        public LogEntry getEntry(int index) {
            return logManager.getEntry(index);
        }

        @Override
        public long getCommitIndex() {
            return logManager.getCommitIndex();
        }

        @Override
        public void tryCommit() {
            logManager.tryCommit();
        }

        @Override
        public void clearPendingCommits() {
            logManager.clearPendingCommits();
        }
    }

    private class ElectionLogProvider implements RaftElectionManager.LogProvider {
        @Override
        public int getLogSize() {
            return logManager.getLogSize();
        }

        @Override
        public long getLastLogTerm() {
            LogEntry lastEntry = logManager.getEntry(logManager.getLogSize() - 1);
            return lastEntry != null ? lastEntry.term() : 0;
        }

        @Override
        public void clearPendingCommits() {
            logManager.clearPendingCommits();
        }
    }
}