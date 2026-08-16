package org.divesh.core;

// import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class RestoreService {

    private final Path repoRoot;
    private final ObjectStore objectStore;
    private final RefStore refStore;
    private final Index index;

    public RestoreService(Path repoRoot, ObjectStore objectStore,
            RefStore refStore, Index index) {
        this.repoRoot = repoRoot;
        this.objectStore = objectStore;
        this.refStore = refStore;
        this.index = index;
    }

    public void restoreStaged(String filepath) throws Exception {
        String commitSha = refStore.resolveHead();

        // no commits yet — just remove from index
        if (commitSha == null) {
            index.remove(filepath);
            System.out.println("unstaged: " + filepath);
            return;
        }

        Map<String, String> committed = getCommittedFiles(commitSha);

        if (committed.containsKey(filepath)) {
            // restore index to committed SHA
            index.add(filepath, committed.get(filepath));
        } else {
            // never committed — remove from index entirely
            index.remove(filepath);
        }

        System.out.println("unstaged: " + filepath);
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
        if (!(treeObj instanceof GitObject.Tree tree))
            return;

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