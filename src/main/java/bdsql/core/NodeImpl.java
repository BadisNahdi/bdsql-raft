package bdsql.core;

public class NodeImpl implements Node {
    private final String id;

    public NodeImpl(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void start() {
        System.out.println("Node " + id + " started.");
    }

    @Override
    public void stop() {
        System.out.println("Node " + id + " stopped.");
    }
}