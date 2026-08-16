package org.divesh.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MergeService {

    private final Path repoRoot;
    private final ObjectStore objectStore;
    private final RefStore refStore;
    private final Index index;

    public MergeService(Path repoRoot, ObjectStore objectStore,
                        RefStore refStore, Index index) {
        this.repoRoot    = repoRoot;
        this.objectStore = objectStore;
        this.refStore    = refStore;
        this.index       = index;
    }

    public void merge(String branchName) throws Exception {
        String currentSha    = refStore.resolveHead();
        String currentBranch = refStore.getCurrentBranch();

        Path branchFile = repoRoot.resolve(".git")
                .resolve("refs")
                .resolve("heads")
                .resolve(branchName);

        if (!Files.exists(branchFile)) {
            System.out.println("error: branch '"
                    + branchName + "' not found");
            return;
        }

        String targetSha = Files.readString(branchFile).trim();

        if (currentSha.equals(targetSha)) {
            System.out.println("Already up to date.");
            return;
        }

        if (isFastForward(currentSha, targetSha)) {
            doFastForward(targetSha, branchName, currentBranch);
            return;
        }

        doThreeWayMerge(currentSha, targetSha, currentBranch, branchName);
    }

    private boolean isFastForward(String currentSha, String targetSha) throws Exception {
        String sha = targetSha;
        while (sha != null) {
            if (sha.equals(currentSha)) return true;
            GitObject obj = objectStore.readObject(sha);
            if (obj instanceof GitObject.Commit c) {
                sha = c.parentSha();
            } else break;
        }
        return false;
    }

    private void doFastForward(String targetSha, String branchName, String currentBranch) throws Exception {
        restoreTree(targetSha);
        refStore.updateHead(targetSha);
        System.out.println("Fast-forward");
        System.out.println("Merged '" + branchName
                + "' into '" + currentBranch + "'.");
    }

    private void restoreTree(String commitSha) throws Exception {
        Map<String, String> currentFiles = index.getEntries();
        GitObject commitObj = objectStore.readObject(commitSha);
        if (!(commitObj instanceof GitObject.Commit commit)) return;

        Map<String, String> targetFiles = new LinkedHashMap<>();
        collectFlatEntries(commit.treeSha(), "", targetFiles);

        // delete files not in target
        for (String filepath : currentFiles.keySet()) {
            if (!targetFiles.containsKey(filepath)) {
                Files.deleteIfExists(repoRoot.resolve(filepath));
            }
        }

        // delete empty directories
        for (String filepath : currentFiles.keySet()) {
            if (!targetFiles.containsKey(filepath)) {
                Path parent = repoRoot.resolve(filepath).getParent();
                if (!parent.equals(repoRoot)
                        && Files.isDirectory(parent)) {
                    try (var list = Files.list(parent)) {
                        if (list.findAny().isEmpty()) {
                            Files.deleteIfExists(parent);
                        }
                    }
                }
            }
        }

        index.clear();
        restoreTreeEntries(commit.treeSha(), repoRoot);
    }

    private void restoreTreeEntries(String treeSha, Path targetDir) throws Exception {

        GitObject treeObj = objectStore.readObject(treeSha);
        if (!(treeObj instanceof GitObject.Tree tree)) return;

        for (GitObject.TreeEntry entry : tree.entries()) {
            Path entryPath = targetDir.resolve(entry.name());
            if (entry.mode().equals("040000")) {
                Files.createDirectories(entryPath);
                restoreTreeEntries(entry.sha(), entryPath);
            } else {
                GitObject blobObj = objectStore.readObject(entry.sha());
                if (!(blobObj instanceof GitObject.Blob blob)) continue;
                Files.createDirectories(entryPath.getParent());
                Files.write(entryPath, blob.content());
                String relative = repoRoot.relativize(entryPath)
                        .toString();
                index.add(relative, entry.sha());
            }
        }
    }

    private void doThreeWayMerge(String ourSha, String theirSha, String ourBranch, String theirBranch) throws Exception {

        String ancestorSha = findCommonAncestor(ourSha, theirSha);
        if (ancestorSha == null) {
            System.out.println("error: no common ancestor found");
            return;
        }

        Map<String, String> ancestorFiles = getCommittedEntries(ancestorSha);
        Map<String, String> ourFiles      = getCommittedEntries(ourSha);
        Map<String, String> theirFiles    = getCommittedEntries(theirSha);

        Set<String> allFiles = new LinkedHashSet<>();
        allFiles.addAll(ancestorFiles.keySet());
        allFiles.addAll(ourFiles.keySet());
        allFiles.addAll(theirFiles.keySet());

        boolean hasConflicts = false;
        Map<String, String> mergedIndex = new LinkedHashMap<>();

        for (String filepath : allFiles) {
            String ancestorFile = ancestorFiles.get(filepath);
            String ourFile      = ourFiles.get(filepath);
            String theirFile    = theirFiles.get(filepath);

            // case 1 — same in both
            if (Objects.equals(ourFile, theirFile)) {
                if (ourFile != null) {
                    mergedIndex.put(filepath, ourFile);
                    copyBlobToDisk(ourFile, filepath);
                }
                continue;
            }

            // case 2 — only in theirs
            if (ourFile == null && theirFile != null) {
                mergedIndex.put(filepath, theirFile);
                copyBlobToDisk(theirFile, filepath);
                System.out.println("added:    " + filepath);
                continue;
            }

            // case 3 — only in ours
            if (theirFile == null && ourFile != null) {
                mergedIndex.put(filepath, ourFile);
                copyBlobToDisk(ourFile, filepath);
                continue;
            }

            // case 4 — only theirs changed
            if (Objects.equals(ourFile, ancestorFile)) {
                mergedIndex.put(filepath, theirFile);
                copyBlobToDisk(theirFile, filepath);
                System.out.println("updated:  " + filepath);
                continue;
            }

            // case 5 — only ours changed
            if (Objects.equals(theirFile, ancestorFile)) {
                mergedIndex.put(filepath, ourFile);
                copyBlobToDisk(ourFile, filepath);
                continue;
            }

            // case 6 — CONFLICT
            hasConflicts = true;
            System.out.println("CONFLICT: " + filepath);
            writeConflictMarkers(filepath, ourFile,
                    theirFile, ourBranch, theirBranch);
        }

        if (hasConflicts) {
            for (Map.Entry<String, String> e : mergedIndex.entrySet()) {
                index.add(e.getKey(), e.getValue());
            }
            System.out.println("\nAutomatic merge failed.");
            System.out.println(
                    "Fix conflicts and then run mygit commit.");
            return;
        }

        index.clear();
        for (Map.Entry<String, String> e : mergedIndex.entrySet()) {
            index.add(e.getKey(), e.getValue());
        }
        createMergeCommit(ourSha, theirSha, ourBranch, theirBranch);
    }

    private String findCommonAncestor(String sha1, String sha2) throws Exception {
        Set<String> ancestors = new HashSet<>();
        String current = sha1;
        while (current != null) {
            ancestors.add(current);
            GitObject obj = objectStore.readObject(current);
            if (obj instanceof GitObject.Commit c) {
                current = c.parentSha();
            } else break;
        }
        current = sha2;
        while (current != null) {
            if (ancestors.contains(current)) return current;
            GitObject obj = objectStore.readObject(current);
            if (obj instanceof GitObject.Commit c) {
                current = c.parentSha();
            } else break;
        }
        return null;
    }

    private Map<String, String> getCommittedEntries(String commitSha) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        GitObject obj = objectStore.readObject(commitSha);
        if (obj instanceof GitObject.Commit commit) {
            collectFlatEntries(commit.treeSha(), "", result);
        }
        return result;
    }

    private void collectFlatEntries(String treeSha, String prefix, Map<String, String> result) throws Exception {
        GitObject treeObj = objectStore.readObject(treeSha);
        if (!(treeObj instanceof GitObject.Tree tree)) return;
        for (GitObject.TreeEntry entry : tree.entries()) {
            String fullPath = prefix.isEmpty()
                    ? entry.name()
                    : prefix + "/" + entry.name();
            if (entry.mode().equals("040000")) {
                collectFlatEntries(entry.sha(), fullPath, result);
            } else {
                result.put(fullPath, entry.sha());
            }
        }
    }

    private void copyBlobToDisk(String sha, String filepath) throws Exception {
        GitObject obj = objectStore.readObject(sha);
        if (!(obj instanceof GitObject.Blob blob)) return;
        Path target = repoRoot.resolve(filepath);
        Files.createDirectories(target.getParent());
        Files.write(target, blob.content());
    }

    private void writeConflictMarkers(String filepath, String ourSha, String theirSha, String ourBranch, String theirBranch) throws Exception {
        String ourContent   = getBlobContent(ourSha);
        String theirContent = getBlobContent(theirSha);
        String conflicted =
                "<<<<<<< " + ourBranch + "\n"
                        + ourContent
                        + (ourContent.endsWith("\n") ? "" : "\n")
                        + "=======\n"
                        + theirContent
                        + (theirContent.endsWith("\n") ? "" : "\n")
                        + ">>>>>>> " + theirBranch + "\n";
        Path target = repoRoot.resolve(filepath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, conflicted);
    }

    private String getBlobContent(String sha) throws Exception {
        GitObject obj = objectStore.readObject(sha);
        if (obj instanceof GitObject.Blob b) {
            return new String(b.content());
        }
        return "";
    }

    private void createMergeCommit(String ourSha, String theirSha, String ourBranch, String theirBranch) throws Exception {
        Map<String, String> entries = index.getEntries();
        String treeSha = objectStore.writeTree(entries);
        long timestamp = System.currentTimeMillis() / 1000;
        String author  = "Divesh <divesh@email.com>";
        String commitContent =
                "tree "     + treeSha  + "\n"
                        + "parent " + ourSha   + "\n"
                        + "parent " + theirSha + "\n"
                        + "author "    + author + " " + timestamp + " +0530\n"
                        + "committer " + author + " " + timestamp + " +0530\n"
                        + "\n"
                        + "Merge branch '" + theirBranch
                        + "' into " + ourBranch + "\n";
        byte[] commitBytes = commitContent
                .getBytes(StandardCharsets.UTF_8);
        String commitSha = objectStore.hashObject(
                commitBytes, "commit", true);
        refStore.updateHead(commitSha);
        System.out.println("Merged '" + theirBranch
                + "' into '" + ourBranch + "'.");
        System.out.println("[" + ourBranch + " "
                + commitSha.substring(0, 7)
                + "] Merge branch '" + theirBranch + "'");
    }
}