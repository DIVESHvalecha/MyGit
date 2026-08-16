package org.divesh.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class ObjectStore {
    Path gitDir;

    public ObjectStore(Path repoRoot) {
        this.gitDir = repoRoot.resolve(".git");
    }

    public String hashObject(byte[] content, String type, boolean write) throws NoSuchAlgorithmException, IOException {
        String header = type+ " " + content.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] full = new byte[headerBytes.length + content.length];

        System.arraycopy(headerBytes, 0, full, 0, headerBytes.length);

        System.arraycopy(content, 0, full, headerBytes.length, content.length);

        MessageDigest md = MessageDigest.getInstance("SHA-1");

        byte[] rawHash = md.digest(full);

        StringBuilder sha = new StringBuilder();
        for (byte b : rawHash) {
            sha.append(String.format("%02x", b));
        }
        String shaString = sha.toString();
        if (write){
            String folder   = shaString.substring(0, 2);
            String filename = shaString.substring(2);
            Path objectPath = gitDir.resolve("objects")
                    .resolve(folder)
                    .resolve(filename);

            Files.createDirectories(objectPath.getParent());
            try (DeflaterOutputStream dos = new DeflaterOutputStream(
                    Files.newOutputStream(objectPath))) {
                dos.write(full);
            }
        }
        return shaString;
    }

    public GitObject readObject(String sha) throws IOException {
        String folder   = sha.substring(0, 2);
        String filename = sha.substring(2);
        Path objectPath = gitDir.resolve("objects")
                .resolve(folder)
                .resolve(filename);

        byte[] compressed = Files.readAllBytes(objectPath);

        InflaterInputStream iis = new InflaterInputStream(
                new ByteArrayInputStream(compressed));
        byte[] full = iis.readAllBytes();
        iis.close();

        // find the \0 position
        int nullPos = 0;
        while (full[nullPos] != 0) nullPos++;

        String header = new String(full, 0, nullPos, StandardCharsets.UTF_8);

        byte[] content = Arrays.copyOfRange(full, nullPos + 1, full.length);

        // header is "blob 13" — split on space to get type
        String type = header.split(" ")[0];  // "blob"

        return switch (type) {
            case "blob"   -> new GitObject.Blob(content);
            case "tree"   -> parseTree(content);
            case "commit" -> parseCommit(content);
            case "tag"    -> parseTag(content);
            default       -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    private GitObject.Commit parseCommit(byte[] content) {
        String contentString = new String(content, StandardCharsets.UTF_8);
        String[] lines = contentString.split("\n");
        String treeSha    = null;
        String parentSha  = null;
        String author     = null;
        String committer  = null;
        StringBuilder message = new StringBuilder();
        boolean inMessage = false;

        for (String line : lines) {
            if (inMessage) {
                // everything after the blank line is the message
                if (!message.isEmpty()) message.append("\n");
                message.append(line);

            } else if (line.isEmpty()) {
                // blank line = message starts from next line
                inMessage = true;

            } else if (line.startsWith("tree ")) {
                treeSha = line.substring(5);

            } else if (line.startsWith("parent ")) {
                parentSha = line.substring(7);

            } else if (line.startsWith("author ")) {
                author = line.substring(7);

            } else if (line.startsWith("committer ")) {
                committer = line.substring(10);
            }
        }

        return new GitObject.Commit(
                treeSha,
                parentSha,
                author,
                committer,
                message.toString().trim()
        );
    }

    private GitObject.Tag parseTag(byte[] content) {
        String raw = new String(content, StandardCharsets.UTF_8);

        // 2. split into lines
        String[] lines = raw.split("\n");

        // 3. declare variables
        String objectSha = null;
        String name      = null;
        String tagger    = null;
        StringBuilder message = new StringBuilder();
        boolean inMessage = false;

        for (String line : lines) {
            if (inMessage) {
                if (!message.isEmpty()) message.append("\n");
                message.append(line);

            } else if (line.isEmpty()) {
                inMessage = true;

            } else if (line.startsWith("object ")) {
                objectSha = line.substring(7);

            } else if (line.startsWith("tag ")) {
                name = line.substring(4);

            } else if (line.startsWith("tagger ")) {
                tagger = line.substring(7);
            }
            // "type" line is skipped — always "commit", not needed
        }

        return new GitObject.Tag(
                objectSha,
                name,
                tagger,
                message.toString().trim()
        );
    }

    private GitObject.Tree parseTree(byte[] content) {
        List<GitObject.TreeEntry> entries = new ArrayList<>();
        int pos = 0;

        while (pos < content.length) {

            // step A — find the \0 to get mode + name
            int nullPos = pos;
            while (content[nullPos] != 0) nullPos++;

            // content[pos .. nullPos-1] = "100644 Hello.java"
            String modeAndName = new String(content, pos, nullPos - pos,
                    StandardCharsets.UTF_8);
            // split on first space only
            int spacePos = modeAndName.indexOf(' ');
            String mode  = modeAndName.substring(0, spacePos);
            String name  = modeAndName.substring(spacePos + 1);

            // step B — read 20 raw SHA bytes right after \0
            int shaStart = nullPos + 1;
            byte[] rawSha = Arrays.copyOfRange(content, shaStart, shaStart + 20);

            StringBuilder sha = new StringBuilder();
            for (byte b : rawSha) {
                sha.append(String.format("%02x", b));
            }

            // step D — add entry
            entries.add(new GitObject.TreeEntry(mode, name, sha.toString()));

            // step E — move past this entry
            pos = shaStart + 20;
        }

        return new GitObject.Tree(entries);
    }

    public String writeTree(Map<String, String> indexEntries) throws Exception {
        return writeTreeRecursive(indexEntries, "");
    }

    private String writeTreeRecursive(Map<String, String> indexEntries, String prefix) throws Exception {

        ByteArrayOutputStream treeContent = new ByteArrayOutputStream();

        // separate into direct files and subdirectories
        Map<String, String> directFiles = new LinkedHashMap<>();
        Map<String, Map<String, String>> subDirs = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : indexEntries.entrySet()) {
            String path = entry.getKey();
            String sha  = entry.getValue();

            // strip current prefix to get relative path
            String relative = prefix.isEmpty() ? path
                    : path.substring(prefix.length() + 1);

            if (!relative.contains("/")) {
                // direct file in this directory
                directFiles.put(relative, sha);
            } else {
                // file is inside a subdirectory
                String dirName = relative.substring(0, relative.indexOf("/"));
                subDirs.computeIfAbsent(dirName, k -> new LinkedHashMap<>())
                        .put(path, sha);
            }
        }

        // collect all entries for sorting
        Map<String, String> allEntries = new LinkedHashMap<>();

        // recursively build subtrees
        for (Map.Entry<String, Map<String, String>> dir : subDirs.entrySet()) {
            String dirName    = dir.getKey();
            String fullPrefix = prefix.isEmpty() ? dirName : prefix + "/" + dirName;
            String subTreeSha = writeTreeRecursive(dir.getValue(), fullPrefix);
            allEntries.put(dirName, "040000:" + subTreeSha);
        }

        // add direct files
        for (Map.Entry<String, String> file : directFiles.entrySet()) {
            allEntries.put(file.getKey(), "100644:" + file.getValue());
        }

        // sort alphabetically
        List<Map.Entry<String, String>> sorted = new ArrayList<>(allEntries.entrySet());
        sorted.sort(Map.Entry.comparingByKey());

        // write binary tree content
        for (Map.Entry<String, String> entry : sorted) {
            String name       = entry.getKey();
            String modeAndSha = entry.getValue();
            String[] parts    = modeAndSha.split(":");
            String mode       = parts[0];
            String sha        = parts[1];

            String modeAndName = mode + " " + name + "\0";
            treeContent.write(modeAndName.getBytes(StandardCharsets.UTF_8));
            treeContent.write(hexToBytes(sha));
        }

        return hashObject(treeContent.toByteArray(), "tree", true);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }
}
