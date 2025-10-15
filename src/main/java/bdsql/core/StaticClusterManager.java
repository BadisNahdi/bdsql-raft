package bdsql.core;

import java.util.ArrayList;
import java.util.List;

public class StaticClusterManager implements ClusterManager {
    private final List<Node> nodes = new ArrayList<>();
    private Node leader;

    @Override
    public void registerNode(Node node) {
        nodes.add(node);
        System.out.println("Registered node: " + node.getId());
    }

    @Override
    public List<Node> getAllNodes() {
        return nodes;
    }

    @Override
    public Node getLeader() {
        return leader;
    }

    @Override
    public void setLeader(Node leader) {
        this.leader = leader;
        System.out.println("Leader is now: " + leader.getId());
    }
}