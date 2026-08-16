package org.divesh.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class StashService {

    private final Path repoRoot;
    private final ObjectStore objectStore;
    private final RefStore refStore;
    private final Index index;

    public StashService(Path repoRoot, ObjectStore objectStore,
                        RefStore refStore, Index index) {
        this.repoRoot    = repoRoot;
        this.objectStore = objectStore;
        this.refStore    = refStore;
        this.index       = index;
    }

    public void stash() throws Exception {
        // check if there's anything to stash
        Map<String, String> indexEntries = index.getEntries();
        if (indexEntries.isEmpty()) {
            System.out.println("No local changes to save");
            return;
        }

        // hash all current files from disk into blobs
        Map<String, String> currentFiles = new LinkedHashMap<>();
        for (String filepath : indexEntries.keySet()) {
            Path filePath = repoRoot.resolve(filepath);
            if (Files.exists(filePath)) {
                byte[] content = Files.readAllBytes(filePath);
                String sha = objectStore.hashObject(
                        content, "blob", true);
                currentFiles.put(filepath, sha);
            }
        }

        // build tree from current files
        String treeSha = objectStore.writeTree(currentFiles);

        // get current HEAD info for stash message
        String parentSha    = refStore.resolveHead();
        String branchName   = refStore.getCurrentBranch();
        String shortSha     = parentSha != null
                ? parentSha.substring(0, 7) : "unknown";

        // get last commit message for stash description
        String lastMessage = "no commits";
        if (parentSha != null) {
            GitObject obj = objectStore.readObject(parentSha);
            if (obj instanceof GitObject.Commit c) {
                lastMessage = c.message();
            }
        }

        // build stash commit content
        long timestamp = System.currentTimeMillis() / 1000;
        String author  = "Divesh <divesh@email.com>";
        String message = "WIP on " + branchName + ": "
                + shortSha + " " + lastMessage;

        StringBuilder commitContent = new StringBuilder();
        commitContent.append("tree ").append(treeSha).append("\n");
        if (parentSha != null) {
            commitContent.append("parent ")
                    .append(parentSha).append("\n");
        }
        commitContent.append("author ").append(author)
                .append(" ").append(timestamp)
                .append(" +0530\n");
        commitContent.append("committer ").append(author)
                .append(" ").append(timestamp)
                .append(" +0530\n");
        commitContent.append("\n").append(message).append("\n");

        // store stash commit
        byte[] commitBytes = commitContent.toString()
                .getBytes(StandardCharsets.UTF_8);
        String stashSha = objectStore.hashObject(
                commitBytes, "commit", true);

        // write to .git/refs/stash
        Path stashRef = repoRoot.resolve(".git")
                .resolve("refs")
                .resolve("stash");
        Files.writeString(stashRef, stashSha + "\n");

        // reset working directory to HEAD (hard reset)
        resetToHead(parentSha);

        System.out.println("Saved working directory state: "
                + message);
    }

    public void pop() throws Exception {
        Path stashRef = repoRoot.resolve(".git")
                .resolve("refs")
                .resolve("stash");

        if (!Files.exists(stashRef)) {
            System.out.println("No stash entries found");
            return;
        }

        String stashSha = Files.readString(stashRef).trim();

        // read stash commit
        GitObject obj = objectStore.readObject(stashSha);
        if (!(obj instanceof GitObject.Commit stashCommit)) {
            System.out.println("error: invalid stash");
            return;
        }

        // restore stash files to disk
        Map<String, String> stashFiles =
                getFilesFromTree(stashCommit.treeSha());

        for (Map.Entry<String, String> entry : stashFiles.entrySet()) {
            String filepath = entry.getKey();
            String sha      = entry.getValue();

            GitObject blobObj = objectStore.readObject(sha);
            if (blobObj instanceof GitObject.Blob blob) {
                Path target = repoRoot.resolve(filepath);
                Files.createDirectories(target.getParent());
                Files.write(target, blob.content());
                index.add(filepath, sha);
            }
        }

        // delete stash ref
        Files.delete(stashRef);

        System.out.println("Restored stash: "
                + stashCommit.message());
    }

    public void list() throws Exception {
        Path stashRef = repoRoot.resolve(".git")
                .resolve("refs")
                .resolve("stash");

        if (!Files.exists(stashRef)) {
            System.out.println("No stash entries");
            return;
        }

        String stashSha = Files.readString(stashRef).trim();
        GitObject obj   = objectStore.readObject(stashSha);

        if (obj instanceof GitObject.Commit c) {
            System.out.println("stash@{0}: " + c.message());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void resetToHead(String headSha) throws Exception {
        if (headSha == null) {
            index.clear();
            return;
        }

        // get HEAD files
        Map<String, String> headFiles = getFilesFromCommit(headSha);

        // get current files
        Map<String, String> currentFiles = index.getEntries();

        // delete files not in HEAD
        for (String filepath : currentFiles.keySet()) {
            if (!headFiles.containsKey(filepath)) {
                Files.deleteIfExists(repoRoot.resolve(filepath));
            }
        }

        // restore HEAD files to disk
        for (Map.Entry<String, String> entry : headFiles.entrySet()) {
            String filepath = entry.getKey();
            String sha      = entry.getValue();
            GitObject obj   = objectStore.readObject(sha);
            if (obj instanceof GitObject.Blob blob) {
                Path target = repoRoot.resolve(filepath);
                Files.createDirectories(target.getParent());
                Files.write(target, blob.content());
            }
        }

        // rebuild index from HEAD
        index.clear();
        for (Map.Entry<String, String> e : headFiles.entrySet()) {
            index.add(e.getKey(), e.getValue());
        }
    }

    private Map<String, String> getFilesFromCommit(
            String commitSha) throws Exception {

        GitObject obj = objectStore.readObject(commitSha);
        if (obj instanceof GitObject.Commit commit) {
            return getFilesFromTree(commit.treeSha());
        }
        return new LinkedHashMap<>();
    }

    private Map<String, String> getFilesFromTree(
            String treeSha) throws Exception {

        Map<String, String> result = new LinkedHashMap<>();
        collectFiles(treeSha, "", result);
        return result;
    }

    private void collectFiles(String treeSha,
                              String prefix,
                              Map<String, String> result) throws Exception {

        GitObject treeObj = objectStore.readObject(treeSha);
        if (!(treeObj instanceof GitObject.Tree tree)) return;

        for (GitObject.TreeEntry entry : tree.entries()) {
            String fullPath = prefix.isEmpty()
                    ? entry.name()
                    : prefix + "/" + entry.name();

            if (entry.mode().equals("040000")) {
                collectFiles(entry.sha(), fullPath, result);
            } else {
                result.put(fullPath, entry.sha());
            }
        }
    }
}