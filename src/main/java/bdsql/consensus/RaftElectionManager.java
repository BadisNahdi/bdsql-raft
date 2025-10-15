package bdsql.consensus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import bdsql.consensus.rpc.RaftGrpc;
import bdsql.consensus.rpc.RequestVoteRequest;
import bdsql.consensus.rpc.RequestVoteResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class RaftElectionManager {
    private final String nodeId;
    private final ClusterInfo clusterInfo;
    private final RaftStateManager stateManager;
    private final RaftReplicationManager replicationManager;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("raft-election-" + t.getId());
        return t;
    });
    
    private final ExecutorService rpcExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("raft-vote-" + t.getId());
        return t;
    });
    
    private ScheduledFuture<?> electionTimeoutTask;
    private LogProvider logProvider;

    public RaftElectionManager(
            String nodeId,
            ClusterInfo clusterInfo,
            RaftStateManager stateManager,
            RaftReplicationManager replicationManager) {
        this.nodeId = nodeId;
        this.clusterInfo = clusterInfo;
        this.stateManager = stateManager;
        this.replicationManager = replicationManager;
    }

    public void setLogProvider(LogProvider logProvider) {
        this.logProvider = logProvider;
    }

    public void resetElectionTimeout() {
        if (electionTimeoutTask != null) {
            electionTimeoutTask.cancel(false);
        }
        long timeout = 1000 + new Random().nextInt(1000);
        electionTimeoutTask = scheduler.schedule(this::onElectionTimeout, timeout, TimeUnit.MILLISECONDS);
    }

    public void cancelElectionTimeout() {
        if (electionTimeoutTask != null) {
            electionTimeoutTask.cancel(true);
        }
    }

    private void onElectionTimeout() {
        stateManager.becomeCandidate();
        long newTerm = stateManager.getCurrentTerm();
        
        System.out.println(nodeId + " starting election for term " + newTerm);

        List<String> rawPeers = clusterInfo.getPeerAddressesExcept(nodeId);
        List<String> peers = new ArrayList<>(new LinkedHashSet<>(rawPeers));
        List<String> allNodes = new ArrayList<>(new LinkedHashSet<>(clusterInfo.getAllNodes()));

        System.out.println(nodeId + " peers (deduped) = " + peers + ", allNodes = " + allNodes);

        CountDownLatch latch = new CountDownLatch(peers.size());
        AtomicLong votes = new AtomicLong(1);

        for (String peer : peers) {
            rpcExecutor.execute(() -> {
                try {
                    ManagedChannel channel = getOrCreateChannel(peer);
                    RaftGrpc.RaftBlockingStub stub = RaftGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(2, TimeUnit.SECONDS);

                    RequestVoteRequest req = RequestVoteRequest.newBuilder()
                            .setCandidateId(nodeId)
                            .setTerm(newTerm)
                            .setLastLogIndex(logProvider.getLogSize() - 1)
                            .setLastLogTerm(logProvider.getLastLogTerm())
                            .build();

                    System.out.println(nodeId + " requesting vote from " + peer + " for term " + newTerm);
                    RequestVoteResponse resp = stub.requestVote(req);

                    if (resp.getVoteGranted()) {
                        System.out.println(nodeId + " got vote from " + peer);
                        votes.incrementAndGet();
                    } else {
                        System.out.println(nodeId + " vote denied by " + peer + " (term=" + resp.getTerm() + ")");
                        if (resp.getTerm() > stateManager.getCurrentTerm()) {
                            stateManager.becomeFollower(resp.getTerm());
                        }
                    }
                } catch (Exception e) {
                    System.err.println(nodeId + " failed to contact " + peer + " : " + 
                            e.getClass().getSimpleName() + " - " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        int majority = (allNodes.size() / 2) + 1;
        System.out.println(nodeId + " election votes=" + votes.get() + " need=" + majority);

        if (stateManager.isCandidate() && votes.get() >= majority) {
            becomeLeader();
        } else {
            stateManager.setState(RaftState.FOLLOWER);
            logProvider.clearPendingCommits();
            resetElectionTimeout();
        }
    }

    private void becomeLeader() {
        stateManager.becomeLeader();
        replicationManager.initializeIndices(logProvider.getLogSize());
        replicationManager.startHeartbeats();
    }

    private ManagedChannel getOrCreateChannel(String peer) {
        String[] parts = peer.split(":");
        String host = parts[0].trim();
        int port = Integer.parseInt(parts[1].trim());
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(4 * 1024 * 1024)
                .build();
    }

    public void shutdown() {
        cancelElectionTimeout();
        scheduler.shutdownNow();
        rpcExecutor.shutdownNow();
    }

    public interface LogProvider {
        int getLogSize();
        long getLastLogTerm();
        void clearPendingCommits();
    }
}