package bdsql.core;

import java.util.List;

public interface ClusterManager {
    void registerNode(Node node);
    List<Node> getAllNodes();
    Node getLeader();
    void setLeader(Node leader);
}
