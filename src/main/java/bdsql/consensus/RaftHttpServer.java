package bdsql.consensus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import bdsql.consensus.utilities.DASHBOARD_HTML;
import bdsql.storage.DocumentStore;
import bdsql.storage.KeyValueStore;

public class RaftHttpServer {
    private final String nodeId;
    private final RaftStateManager stateManager;
    private final RaftLogManager logManager;
    private final RaftReplicationManager replicationManager;
    private final KeyValueStore kvStore;
    private final DocumentStore documentStore;
    private final ClusterInfo clusterInfo;
    private HttpServer httpServer;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();


    public RaftHttpServer(
            String nodeId,
            RaftStateManager stateManager,
            RaftLogManager logManager,
            RaftReplicationManager replicationManager,
            KeyValueStore kvStore,
            DocumentStore documentStore,
            ClusterInfo clusterInfo) {
        this.nodeId = nodeId;
        this.stateManager = stateManager;
        this.logManager = logManager;
        this.replicationManager = replicationManager;
        this.kvStore = kvStore;
        this.documentStore = documentStore;
        this.clusterInfo = clusterInfo;
    }

    public void start(int httpPort) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);

        httpServer.createContext("/admin", this::handleAdminDashboard);

        httpServer.createContext("/api/status", this::handleStatus);
        httpServer.createContext("/api/cluster", this::handleCluster);
        httpServer.createContext("/api/log", this::handleLog);
        httpServer.createContext("/api/kv", this::handleKeyValue);
        httpServer.createContext("/api/doc", this::handleDocument);
        httpServer.createContext("/api/doc/index", this::handleDocumentIndex);

        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        System.out.println("HTTP client API + admin UI started at port " + httpPort);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    private void handleAdminDashboard(HttpExchange exchange) throws IOException {
        byte[] html = DASHBOARD_HTML.HTML.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, html.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(html);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        String resp = String.format(
                "{\"id\":\"%s\",\"state\":\"%s\",\"term\":%d,\"commitIndex\":%d,\"lastApplied\":%d}",
                nodeId,
                stateManager.getState(),
                stateManager.getCurrentTerm(),
                logManager.getCommitIndex(),
                logManager.getLastApplied());
        sendJson(exchange, 200, resp);
    }

    private void handleCluster(HttpExchange exchange) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"self\":\"").append(escapeJson(nodeId)).append("\",");
            sb.append("\"peers\":[");

            var peers = clusterInfo.getPeerAddressesExcept(nodeId);
            boolean first = true;
            for (String p : new LinkedHashSet<>(peers)) {
                if (!first)
                    sb.append(",");
                first = false;

                long ni = replicationManager.getNextIndex(p);
                long mi = replicationManager.getMatchIndex(p);
                long lastHb = replicationManager.getLastHeartbeat(p);

                sb.append("{\"addr\":\"").append(escapeJson(p))
                        .append("\",\"nextIndex\":").append(ni)
                        .append(",\"matchIndex\":").append(mi)
                        .append(",\"lastHeartbeat\":").append(lastHb)
                        .append("}");
            }
            sb.append("]");
            sb.append("}");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception ex) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    private void handleLog(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
            int count = 50;
            if (q.containsKey("count")) {
                try {
                    count = Integer.parseInt(q.get("count"));
                } catch (Exception ignored) {
                }
            }

            List<Map<String, Object>> slice = new ArrayList<>();
            List<LogEntry> logEntries = logManager.getRecentEntries(count);

            for (LogEntry le : logEntries) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("index", le.index());
                m.put("term", le.term());
                String dataStr;
                try {
                    dataStr = new String(le.data(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    dataStr = Arrays.toString(le.data());
                }
                if (dataStr.length() > 200) {
                    dataStr = dataStr.substring(0, 200) + "...";
                }
                m.put("data", dataStr);
                slice.add(m);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"entries\":[");
            boolean first = true;
            for (var e : slice) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append("{\"index\":").append(e.get("index"))
                        .append(",\"term\":").append(e.get("term"))
                        .append(",\"data\":\"").append(escapeJson((String) e.get("data"))).append("\"}");
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception ex) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    private void handleKeyValue(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                handleKvGet(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handleKvPost(exchange);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleKvDelete(exchange);
            } else {
                sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            }
        } catch (Exception ex) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    private void handleKvGet(HttpExchange exchange) throws IOException {
        Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
        String key = q.get("key");
        String val = key == null ? null : kvStore.get(key);

        if (val == null) {
            sendJson(exchange, 404, "{\"error\":\"not_found\"}");
        } else {
            sendJson(exchange, 200, "{\"value\":\"" + escapeJson(val) + "\"}");
        }
    }

    private void handleKvPost(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = queryToMap(body);
        String op = form.getOrDefault("op", form.get("operation"));

        if (op == null) {
            sendJson(exchange, 400, "{\"error\":\"missing op\"}");
            return;
        }

        op = op.toUpperCase(Locale.ROOT);

        if ("SET".equals(op)) {
            String key = form.get("key");
            String value = form.get("value");
            if (key == null || value == null) {
                sendJson(exchange, 400, "{\"error\":\"need key and value\"}");
                return;
            }

            String cmd = "SET " + key + " " + value;
            long idx = logManager.appendEntry(cmd.getBytes(StandardCharsets.UTF_8));

            if (idx < 0) {
                sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
            } else {
                sendJson(exchange, 200, "{\"index\":" + idx + "}");
            }
        } else if ("DEL".equals(op)) {
            String key = form.get("key");
            if (key == null) {
                sendJson(exchange, 400, "{\"error\":\"need key\"}");
                return;
            }

            String cmd = "DEL " + key;
            long idx = logManager.appendEntry(cmd.getBytes(StandardCharsets.UTF_8));

            if (idx < 0) {
                sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
            } else {
                sendJson(exchange, 200, "{\"index\":" + idx + "}");
            }
        } else {
            sendJson(exchange, 400, "{\"error\":\"unknown op\"}");
        }
    }

    private void handleKvDelete(HttpExchange exchange) throws IOException {
        Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
        String key = q.get("key");

        if (key == null) {
            sendJson(exchange, 400, "{\"error\":\"need key\"}");
            return;
        }

        String cmd = "DEL " + key;
        long idx = logManager.appendEntry(cmd.getBytes(StandardCharsets.UTF_8));

        if (idx < 0) {
            sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
        } else {
            sendJson(exchange, 200, "{\"index\":" + idx + "}");
        }
    }

    private void handleDocument(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleDocGet(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handleDocInsert(exchange);
            } else if ("PUT".equalsIgnoreCase(method)) {
                handleDocUpdate(exchange);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDocDelete(exchange);
            } else {
                sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            }
        } catch (Exception ex) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    private void handleDocInsert(HttpExchange exchange) throws IOException {
        String query = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
        String collection = q.getOrDefault("collection", null);

        com.google.gson.JsonObject root;
        if (collection != null && !collection.isEmpty()) {
            root = new com.google.gson.JsonObject();
            try {
                com.google.gson.JsonElement bodyElem = com.google.gson.JsonParser.parseString(query);
                if (!bodyElem.isJsonObject()) {
                    sendJson(exchange, 400,
                            "{\"error\":\"body must be a JSON object when collection query param is used\"}");
                    return;
                }
                root.addProperty("collection", collection);
                root.add("document", bodyElem.getAsJsonObject());
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"invalid JSON body\"}");
                return;
            }
        } else {
            try {
                root = com.google.gson.JsonParser.parseString(query).getAsJsonObject();
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"invalid JSON body; expected {collection,document}\"}");
                return;
            }
            if (!root.has("collection") || !root.has("document")) {
                sendJson(exchange, 400, "{\"error\":\"missing collection or document\"}");
                return;
            }
        }
        com.google.gson.JsonObject cmd = new com.google.gson.JsonObject();
        cmd.addProperty("op", "INSERT");
        cmd.addProperty("collection", root.get("collection").getAsString());
        cmd.add("document", root.get("document").getAsJsonObject());

        long idx = logManager.appendEntry(gson.toJson(cmd).getBytes(StandardCharsets.UTF_8));
        if (idx < 0) {
            sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
        } else {
            sendJson(exchange, 200, "{\"index\":" + idx + "}");
        }
    }

    private void handleDocGet(HttpExchange exchange) throws IOException {
        Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
        String collection = q.get("collection");
        if (collection == null || collection.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"need collection param\"}");
            return;
        }

        String id = q.get("id");
        if (id != null) {
            DocumentStore.Document doc = documentStore.findById(collection, id);
            if (doc == null) {
                sendJson(exchange, 404, "{\"error\":\"not_found\"}");
            } else {
                sendJson(exchange, 200, doc.toJson());
            }
            return;
        }

        String field = q.get("field");
        String value = q.get("value");
        int limit = 100;
        if (q.containsKey("limit")) {
            try {
                limit = Integer.parseInt(q.get("limit"));
            } catch (Exception ignored) {
            }
        }

        if (field != null && value != null) {
            DocumentStore.Query query = new DocumentStore.Query().where(field, value).limit(limit);
            List<DocumentStore.Document> docs = documentStore.find(collection, query);
            sendJson(exchange, 200, gson.toJson(docs.stream().map(DocumentStore.Document::getData).toArray()));
            return;
        }

        List<DocumentStore.Document> recent = documentStore.listCollections().contains(collection)
                ? documentStore.find(collection, new DocumentStore.Query().limit(limit))
                : List.of();
        sendJson(exchange, 200, gson.toJson(recent.stream().map(DocumentStore.Document::getData).toArray()));
    }

    private void handleDocUpdate(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        com.google.gson.JsonObject root;
        try {
            root = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"invalid JSON body\"}");
            return;
        }
        String collection = root.has("collection") ? root.get("collection").getAsString() : null;
        String id = root.has("id") ? root.get("id").getAsString() : null;
        if (collection == null || id == null) {
            sendJson(exchange, 400, "{\"error\":\"need collection and id\"}");
            return;
        }
        if (!root.has("updates")) {
            sendJson(exchange, 400, "{\"error\":\"need updates object\"}");
            return;
        }
        com.google.gson.JsonObject updates = root.get("updates").getAsJsonObject();

        com.google.gson.JsonObject cmd = new com.google.gson.JsonObject();
        cmd.addProperty("op", "UPDATE");
        cmd.addProperty("collection", collection);
        cmd.addProperty("id", id);
        cmd.add("updates", updates);

        long idx = logManager.appendEntry(gson.toJson(cmd).getBytes(StandardCharsets.UTF_8));
        if (idx < 0) {
            sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
        } else {
            sendJson(exchange, 200, "{\"index\":" + idx + "}");
        }
    }

    private void handleDocDelete(HttpExchange exchange) throws IOException {
        Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
        String collection = q.get("collection");
        String id = q.get("id");

        if (collection == null || id == null) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (body != null && !body.isEmpty()) {
                try {
                    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                    if (collection == null && root.has("collection"))
                        collection = root.get("collection").getAsString();
                    if (id == null && root.has("id"))
                        id = root.get("id").getAsString();
                } catch (Exception ignored) {
                }
            }
        }

        if (collection == null || id == null) {
            sendJson(exchange, 400, "{\"error\":\"need collection and id\"}");
            return;
        }

        com.google.gson.JsonObject cmd = new com.google.gson.JsonObject();
        cmd.addProperty("op", "DELETE");
        cmd.addProperty("collection", collection);
        cmd.addProperty("id", id);

        long idx = logManager.appendEntry(gson.toJson(cmd).getBytes(StandardCharsets.UTF_8));
        if (idx < 0) {
            sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
        } else {
            sendJson(exchange, 200, "{\"index\":" + idx + "}");
        }
    }

    private void handleDocumentIndex(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
            String collection = q.get("collection");
            String field = q.get("field");

            if ((collection == null || collection.isEmpty()) || (field == null || field.isEmpty())) {
                try {
                    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                    if (collection == null && root.has("collection"))
                        collection = root.get("collection").getAsString();
                    if (field == null && root.has("field"))
                        field = root.get("field").getAsString();
                } catch (Exception ignored) {
                }
            }

            if (collection == null || field == null) {
                sendJson(exchange, 400, "{\"error\":\"need collection and field\"}");
                return;
            }

            com.google.gson.JsonObject cmd = new com.google.gson.JsonObject();
            cmd.addProperty("op", "CREATE_INDEX");
            cmd.addProperty("collection", collection);
            cmd.addProperty("field", field);

            long idx = logManager.appendEntry(gson.toJson(cmd).getBytes(StandardCharsets.UTF_8));
            if (idx < 0) {
                sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
            } else {
                sendJson(exchange, 200, "{\"index\":" + idx + "}");
            }
        } catch (Exception ex) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> queryToMap(String query) {
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        return Arrays.stream(query.split("&"))
                .map(s -> s.split("=", 2))
                .filter(arr -> arr.length >= 1)
                .collect(Collectors.toMap(
                        arr -> HttpUtils.urlDecode(arr[0]),
                        arr -> arr.length == 2 ? HttpUtils.urlDecode(arr[1]) : ""));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}