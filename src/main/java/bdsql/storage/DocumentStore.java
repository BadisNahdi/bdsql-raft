package bdsql.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DocumentStore {
    private final Path storeFile;
    private final Path indexDir;
    private final Gson gson;
    
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Document>> collections = new ConcurrentHashMap<>();
    
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<Object, Set<String>>>> indexes = new ConcurrentHashMap<>();
    
    private final ConcurrentHashMap<String, AtomicLong> idGenerators = new ConcurrentHashMap<>();
    
    private final ConcurrentHashMap<String, Set<String>> indexedFields = new ConcurrentHashMap<>();

    public DocumentStore(Path storageDir) throws IOException {
        this.storeFile = storageDir.resolve("documents.log");
        this.indexDir = storageDir.resolve("indexes");
        Files.createDirectories(storageDir);
        Files.createDirectories(indexDir);
        
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        loadFromDisk();
        rebuildIndexes();
    }

    private void loadFromDisk() throws IOException {
        if (!Files.exists(storeFile)) return;

        try (BufferedReader reader = Files.newBufferedReader(storeFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonObject logEntry = JsonParser.parseString(line).getAsJsonObject();
                    String operation = logEntry.get("op").getAsString();
                    String collection = logEntry.get("collection").getAsString();
                    
                    switch (operation) {
                        case "INSERT":
                            JsonObject doc = logEntry.get("document").getAsJsonObject();
                            String id = doc.get("_id").getAsString();
                            Document document = new Document(id, doc);
                            collections.computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                                      .put(id, document);
                            break;
                            
                        case "UPDATE":
                            String updateId = logEntry.get("id").getAsString();
                            JsonObject updateDoc = logEntry.get("document").getAsJsonObject();
                            collections.computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                                      .put(updateId, new Document(updateId, updateDoc));
                            break;
                            
                        case "DELETE":
                            String deleteId = logEntry.get("id").getAsString();
                            collections.computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                                      .remove(deleteId);
                            break;
                            
                        case "CREATE_INDEX":
                            String field = logEntry.get("field").getAsString();
                            indexedFields.computeIfAbsent(collection, k -> ConcurrentHashMap.newKeySet())
                                        .add(field);
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse log entry: " + line + " - " + e.getMessage());
                }
            }
        }
    }

    private void rebuildIndexes() {
        for (Map.Entry<String, ConcurrentHashMap<String, Document>> collectionEntry : collections.entrySet()) {
            String collection = collectionEntry.getKey();
            Set<String> fieldsToIndex = indexedFields.getOrDefault(collection, Collections.emptySet());
            
            for (Document doc : collectionEntry.getValue().values()) {
                indexDocument(collection, doc, fieldsToIndex);
            }
        }
    }

    public synchronized String insert(String collection, JsonObject document) throws IOException {
        String id;
        if (document.has("_id")) {
            id = document.get("_id").getAsString();
        } else {
            id = generateId(collection);
            document.addProperty("_id", id);
        }
        
        document.addProperty("_createdAt", System.currentTimeMillis());
        document.addProperty("_updatedAt", System.currentTimeMillis());
        
        Document doc = new Document(id, document);
        collections.computeIfAbsent(collection, k -> new ConcurrentHashMap<>()).put(id, doc);
        
        autoIndexDocument(collection, doc);
        
        JsonObject logEntry = new JsonObject();
        logEntry.addProperty("op", "INSERT");
        logEntry.addProperty("collection", collection);
        logEntry.add("document", document);
        appendToLog(logEntry);
        
        return id;
    }

    public synchronized boolean update(String collection, String id, JsonObject updates) throws IOException {
        ConcurrentHashMap<String, Document> coll = collections.get(collection);
        if (coll == null || !coll.containsKey(id)) {
            return false;
        }
        
        Document existingDoc = coll.get(id);
        JsonObject merged = existingDoc.getData().deepCopy();
        
        removeFromIndexes(collection, existingDoc);
        
        for (Map.Entry<String, JsonElement> entry : updates.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }
        
        merged.addProperty("_updatedAt", System.currentTimeMillis());
        
        Document newDoc = new Document(id, merged);
        coll.put(id, newDoc);
        
        autoIndexDocument(collection, newDoc);
        
        JsonObject logEntry = new JsonObject();
        logEntry.addProperty("op", "UPDATE");
        logEntry.addProperty("collection", collection);
        logEntry.addProperty("id", id);
        logEntry.add("document", merged);
        appendToLog(logEntry);
        
        return true;
    }

    public synchronized boolean delete(String collection, String id) throws IOException {
        ConcurrentHashMap<String, Document> coll = collections.get(collection);
        if (coll == null) {
            return false;
        }
        
        Document doc = coll.remove(id);
        if (doc == null) {
            return false;
        }
        
        removeFromIndexes(collection, doc);
        
        JsonObject logEntry = new JsonObject();
        logEntry.addProperty("op", "DELETE");
        logEntry.addProperty("collection", collection);
        logEntry.addProperty("id", id);
        appendToLog(logEntry);
        
        return true;
    }

    public Document findById(String collection, String id) {
        ConcurrentHashMap<String, Document> coll = collections.get(collection);
        return coll != null ? coll.get(id) : null;
    }

    public List<Document> find(String collection, Query query) {
        ConcurrentHashMap<String, Document> coll = collections.get(collection);
        if (coll == null) {
            return Collections.emptyList();
        }
        
        Set<String> candidateIds = findCandidateIdsUsingIndex(collection, query);
        
        if (candidateIds != null) {
            return candidateIds.stream()
                    .map(coll::get)
                    .filter(Objects::nonNull)
                    .filter(doc -> query.matches(doc))
                    .limit(query.getLimit())
                    .collect(Collectors.toList());
        } else {
            return coll.values().stream()
                    .filter(query::matches)
                    .limit(query.getLimit())
                    .collect(Collectors.toList());
        }
    }

    public synchronized void createIndex(String collection, String field) throws IOException {
        indexedFields.computeIfAbsent(collection, k -> ConcurrentHashMap.newKeySet()).add(field);
        
        ConcurrentHashMap<String, Document> coll = collections.get(collection);
        if (coll != null) {
            for (Document doc : coll.values()) {
                indexField(collection, field, doc);
            }
        }
        
        JsonObject logEntry = new JsonObject();
        logEntry.addProperty("op", "CREATE_INDEX");
        logEntry.addProperty("collection", collection);
        logEntry.addProperty("field", field);
        appendToLog(logEntry);
    }

    public List<String> listCollections() {
        return new ArrayList<>(collections.keySet());
    }

    public long count(String collection) {
        ConcurrentHashMap<String, Document> coll = collections.get(collection);
        return coll != null ? coll.size() : 0;
    }

    private void autoIndexDocument(String collection, Document doc) {
        indexField(collection, "_id", doc);
        
        Set<String> fieldsToIndex = indexedFields.getOrDefault(collection, Collections.emptySet());
        indexDocument(collection, doc, fieldsToIndex);
        
        for (String commonField : Arrays.asList("_createdAt", "_updatedAt", "email", "username", "status")) {
            if (doc.getData().has(commonField)) {
                indexField(collection, commonField, doc);
            }
        }
    }

    private void indexDocument(String collection, Document doc, Set<String> fields) {
        for (String field : fields) {
            indexField(collection, field, doc);
        }
    }

    private void indexField(String collection, String field, Document doc) {
        if (!doc.getData().has(field)) {
            return;
        }
        
        JsonElement element = doc.getData().get(field);
        Object value = jsonElementToIndexableValue(element);
        
        indexes.computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
               .computeIfAbsent(field, k -> new ConcurrentHashMap<>())
               .computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet())
               .add(doc.getId());
    }

    private void removeFromIndexes(String collection, Document doc) {
        ConcurrentHashMap<String, ConcurrentHashMap<Object, Set<String>>> collectionIndexes = indexes.get(collection);
        if (collectionIndexes == null) {
            return;
        }
        
        for (Map.Entry<String, JsonElement> entry : doc.getData().entrySet()) {
            String field = entry.getKey();
            ConcurrentHashMap<Object, Set<String>> fieldIndex = collectionIndexes.get(field);
            if (fieldIndex != null) {
                Object value = jsonElementToIndexableValue(entry.getValue());
                Set<String> ids = fieldIndex.get(value);
                if (ids != null) {
                    ids.remove(doc.getId());
                }
            }
        }
    }

    private Set<String> findCandidateIdsUsingIndex(String collection, Query query) {
        ConcurrentHashMap<String, ConcurrentHashMap<Object, Set<String>>> collectionIndexes = indexes.get(collection);
        if (collectionIndexes == null) {
            return null;
        }
        
        for (Map.Entry<String, Object> condition : query.getConditions().entrySet()) {
            String field = condition.getKey();
            ConcurrentHashMap<Object, Set<String>> fieldIndex = collectionIndexes.get(field);
            if (fieldIndex != null) {
                Set<String> ids = fieldIndex.get(condition.getValue());
                return ids != null ? new HashSet<>(ids) : Collections.emptySet();
            }
        }
        
        return null;
    }

    private Object jsonElementToIndexableValue(JsonElement element) {
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                return element.getAsString();
            } else if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsNumber();
            } else if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
        }
        return element.toString();
    }

    private String generateId(String collection) {
        AtomicLong generator = idGenerators.computeIfAbsent(collection, k -> new AtomicLong(0));
        return collection + "_" + System.currentTimeMillis() + "_" + generator.incrementAndGet();
    }

    private void appendToLog(JsonObject logEntry) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(storeFile, 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(gson.toJson(logEntry));
            writer.newLine();
        }
    }

    public static class Document {
        private final String id;
        private final JsonObject data;

        public Document(String id, JsonObject data) {
            this.id = id;
            this.data = data;
        }

        public String getId() {
            return id;
        }

        public JsonObject getData() {
            return data;
        }

        public String toJson() {
            return new GsonBuilder().setPrettyPrinting().create().toJson(data);
        }

        @Override
        public String toString() {
            return toJson();
        }
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

        public int getLimit() {
            return limit;
        }

        public Map<String, Object> getConditions() {
            return conditions;
        }

        public boolean matches(Document doc) {
            for (Map.Entry<String, Object> condition : conditions.entrySet()) {
                String field = condition.getKey();
                Object expectedValue = condition.getValue();
                
                if (!doc.getData().has(field)) {
                    return false;
                }
                
                JsonElement element = doc.getData().get(field);
                Object actualValue = jsonElementToObject(element);
                
                if (!Objects.equals(expectedValue, actualValue)) {
                    return false;
                }
            }
            return true;
        }

        private Object jsonElementToObject(JsonElement element) {
            if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isString()) {
                    return element.getAsString();
                } else if (element.getAsJsonPrimitive().isNumber()) {
                    return element.getAsNumber();
                } else if (element.getAsJsonPrimitive().isBoolean()) {
                    return element.getAsBoolean();
                }
            }
            return element.toString();
        }
    }
}