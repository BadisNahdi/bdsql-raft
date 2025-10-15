package bdsql;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import bdsql.consensus.ClusterInfo;
import bdsql.consensus.RaftNode;

public class MainCli {
    public static void main(String[] argv) throws Exception {
        if (argv.length == 0) {
            System.out.println("Usage:");
            System.out.println("  start-node <nodeName> <host:port> <peer1> <peer2> ...   -- starts node in this JVM");
            System.out.println("  shell <httpHost:port>                                   -- connect to node HTTP API");
            return;
        }
        String cmd = argv[0];
        if ("start-node".equals(cmd)) {
            if (argv.length < 3) { System.err.println("start-node needs nodeName and host:port"); return; }
            String nodeName = argv[1];
            String hostPort = argv[2];
            List<String> peers = new ArrayList<>();
            for (int i = 2; i < argv.length; i++) peers.add(argv[i]);
            System.out.println(peers);
            ClusterInfo cluster = new ClusterInfo(peers);
            String[] hp = hostPort.split(":");
            int port = Integer.parseInt(hp[1]);
            RaftNode node = new RaftNode(nodeName, port, cluster, null);
            node.start();
            System.out.println("Node started. HTTP client API at port " + (port + 1000));
            node.blockUntilShutdown();
        } else if ("shell".equals(cmd)) {
            if (argv.length < 2) { System.err.println("shell needs httpHost:port"); return; }
            String http = argv[1];
            runShell(http);
        } else {
            System.err.println("Unknown command: " + cmd);
        }
    }

    private static void runShell(String httpHostPort) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Connected to " + httpHostPort + ". Commands: put key value | get key | del key | info | exit");
        while (true) {
            System.out.print("> ");
            String line = in.readLine();
            if (line == null) break;
            String[] parts = line.trim().split(" ", 3);
            if (parts.length == 0) continue;
            String op = parts[0].toLowerCase(Locale.ROOT);
            if ("exit".equals(op) || "quit".equals(op)) break;
            if ("put".equals(op) && parts.length == 3) {
                String key = parts[1], value = parts[2];
                String body = "op=SET&key=" + urlEncode(key) + "&value=" + urlEncode(value);
                String resp = httpPost("http://" + httpHostPort + "/kv", body);
                System.out.println(resp);
            } else if ("get".equals(op) && parts.length >= 2) {
                String key = parts[1];
                String resp = httpGet("http://" + httpHostPort + "/kv?key=" + urlEncode(key));
                System.out.println(resp);
            } else if ("del".equals(op) && parts.length >= 2) {
                String key = parts[1];
                String resp = httpDelete("http://" + httpHostPort + "/kv?key=" + urlEncode(key));
                System.out.println(resp);
            } else if ("info".equals(op)) {
                System.out.println(httpGet("http://" + httpHostPort + "/status"));
            } else {
                System.out.println("unknown command");
            }
        }
        System.out.println("bye");
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        try (var in = new java.io.InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8);
             var br = new java.io.BufferedReader(in)) {
            return br.lines().collect(java.util.stream.Collectors.joining("\n"));
        } catch (Exception ex) {
            if (c.getErrorStream() != null) {
                try (var in = new java.io.InputStreamReader(c.getErrorStream(), StandardCharsets.UTF_8);
                     var br = new java.io.BufferedReader(in)) {
                    return "ERROR: " + br.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
            }
            throw ex;
        }
    }

    private static String httpPost(String urlStr, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        c.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (var os = c.getOutputStream()) { os.write(bytes); }
        try (var in = new java.io.InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8);
             var br = new java.io.BufferedReader(in)) {
            return br.lines().collect(java.util.stream.Collectors.joining("\n"));
        } catch (Exception ex) {
            if (c.getErrorStream() != null) {
                try (var in = new java.io.InputStreamReader(c.getErrorStream(), StandardCharsets.UTF_8);
                     var br = new java.io.BufferedReader(in)) {
                    return "ERROR: " + br.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
            }
            throw ex;
        }
    }

    private static String httpDelete(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("DELETE");
        try (var in = new java.io.InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8);
             var br = new java.io.BufferedReader(in)) {
            return br.lines().collect(java.util.stream.Collectors.joining("\n"));
        } catch (Exception ex) {
            if (c.getErrorStream() != null) {
                try (var in = new java.io.InputStreamReader(c.getErrorStream(), StandardCharsets.UTF_8);
                     var br = new java.io.BufferedReader(in)) {
                    return "ERROR: " + br.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
            }
            throw ex;
        }
    }

    private static String urlEncode(String s) { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
