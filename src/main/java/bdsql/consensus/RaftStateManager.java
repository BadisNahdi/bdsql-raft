package bdsql.consensus;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import bdsql.storage.PersistentStateStore;
import bdsql.storage.PersistentStateStore.PersistentState;

public class RaftStateManager {
    private final String nodeId;
    private final PersistentStateStore persistentStateStore;
    private final AtomicLong currentTerm = new AtomicLong(0);
    private volatile String votedFor = null;
    private volatile RaftState state = RaftState.FOLLOWER;

    public RaftStateManager(String nodeId, PersistentStateStore persistentStateStore) throws IOException {
        this.nodeId = nodeId;
        this.persistentStateStore = persistentStateStore;
        
        PersistentState persisted = persistentStateStore.read();
        this.currentTerm.set(persisted.currentTerm());
        this.votedFor = persisted.votedFor();
    }

    public RaftState getState() {
        return state;
    }

    public void setState(RaftState state) {
        this.state = state;
    }

    public long getCurrentTerm() {
        return currentTerm.get();
    }

    public long incrementTerm() {
        return currentTerm.incrementAndGet();
    }

    public void setCurrentTerm(long term) {
        currentTerm.set(term);
    }

    public String getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(String candidateId) {
        this.votedFor = candidateId;
    }

    public boolean isLeader() {
        return state == RaftState.LEADER;
    }

    public boolean isCandidate() {
        return state == RaftState.CANDIDATE;
    }

    public boolean isFollower() {
        return state == RaftState.FOLLOWER;
    }

    public synchronized void persistState() {
        try {
            persistentStateStore.write(currentTerm.get(), votedFor);
        } catch (IOException e) {
            System.err.println(nodeId + " ERROR persisting Raft state: " + e.getMessage());
        }
    }

    public synchronized void becomeFollower(long term) {
        currentTerm.set(term);
        state = RaftState.FOLLOWER;
        votedFor = null;
        persistState();
    }

    public synchronized void becomeCandidate() {
        state = RaftState.CANDIDATE;
        currentTerm.incrementAndGet();
        votedFor = nodeId;
        persistState();
    }

    public synchronized void becomeLeader() {
        state = RaftState.LEADER;
        System.out.println(nodeId + " became leader for term " + currentTerm.get());
    }

    public synchronized boolean grantVote(long term, String candidateId) {
        if (term < currentTerm.get()) {
            return false;
        }

        if (term > currentTerm.get()) {
            currentTerm.set(term);
            state = RaftState.FOLLOWER;
            votedFor = null;
            persistState();
        }

        if (term == currentTerm.get()) {
            if (votedFor == null || votedFor.equals(candidateId)) {
                votedFor = candidateId;
                persistState();
                return true;
            }
        }

        return false;
    }
}