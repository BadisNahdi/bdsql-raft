package bdsql.consensus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import bdsql.storage.WriteAheadLog;
import bdsql.storage.WriteAheadLog.WALRecord;

public class RaftLogManager {
    private final String nodeId;
    private final WriteAheadLog wal;
    private final List<LogEntry> log = Collections.synchronizedList(new ArrayList<>());
    private final Consumer<LogEntry> applyFn;
    private final ConcurrentHashMap<Long, CompletableFuture<Boolean>> pendingCommits = new ConcurrentHashMap<>();
    
    private volatile long commitIndex = 0;
    private volatile long lastApplied = 0;
    private final long commitTimeoutMs = 3000L;
    
    private final RaftStateManager stateManager;
    private final RaftReplicationManager replicationManager;

    public RaftLogManager(
            String nodeId,
            WriteAheadLog wal,
            Consumer<LogEntry> applyFn,
            RaftStateManager stateManager,
            RaftReplicationManager replicationManager) throws IOException {
        this.nodeId = nodeId;
        this.wal = wal;
        this.applyFn = applyFn;
        this.stateManager = stateManager;
        this.replicationManager = replicationManager;
        
        initializeLog();
    }

    private void initializeLog() throws IOException {
        log.clear();
        log.add(new LogEntry(0, 0, new byte[0]));
        
        List<WALRecord> records = wal.readAll();
        for (WALRecord r : records) {
            long expected = log.size();
            if (r.index() != expected) {
                System.err.println(nodeId + " WAL unexpected index: " + r.index() + " expected " + expected
                        + " — truncating remaining WAL");
                try {
                    wal.truncateSuffixFrom(expected - 1);
                } catch (IOException ioe) {
                    System.err.println(nodeId + " failed to truncate WAL after unexpected index: " + ioe.getMessage());
                }
                break;
            }
            log.add(new LogEntry((int) r.index(), r.term(), r.data()));
        }
    }

    public long getCommitIndex() {
        return commitIndex;
    }

    public long getLastApplied() {
        return lastApplied;
    }

    public int getLogSize() {
        synchronized (log) {
            return log.size();
        }
    }

    public LogEntry getEntry(int index) {
        synchronized (log) {
            if (index >= 0 && index < log.size()) {
                return log.get(index);
            }
            return null;
        }
    }

    public List<LogEntry> getRecentEntries(int count) {
        synchronized (log) {
            int size = log.size();
            int start = Math.max(0, size - count);
            return new ArrayList<>(log.subList(start, size));
        }
    }

    public long appendEntry(byte[] data) {
        if (!stateManager.isLeader()) {
            System.err.println("appendEntry: not leader");
            return -1;
        }

        long idx;
        long term = stateManager.getCurrentTerm();
        
        synchronized (log) {
            try {
                long appended = wal.appendWithExpectedIndex(wal.getLastIndex() + 1, term, data);
                idx = appended;
                log.add(new LogEntry((int) appended, term, data));
                replicationManager.updateMatchIndex(nodeId, appended);
                replicationManager.updateNextIndex(nodeId, appended + 1);
            } catch (IOException ioe) {
                System.err.println(nodeId + " failed to persist WAL entry: " + ioe.getMessage());
                return -1;
            }
        }

        CompletableFuture<Boolean> f = new CompletableFuture<>();
        pendingCommits.put(idx, f);

        replicationManager.triggerReplication();
        tryCommit();

        try {
            boolean ok = f.get(commitTimeoutMs, TimeUnit.MILLISECONDS);
            pendingCommits.remove(idx);
            return ok ? idx : -1;
        } catch (Exception e) {
            pendingCommits.remove(idx);
            System.err.println(nodeId + " appendEntry timeout/failed for idx " + idx + ": " + e.getMessage());
            return -1;
        }
    }

    public synchronized AppendEntriesResult handleAppendEntries(
            long prevIndex,
            long prevTerm,
            List<bdsql.consensus.rpc.LogEntry> entries,
            long leaderCommit) {
        
        synchronized (log) {
            if (prevIndex >= log.size()) {
                return new AppendEntriesResult(false, "prevIndex out of bounds");
            }
            
            if (log.get((int) prevIndex).term() != prevTerm) {
                return new AppendEntriesResult(false, "term mismatch at prevIndex");
            }

            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                long incomingIdx = e.getIndex();
                long incomingTerm = e.getTerm();

                if (incomingIdx < log.size()) {
                    if (log.get((int) incomingIdx).term() != incomingTerm) {
                        while (log.size() > incomingIdx) {
                            log.remove(log.size() - 1);
                        }
                        try {
                            wal.truncateSuffixFrom(log.size() - 1);
                        } catch (IOException ioe) {
                            System.err.println(nodeId + " failed to truncate WAL during conflict resolution: " + ioe.getMessage());
                            return new AppendEntriesResult(false, "WAL truncation failed");
                        }
                    }
                }

                if (incomingIdx >= log.size()) {
                    byte[] data = e.getData().toByteArray();
                    try {
                        long appended = wal.appendWithExpectedIndex(wal.getLastIndex() + 1, incomingTerm, data);
                        if (appended != incomingIdx) {
                            System.err.println(nodeId + " WAL appended index mismatch: appended=" + appended + " expected=" + incomingIdx);
                            wal.truncateSuffixFrom(incomingIdx - 1);
                            return new AppendEntriesResult(false, "index mismatch");
                        }
                        log.add(new LogEntry((int) appended, incomingTerm, data));
                    } catch (IOException ioe) {
                        System.err.println(nodeId + " WAL append failed for incoming entry: " + ioe.getMessage());
                        return new AppendEntriesResult(false, "WAL append failed");
                    }
                }
            }

            if (leaderCommit > commitIndex) {
                commitIndex = Math.min(leaderCommit, log.size() - 1);
                applyEntries();
            }
        }

        return new AppendEntriesResult(true, null);
    }

    public void tryCommit() {
        if (!stateManager.isLeader()) {
            return;
        }

        long lastIndex;
        synchronized (log) {
            lastIndex = log.size() - 1;
        }
        
        for (long n = commitIndex + 1; n <= lastIndex; n++) {
            final long candidate = n;
            long termAtN = log.get((int) candidate).term();
            
            if (termAtN != stateManager.getCurrentTerm()) {
                continue;
            }

            int replicaCount = replicationManager.countReplicasWithIndex(candidate);
            
            if (replicaCount >= replicationManager.getMajorityCount()) {
                commitIndex = candidate;
                applyEntries();
            }
        }
    }

    public void applyEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry e;
            synchronized (log) {
                e = log.get((int) lastApplied);
            }
            try {
                applyFn.accept(e);
            } catch (Exception ex) {
                System.err.println("apply failed: " + ex.getMessage());
            }
            
            CompletableFuture<Boolean> f = pendingCommits.remove(lastApplied);
            if (f != null) {
                f.complete(Boolean.TRUE);
            }
        }
    }

    public void clearPendingCommits() {
        for (var entry : pendingCommits.entrySet()) {
            entry.getValue().complete(Boolean.FALSE);
        }
        pendingCommits.clear();
    }

    public void close() throws IOException {
        wal.close();
    }

    public static class AppendEntriesResult {
        private final boolean success;
        private final String error;

        public AppendEntriesResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }
    }
}