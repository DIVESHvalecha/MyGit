package org.divesh.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResetService {

    private final Path repoRoot;
    private final ObjectStore objectStore;
    private final RefStore refStore;
    private final Index index;

    public ResetService(Path repoRoot, ObjectStore objectStore,
                        RefStore refStore, Index index) {
        this.repoRoot    = repoRoot;
        this.objectStore = objectStore;
        this.refStore    = refStore;
        this.index       = index;
    }

    public void reset(String mode, String target) throws Exception {

        // 1. resolve target to a SHA
        String targetSha = resolveTarget(target);
        if (targetSha == null) {
            System.out.println("error: could not resolve " + target);
            return;
        }

        // 2. apply the mode
        switch (mode) {
            case "--soft"  -> doSoft(targetSha);
            case "--hard"  -> doHard(targetSha);
            default        -> doMixed(targetSha); // --mixed is default
        }
    }

    private void doSoft(String targetSha) throws Exception {
        refStore.updateHead(targetSha);
        System.out.println("HEAD is now at "
                + targetSha.substring(0, 7));
    }


    private void doMixed(String targetSha) throws Exception {
        refStore.updateHead(targetSha);
        rebuildIndex(targetSha);
        System.out.println("HEAD is now at "
                + targetSha.substring(0, 7));
    }


    private void doHard(String targetSha) throws Exception {
        // get current files before we change anything
        Map<String, String> currentFiles = index.getEntries();

        // get target files
        Map<String, String> targetFiles = getCommittedFiles(targetSha);

        // delete files not in target
        for (String filepath : currentFiles.keySet()) {
            if (!targetFiles.containsKey(filepath)) {
                Files.deleteIfExists(repoRoot.resolve(filepath));

                // delete empty parent directories
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

        // restore target files to disk
        for (Map.Entry<String, String> entry : targetFiles.entrySet()) {
            String filepath = entry.getKey();
            String sha      = entry.getValue();
            GitObject obj   = objectStore.readObject(sha);
            if (obj instanceof GitObject.Blob blob) {
                Path target = repoRoot.resolve(filepath);
                Files.createDirectories(target.getParent());
                Files.write(target, blob.content());
            }
        }

        // update HEAD and index
        refStore.updateHead(targetSha);
        rebuildIndex(targetSha);

        System.out.println("HEAD is now at "
                + targetSha.substring(0, 7));
    }


    private String resolveTarget(String target) throws Exception {
        if (target.equals("HEAD")) {
            return refStore.resolveHead();
        }

        if (target.startsWith("HEAD~")) {
            int steps = Integer.parseInt(target.substring(5));
            String sha = refStore.resolveHead();
            for (int i = 0; i < steps; i++) {
                if (sha == null) {
                    throw new Exception(
                            "fatal: not enough commits");
                }
                GitObject obj = objectStore.readObject(sha);
                if (obj instanceof GitObject.Commit c) {
                    sha = c.parentSha();
                } else break;
            }
            return sha;
        }

        // raw SHA
        return target;
    }

    private void rebuildIndex(String commitSha) throws Exception {
        Map<String, String> files = getCommittedFiles(commitSha);
        index.clear();
        for (Map.Entry<String, String> e : files.entrySet()) {
            index.add(e.getKey(), e.getValue());
        }
    }

    private Map<String, String> getCommittedFiles(
            String commitSha) throws Exception {

        Map<String, String> result = new LinkedHashMap<>();
        GitObject obj = objectStore.readObject(commitSha);
        if (obj instanceof GitObject.Commit commit) {
            collectFiles(commit.treeSha(), "", result);
        }
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