package bdsql;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import bdsql.consensus.ClusterInfo;
import bdsql.consensus.RaftNode;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.err.println("Usage: Main <nodeId> <host:port> <peer1> <peer2> ...");
            return;
        }
        String nodeName = args[0];
        String hostPort = args[1];
        int port = Integer.parseInt(hostPort.split(":")[1]);
        String host = (hostPort.split(":")[0]);
        List<String> nodes = Arrays.asList(args).subList(1, args.length);
        ClusterInfo cluster = new ClusterInfo(nodes);
        RaftNode node = new RaftNode(nodeName, host, port, cluster, null);

        node.start();

        Thread.currentThread().join();
    }
}
