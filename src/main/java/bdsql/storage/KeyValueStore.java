package bdsql.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {
    private final Path storeFile;
    private final ConcurrentHashMap<String, String> data = new ConcurrentHashMap<>();

    public KeyValueStore(Path storageDir) throws IOException {
        this.storeFile = storageDir.resolve("kvstore.log");
        Files.createDirectories(storageDir);
        loadFromDisk();
    }

    private void loadFromDisk() throws IOException {
        if (!Files.exists(storeFile)) return;

        try (BufferedReader reader = Files.newBufferedReader(storeFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ", 3);
                if (parts.length < 2) continue;

                String cmd = parts[0];
                String key = parts[1];
                if (cmd.equals("SET") && parts.length == 3) {
                    data.put(key, parts[2]);
                } else if (cmd.equals("DEL")) {
                    data.remove(key);
                }
            }
        }
    }

    public synchronized void set(String key, String value) throws IOException {
        data.put(key, value);
        appendToLog("SET " + key + " " + value);
    }

    public synchronized void delete(String key) throws IOException {
        data.remove(key);
        appendToLog("DEL " + key);
    }

    public String get(String key) {
        return data.get(key);
    }

    private void appendToLog(String line) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(storeFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.newLine();
        }
    }
}
