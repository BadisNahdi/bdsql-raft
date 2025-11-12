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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import bdsql.consensus.utilities.DASHBOARD_HTML;
import bdsql.consensus.utilities.SWAGGER_HTML;
import bdsql.storage.BTreeDocumentStore;
import bdsql.storage.BTreeDocumentStore.Query;
import bdsql.storage.KeyValueStore;

public class RaftHttpServer {
    private final String nodeId;
    private final RaftStateManager stateManager;
    private final RaftLogManager logManager;
    private final RaftReplicationManager replicationManager;
    private final KeyValueStore kvStore;
    private final BTreeDocumentStore documentStore;
    private final ClusterInfo clusterInfo;
    private HttpServer httpServer;
    private final Gson gson = new Gson();

    public RaftHttpServer(
            String nodeId,
            RaftStateManager stateManager,
            RaftLogManager logManager,
            RaftReplicationManager replicationManager,
            KeyValueStore kvStore,
            BTreeDocumentStore documentStore,
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
        httpServer.createContext("/swagger", this::handleSwagger);

        httpServer.createContext("/api/status", this::handleStatus);
        httpServer.createContext("/api/cluster", this::handleCluster);
        httpServer.createContext("/api/log", this::handleLog);
        httpServer.createContext("/api/log/full", this::handleFullLog);
        httpServer.createContext("/api/doc", this::handleDocument);
        httpServer.createContext("/api/doc/index", this::handleDocumentIndex);

        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        System.out.println("HTTP client API + admin UI started at port " + httpPort);
        System.out.println("Swagger UI available at: http://localhost:" + httpPort + "/swagger");
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

    private void handleSwagger(HttpExchange exchange) throws IOException {
        byte[] html = SWAGGER_HTML.HTML.getBytes(StandardCharsets.UTF_8);
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

    private void handleFullLog(HttpExchange exchange) throws IOException {
        try {
            List<Map<String, Object>> allEntries = new ArrayList<>();
            List<LogEntry> logEntries = logManager.getAllEntries();

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
                m.put("data", dataStr);
                allEntries.add(m);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"totalEntries\":").append(allEntries.size()).append(",\"entries\":[");
            boolean first = true;
            for (var e : allEntries) {
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

    private void handleDocGet(HttpExchange exchange) throws IOException {
        Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
        String collection = q.get("collection");
        String id = q.get("id");

        if (collection == null || collection.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"need collection\"}");
            return;
        }

        if (id != null && !id.isEmpty()) {
            JsonObject doc = documentStore.findById(collection, id);
            if (doc == null) {
                sendJson(exchange, 404, "{\"error\":\"not_found\"}");
            } else {
                sendJson(exchange, 200, gson.toJson(doc));
            }
            return;
        }

        if (q.containsKey("field") && q.containsKey("value")) {
            String field = q.get("field");
            String value = q.get("value");
            Query query = new Query().where(field, value);
            List<JsonObject> docs = documentStore.find(collection, query);
            sendJson(exchange, 200, gson.toJson(docs));
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!body.isEmpty()) {
            try {
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                Query query = new Query();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    query.where(e.getKey(), jsonElementToObject(e.getValue()));
                }
                List<JsonObject> docs = documentStore.find(collection, query);
                sendJson(exchange, 200, gson.toJson(docs));
                return;
            } catch (Exception ignored) {
            }
        }

        List<JsonObject> docs = documentStore.find(collection, new Query().limit(100));
        sendJson(exchange, 200, gson.toJson(docs));
    }

    private void handleDocInsert(HttpExchange exchange) throws IOException {
        String queryBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
        String collection = q.getOrDefault("collection", null);

        JsonObject root;
        if (collection != null && !collection.isEmpty()) {
            root = new JsonObject();
            try {
                JsonElement bodyElem = JsonParser.parseString(queryBody);
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
                root = JsonParser.parseString(queryBody).getAsJsonObject();
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"invalid JSON body; expected {collection,document}\"}");
                return;
            }
            if (!root.has("collection") || !root.has("document")) {
                sendJson(exchange, 400, "{\"error\":\"missing collection or document\"}");
                return;
            }
        }

        JsonObject cmd = new JsonObject();
        cmd.addProperty("op", "INSERT");
        cmd.addProperty("collection", root.get("collection").getAsString());
        cmd.add("document", root.get("document").getAsJsonObject());

        long idx = logManager.appendEntry(gson.toJson(cmd).getBytes(StandardCharsets.UTF_8));
        if (idx < 0) {
            Optional<String> maybeLeader = replicationManager.getLikelyLeader();
            if (maybeLeader.isPresent()) {
                String leaderAddr = maybeLeader.get();
                String location = HttpUtils.leaderHttpUrl(leaderAddr, exchange.getRequestURI().getPath()
                        + (exchange.getRequestURI().getQuery() != null ? "?" + exchange.getRequestURI().getQuery()
                                : ""));
                if (location != null) {
                    exchange.getResponseHeaders().set("Location", location);
                    String errorBody = "{\"error\":\"not_leader\",\"redirect\":\"" + escapeJson(location) + "\"}";
                    sendJson(exchange, 307, errorBody);
                    return;
                }
            }
            sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
            return;
        }
        sendJson(exchange, 200, "{\"index\":" + idx + "}");
    }

    private void handleDocUpdate(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
        String collection = q.get("collection");
        String id = q.get("id");

        JsonObject updates;
        if ((collection == null || id == null) || body.isEmpty()) {
            try {
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                if (collection == null && root.has("collection"))
                    collection = root.get("collection").getAsString();
                if (id == null && root.has("id"))
                    id = root.get("id").getAsString();
                if (root.has("updates"))
                    updates = root.get("updates").getAsJsonObject();
                else if (root.has("document"))
                    updates = root.get("document").getAsJsonObject();
                else
                    updates = new JsonObject();
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"invalid JSON body\"}");
                return;
            }
        } else {
            try {
                updates = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"invalid JSON body for updates\"}");
                return;
            }
        }

        if (collection == null || id == null) {
            sendJson(exchange, 400, "{\"error\":\"need collection and id\"}");
            return;
        }

        JsonObject cmd = new JsonObject();
        cmd.addProperty("op", "UPDATE");
        cmd.addProperty("collection", collection);
        cmd.addProperty("id", id);
        cmd.add("updates", updates);

        long idx = logManager.appendEntry(gson.toJson(cmd).getBytes(StandardCharsets.UTF_8));
        if (idx < 0) {
            Optional<String> maybeLeader = replicationManager.getLikelyLeader();
            if (maybeLeader.isPresent()) {
                String leaderAddr = maybeLeader.get();
                String location = HttpUtils.leaderHttpUrl(leaderAddr, exchange.getRequestURI().getPath()
                        + (exchange.getRequestURI().getQuery() != null ? "?" + exchange.getRequestURI().getQuery()
                                : ""));
                if (location != null) {
                    exchange.getResponseHeaders().set("Location", location);
                    String errorBody = "{\"error\":\"not_leader\",\"redirect\":\"" + escapeJson(location) + "\"}";
                    sendJson(exchange, 307, errorBody);
                    return;
                }
            }
            sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
            return;
        }
        sendJson(exchange, 200, "{\"index\":" + idx + "}");
    }

    private void handleDocDelete(HttpExchange exchange) throws IOException {
        Map<String, String> q = queryToMap(exchange.getRequestURI().getRawQuery());
        String collection = q.get("collection");
        String id = q.get("id");

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if ((collection == null || id == null) && !body.isEmpty()) {
            try {
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                if (collection == null && root.has("collection"))
                    collection = root.get("collection").getAsString();
                if (id == null && root.has("id"))
                    id = root.get("id").getAsString();
            } catch (Exception ignored) {
            }
        }

        if (collection == null || id == null) {
            sendJson(exchange, 400, "{\"error\":\"need collection and id\"}");
            return;
        }

        JsonObject cmd = new JsonObject();
        cmd.addProperty("op", "DELETE");
        cmd.addProperty("collection", collection);
        cmd.addProperty("id", id);

        long idx = logManager.appendEntry(gson.toJson(cmd).getBytes(StandardCharsets.UTF_8));
        if (idx < 0) {
            Optional<String> maybeLeader = replicationManager.getLikelyLeader();
            if (maybeLeader.isPresent()) {
                String leaderAddr = maybeLeader.get();
                String location = HttpUtils.leaderHttpUrl(leaderAddr, exchange.getRequestURI().getPath()
                        + (exchange.getRequestURI().getQuery() != null ? "?" + exchange.getRequestURI().getQuery()
                                : ""));
                if (location != null) {
                    exchange.getResponseHeaders().set("Location", location);
                    String errorBody = "{\"error\":\"not_leader\",\"redirect\":\"" + escapeJson(location) + "\"}";
                    sendJson(exchange, 307, errorBody);
                    return;
                }
            }
            sendJson(exchange, 500, "{\"error\":\"not_leader_or_failed\"}");
            return;
        }
        sendJson(exchange, 200, "{\"index\":" + idx + "}");
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
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
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

            JsonObject cmd = new JsonObject();
            cmd.addProperty("op", "CREATE_INDEX");
            cmd.addProperty("collection", collection);
            cmd.addProperty("field", field);

            long idx = logManager.appendEntry(gson.toJson(cmd).getBytes(StandardCharsets.UTF_8));
            if (idx < 0) {
                Optional<String> maybeLeader = replicationManager.getLikelyLeader();
                if (maybeLeader.isPresent()) {
                    String leader = maybeLeader.get();
                    String location = HttpUtils.leaderHttpUrl(leader, exchange.getRequestURI().getPath());
                    if (location != null) {
                        exchange.getResponseHeaders().set("Location", location);
                        String errorBody = "{\"error\":\"not_leader\",\"redirect\":\"" + escapeJson(location) + "\"}";
                        sendJson(exchange, 307, errorBody);
                        return;
                    }
                }
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

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

    private static Object jsonElementToObject(JsonElement element) {
        if (element == null)
            return null;
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString())
                return element.getAsString();
            if (element.getAsJsonPrimitive().isNumber())
                return element.getAsNumber();
            if (element.getAsJsonPrimitive().isBoolean())
                return element.getAsBoolean();
        }
        return element.toString();
    }
}