package bdsql.consensus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import bdsql.consensus.rpc.AppendEntriesRequest;
import bdsql.consensus.rpc.AppendEntriesResponse;
import bdsql.consensus.rpc.RaftGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class RaftReplicationManager {
    private final String nodeId;
    private final ClusterInfo clusterInfo;
    private final RaftStateManager stateManager;
    private final Map<String, Long> nextIndex = new ConcurrentHashMap<>();
    private final Map<String, Long> matchIndex = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> peerLastHeartbeat = new ConcurrentHashMap<>();
    private final Map<String, ManagedChannel> peerChannels = new ConcurrentHashMap<>();
    
    private final ExecutorService rpcExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("raft-rpc-" + t.getId());
        return t;
    });

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("raft-heartbeat-" + t.getId());
        return t;
    });
    
    private ScheduledFuture<?> heartbeatTask;
    private LogProvider logProvider;

    public RaftReplicationManager(String nodeId, ClusterInfo clusterInfo, RaftStateManager stateManager) {
        this.nodeId = nodeId;
        this.clusterInfo = clusterInfo;
        this.stateManager = stateManager;
    }

    public void setLogProvider(LogProvider logProvider) {
        this.logProvider = logProvider;
    }

    public void initializeIndices(int logSize) {
        for (String nodeAddr : clusterInfo.getAllNodes()) {
            nextIndex.put(nodeAddr, (long) logSize);
            matchIndex.put(nodeAddr, 0L);
        }
        matchIndex.put(nodeId, (long) logSize - 1);
        nextIndex.put(nodeId, (long) logSize);
    }

    public void updateNextIndex(String peer, long value) {
        nextIndex.put(peer, value);
    }

    public void updateMatchIndex(String peer, long value) {
        matchIndex.put(peer, value);
    }

    public long getNextIndex(String peer) {
        return nextIndex.getOrDefault(peer, 0L);
    }

    public long getMatchIndex(String peer) {
        return matchIndex.getOrDefault(peer, 0L);
    }

    public long getLastHeartbeat(String peer) {
        AtomicLong lastHb = peerLastHeartbeat.get(peer);
        return lastHb != null ? lastHb.get() : 0L;
    }

    public void recordHeartbeat(String peer) {
        peerLastHeartbeat.computeIfAbsent(peer, k -> new AtomicLong()).set(System.currentTimeMillis());
    }

    public int countReplicasWithIndex(long index) {
        int count = 1;
        for (String peer : clusterInfo.getPeerAddressesExcept(nodeId)) {
            Long peerMatch = matchIndex.getOrDefault(peer, 0L);
            if (peerMatch >= index) {
                count++;
            }
        }
        return count;
    }

    public int getMajorityCount() {
        return (clusterInfo.getAllNodes().size() / 2) + 1;
    }

    public void startHeartbeats() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(this::sendHeartbeats, 0, 150, TimeUnit.MILLISECONDS);
    }

    public void stopHeartbeats() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
    }

    public void triggerReplication() {
        List<String> peers = clusterInfo.getPeerAddressesExcept(nodeId);
        for (String peer : peers) {
            rpcExecutor.execute(() -> replicateToPeer(peer));
        }
    }

    private void sendHeartbeats() {
        if (!stateManager.isLeader()) {
            return;
        }
        triggerReplication();
    }

    public void replicateToPeer(String peer) {
        if (peer.equals(nodeId)) {
            return;
        }
        
        try {
            ManagedChannel channel = getOrCreateChannel(peer);
            RaftGrpc.RaftBlockingStub stub = RaftGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(1000, TimeUnit.MILLISECONDS);

            long nextIdx = nextIndex.getOrDefault(peer, (long) logProvider.getLogSize());
            long prevIdx = nextIdx - 1;
            long prevTerm = 0;
            
            LogEntry prevEntry = logProvider.getEntry((int) prevIdx);
            if (prevEntry != null) {
                prevTerm = prevEntry.term();
            }

            AppendEntriesRequest.Builder builder = AppendEntriesRequest.newBuilder()
                    .setLeaderId(nodeId)
                    .setTerm(stateManager.getCurrentTerm())
                    .setPrevLogIndex(prevIdx)
                    .setPrevLogTerm(prevTerm)
                    .setLeaderCommit(logProvider.getCommitIndex());

            int entriesSent = 0;
            for (long i = nextIdx; i < logProvider.getLogSize(); i++) {
                LogEntry le = logProvider.getEntry((int) i);
                if (le != null) {
                    builder.addEntries(bdsql.consensus.rpc.LogEntry.newBuilder()
                            .setIndex(le.index())
                            .setTerm(le.term())
                            .setData(com.google.protobuf.ByteString.copyFrom(le.data()))
                            .build());
                    entriesSent++;
                }
            }

            AppendEntriesRequest req = builder.build();
            AppendEntriesResponse resp = stub.appendEntries(req);

            if (resp.getSuccess()) {
                recordHeartbeat(peer);
                
                long newMatch;
                if (entriesSent > 0) {
                    newMatch = nextIdx + entriesSent - 1;
                } else {
                    newMatch = prevIdx;
                }
                matchIndex.put(peer, newMatch);
                nextIndex.put(peer, newMatch + 1);
                logProvider.tryCommit();
            } else {
                long ni = nextIndex.getOrDefault(peer, (long) logProvider.getLogSize());
                long newNi = Math.max(1, ni - 1);
                nextIndex.put(peer, newNi);
            }

            if (resp.getTerm() > stateManager.getCurrentTerm()) {
                stateManager.becomeFollower(resp.getTerm());
                logProvider.clearPendingCommits();
                stopHeartbeats();
            }
        } catch (Exception e) {
        }
    }

    private ManagedChannel getOrCreateChannel(String peer) {
        return peerChannels.computeIfAbsent(peer, p -> {
            String[] parts = p.split(":");
            String host = parts[0].trim();
            int port = Integer.parseInt(parts[1].trim());
            return ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .maxInboundMessageSize(4 * 1024 * 1024)
                    .build();
        });
    }

    public void shutdown() {
        stopHeartbeats();
        scheduler.shutdownNow();
        
        for (ManagedChannel c : peerChannels.values()) {
            if (c != null && !c.isShutdown()) {
                c.shutdownNow();
            }
        }
        peerChannels.clear();
        rpcExecutor.shutdownNow();
    }

    public interface LogProvider {
        int getLogSize();
        LogEntry getEntry(int index);
        long getCommitIndex();
        void tryCommit();
        void clearPendingCommits();
    }
}