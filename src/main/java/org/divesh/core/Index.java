package org.divesh.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Index {
    private final Path indexFile;

    public Index(Path repoRoot) {
        this.indexFile = repoRoot.resolve(".git").resolve("index");
    }

    public Map<String , String> load() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();

        // if index doesn't exist yet — no files staged, return empty map
        if (!Files.exists(indexFile)) return entries;

        String content = Files.readString(indexFile);
        String[] lines = content.split("\n");

        for (String line : lines) {
            // skip empty lines
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("  ", 2);
            if (parts.length == 2) {
                String sha      = parts[0].trim();
                String filepath = parts[1].trim();
                entries.put(filepath, sha);
            }
        }

        return entries;
    }

    public void save(Map<String, String> entries) throws IOException {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            // format: "{sha}  {filepath}\n"
            sb.append(entry.getValue())
                    .append("  ")
                    .append(entry.getKey())
                    .append("\n");
        }

        Files.writeString(indexFile, sb.toString());
    }

    public void add(String filepath, String sha) throws IOException {
        // load current state
        Map<String, String> entries = load();

        // add or overwrite entry
        entries.put(filepath, sha);

        // persist back to disk
        save(entries);
    }

    public Map<String, String> getEntries() throws IOException {
        return load();
    }

    public void remove(String filepath) throws IOException {
        Map<String, String> entries = load();
        entries.remove(filepath);
        save(entries);
    }

    public boolean contains(String filepath) throws IOException {
        return load().containsKey(filepath);
    }

    public void clear() throws IOException {
        save(new LinkedHashMap<>());
    }
}
