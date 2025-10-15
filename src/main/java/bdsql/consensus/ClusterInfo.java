package bdsql.consensus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ClusterInfo {
    private final List<String> nodes;

    public ClusterInfo(List<String> nodes) {
        this.nodes = new ArrayList<>(nodes);
    }

    public List<String> getAllNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<String> getPeerAddressesExcept(String id) {
        return nodes.stream().filter(n -> !n.equals(id)).collect(Collectors.toList());
    }
}
