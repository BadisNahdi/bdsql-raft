package bdsql.storage;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class BTreeDocumentStore implements Closeable {
    private final DB db;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final ConcurrentMap<String, String> collectionsCatalog;
    private final ConcurrentMap<String, String> idGeneratorsMap;

    private final Map<String, ConcurrentMap<String, String>> primaryMaps = new HashMap<>();
    private final Map<String, Map<String, BTreeMap<String, Boolean>>> secondaryIndexes = new HashMap<>();

    public BTreeDocumentStore(Path storageDir) throws IOException {
        Files.createDirectories(storageDir);

        this.db = DBMaker.fileDB(storageDir.resolve("bdsql.db").toFile())
                .fileMmapEnableIfSupported() 
                .transactionEnable()
                .checksumHeaderBypass()
                .make();

        this.collectionsCatalog = db.hashMap("__collections_catalog__", Serializer.STRING, Serializer.STRING).createOrOpen();
        this.idGeneratorsMap = db.hashMap("__id_generators__", Serializer.STRING, Serializer.STRING).createOrOpen();

    }

    private synchronized ConcurrentMap<String, String> openPrimaryMap(String collection) {
        if (primaryMaps.containsKey(collection)) return primaryMaps.get(collection);
        String mapName = collectionsCatalog.get(collection);
        if (mapName == null) {
            mapName = "primary_" + collection;
            collectionsCatalog.put(collection, mapName);
            db.commit();
        }
        ConcurrentMap<String, String> map = db.hashMap(mapName, Serializer.STRING, Serializer.STRING).createOrOpen();
        primaryMaps.put(collection, map);
        return map;
    }

    private synchronized BTreeMap<String, Boolean> openSecondaryIndex(String collection, String field) {
        secondaryIndexes.putIfAbsent(collection, new HashMap<>());
        Map<String, BTreeMap<String, Boolean>> indexesForCollection = secondaryIndexes.get(collection);
        if (indexesForCollection.containsKey(field)) return indexesForCollection.get(field);

        String indexMapName = indexMapName(collection, field);
        BTreeMap<String, Boolean> idx = db.treeMap(indexMapName, Serializer.STRING, Serializer.BOOLEAN)
                .counterEnable()
                .createOrOpen();
        indexesForCollection.put(field, idx);
        return idx;
    }

    private String indexMapName(String collection, String field) {
        return "idx_" + collection + "_" + field;
    }

    public synchronized String insert(String collection, JsonObject document) {
        ConcurrentMap<String, String> primary = openPrimaryMap(collection);

        String id;
        if (document.has("_id")) {
            id = document.get("_id").getAsString();
        } else {
            id = generateId(collection);
            document.addProperty("_id", id);
        }
        long now = System.currentTimeMillis();
        document.addProperty("_createdAt", now);
        document.addProperty("_updatedAt", now);

        String json = gson.toJson(document);
        primary.put(id, json);

        autoIndexDocument(collection, id, document);

        db.commit();
        return id;
    }

    public synchronized boolean update(String collection, String id, JsonObject updates) {
        ConcurrentMap<String, String> primary = openPrimaryMap(collection);
        String existingJson = primary.get(id);
        if (existingJson == null) return false;

        JsonObject existing = JsonParser.parseString(existingJson).getAsJsonObject();
        removeFromIndexes(collection, id, existing);

        for (Map.Entry<String, JsonElement> e : updates.entrySet()) {
            existing.add(e.getKey(), e.getValue());
        }
        existing.addProperty("_updatedAt", System.currentTimeMillis());

        primary.put(id, gson.toJson(existing));
        autoIndexDocument(collection, id, existing);
        db.commit();
        return true;
    }

    public synchronized boolean delete(String collection, String id) {
        ConcurrentMap<String, String> primary = openPrimaryMap(collection);
        String existing = primary.remove(id);
        if (existing == null) return false;
        removeFromIndexes(collection, id, JsonParser.parseString(existing).getAsJsonObject());
        db.commit();
        return true;
    }

    public JsonObject findById(String collection, String id) {
        ConcurrentMap<String, String> primary = openPrimaryMap(collection);
        String json = primary.get(id);
        return json == null ? null : JsonParser.parseString(json).getAsJsonObject();
    }

    public List<JsonObject> find(String collection, Query query) {
        ConcurrentMap<String, String> primary = openPrimaryMap(collection);
        Set<String> candidateIds = findCandidateIdsUsingIndex(collection, query);
        Stream<String> idsStream;
        if (candidateIds != null) {
            idsStream = candidateIds.stream();
        } else {
            idsStream = primary.keySet().stream();
        }
        return idsStream
                .map(primary::get)
                .filter(Objects::nonNull)
                .map(s -> JsonParser.parseString(s).getAsJsonObject())
                .filter(query::matchesJson)
                .limit(query.getLimit())
                .collect(Collectors.toList());
    }

    public synchronized void createIndex(String collection, String field) {
        BTreeMap<String, Boolean> idx = openSecondaryIndex(collection, field);
        ConcurrentMap<String, String> primary = openPrimaryMap(collection);
        for (Map.Entry<String, String> e : primary.entrySet()) {
            JsonObject doc = JsonParser.parseString(e.getValue()).getAsJsonObject();
            if (doc.has(field)) {
                String key = compositeIndexKey(doc.get(field), e.getKey());
                idx.put(key, Boolean.TRUE);
            }
        }
        db.commit();
    }

    public List<String> listCollections() {
        return new ArrayList<>(collectionsCatalog.keySet());
    }

    public long count(String collection) {
        return openPrimaryMap(collection).size();
    }

    private void autoIndexDocument(String collection, String id, JsonObject document) {
        Map<String, BTreeMap<String, Boolean>> idxs = secondaryIndexes.get(collection);
        if (idxs == null) return;
        for (Map.Entry<String, BTreeMap<String, Boolean>> entry : idxs.entrySet()) {
            String field = entry.getKey();
            BTreeMap<String, Boolean> idx = entry.getValue();
            if (document.has(field)) {
                String key = compositeIndexKey(document.get(field), id);
                idx.put(key, Boolean.TRUE);
            }
        }
    }

    private void removeFromIndexes(String collection, String id, JsonObject document) {
        Map<String, BTreeMap<String, Boolean>> idxs = secondaryIndexes.get(collection);
        if (idxs == null) return;
        for (Map.Entry<String, BTreeMap<String, Boolean>> entry : idxs.entrySet()) {
            String field = entry.getKey();
            BTreeMap<String, Boolean> idx = entry.getValue();
            if (document.has(field)) {
                String key = compositeIndexKey(document.get(field), id);
                idx.remove(key);
            }
        }
    }

    private Set<String> findCandidateIdsUsingIndex(String collection, Query query) {
        Map<String, BTreeMap<String, Boolean>> idxs = secondaryIndexes.get(collection);
        if (idxs == null) return null;
        for (Map.Entry<String, Object> cond : query.getConditions().entrySet()) {
            String field = cond.getKey();
            BTreeMap<String, Boolean> idx = idxs.get(field);
            if (idx != null) {
                String desired = String.valueOf(cond.getValue());
                String from = desired + "|";
                String to = desired + "|\uFFFF";
                NavigableSet<String> keys = idx.keySet().subSet(from, true, to, true);
                Set<String> ids = keys.stream()
                        .map(k -> k.substring(k.indexOf('|') + 1))
                        .collect(Collectors.toSet());
                return ids;
            }
        }
        return null;
    }

    private String compositeIndexKey(JsonElement element, String id) {
        String val;
        if (element.isJsonPrimitive()) val = element.getAsJsonPrimitive().getAsString();
        else val = element.toString();
        return val + "|" + id;
    }

    private String generateId(String collection) {
        String stored = idGeneratorsMap.get(collection);
        long v = 0;
        if (stored != null) v = Long.parseLong(stored);
        v++;
        idGeneratorsMap.put(collection, Long.toString(v));
        db.commit();
        return collection + "_" + System.currentTimeMillis() + "_" + v;
    }

    @Override
    public void close() {
        db.close();
    }

    public static class Query {
        private final Map<String, Object> conditions = new HashMap<>();
        private int limit = Integer.MAX_VALUE;

        public Query where(String field, Object value) {
            conditions.put(field, value);
            return this;
        }

        public Query limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Map<String, Object> getConditions() {
            return conditions;
        }

        public int getLimit() { return limit; }

        public boolean matchesJson(JsonObject doc) {
            for (Map.Entry<String, Object> e : conditions.entrySet()) {
                String field = e.getKey();
                Object expected = e.getValue();
                if (!doc.has(field)) return false;
                JsonElement act = doc.get(field);
                Object actual = jsonElementToObject(act);
                if (!Objects.equals(expected, actual)) return false;
            }
            return true;
        }

        private Object jsonElementToObject(JsonElement element) {
            if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isString()) return element.getAsString();
                if (element.getAsJsonPrimitive().isNumber()) return element.getAsNumber();
                if (element.getAsJsonPrimitive().isBoolean()) return element.getAsBoolean();
            }
            return element.toString();
        }
    }
}
