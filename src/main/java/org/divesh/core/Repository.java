package org.divesh.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Repository {
    Path repoRoot;
    ObjectStore objectStore;
    RefStore refStore;
    Index index;
    private final MergeService mergeService;
    private final RestoreService restoreService;
    private final ResetService resetService;
    private final StashService stashService;
    private final GitIgnoreService gitIgnore;

    public Repository(Path repoRoot) {
        this.repoRoot = repoRoot;
        this.objectStore = new ObjectStore(repoRoot);
        this.refStore = new RefStore(repoRoot);
        this.index = new Index(repoRoot);
        this.mergeService = new MergeService(repoRoot, objectStore, refStore, index);
        this.restoreService = new RestoreService(repoRoot, objectStore, refStore, index);
        this.resetService = new ResetService(repoRoot, objectStore, refStore, index);
        this.stashService = new StashService(repoRoot, objectStore, refStore, index);
        this.gitIgnore = new GitIgnoreService(repoRoot);
    }

    public static void init(Path directory) throws IOException {

        Path gitDir = directory.resolve(".git");

        if (Files.exists(gitDir)) {
            System.out.println("Reinitialized existing Git repository in "
                    + gitDir.toAbsolutePath());
            return;
        }

        Files.createDirectories(directory.resolve(".git").resolve("objects"));

        Files.createDirectories(directory.resolve(".git").resolve("refs").resolve("heads"));

        Files.writeString(
                directory.resolve(".git").resolve("HEAD"),
                "ref: refs/heads/main\n");

        System.out.println("Initialized empty git repository in "
                + directory.toAbsolutePath());
    }

    public void add(Path filePath) throws Exception {
        checkRepo();

        // convert to absolute path to ensure relativize works correctly
        Path absolutePath = filePath.toAbsolutePath();
        Path absoluteRoot = repoRoot.toAbsolutePath();

        byte[] content = Files.readAllBytes(absolutePath);
        String sha = objectStore.hashObject(content, "blob", true);

        // now relativize works correctly
        String relativePath = absoluteRoot.relativize(absolutePath).toString();
        index.add(relativePath, sha);
    }

    public void addAll(Path directory) throws Exception {
        Path absoluteDir = directory.toAbsolutePath();
        Path absoluteRoot = repoRoot.toAbsolutePath();

        Files.walk(absoluteDir)
                .filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(absoluteRoot.resolve(".git")))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .filter(p -> !gitIgnore.isIgnored(p))
                .forEach(p -> {
                    try {
                        add(p);
                    } catch (Exception e) {
                        System.out.println("error adding " + p
                                + ": " + e.getMessage());
                    }
                });
    }

    public String commit(String message, String author) throws Exception {

        // 1. get staged entries from index
        Map<String, String> entries = index.getEntries();

        // nothing staged — nothing to commit
        if (entries.isEmpty()) {
            System.out.println("nothing to commit, working tree clean");
            return null;
        }

        // 2. build tree object from index entries
        String treeSha = objectStore.writeTree(entries);

        // 3. get parent SHA — null if this is the first commit
        String parentSha = refStore.resolveHead();

        // 4. build commit content string
        long timestamp = System.currentTimeMillis() / 1000;
        StringBuilder commitContent = new StringBuilder();

        commitContent.append("tree ").append(treeSha).append("\n");

        // only add parent line if this is not the first commit
        if (parentSha != null) {
            commitContent.append("parent ").append(parentSha).append("\n");
        }

        commitContent.append("author ")
                .append(author)
                .append(" ")
                .append(timestamp)
                .append(" +0530\n");

        commitContent.append("committer ")
                .append(author)
                .append(" ")
                .append(timestamp)
                .append(" +0530\n");

        commitContent.append("\n");
        commitContent.append(message).append("\n");

        // 5. hash + store commit object
        byte[] commitBytes = commitContent.toString()
                .getBytes(StandardCharsets.UTF_8);
        String commitSha = objectStore.hashObject(commitBytes, "commit", true);

        // 6. move branch pointer forward
        refStore.updateHead(commitSha);

        // 7. print confirmation — same style as real git
        String shortSha = commitSha.substring(0, 7);
        String branch = refStore.getCurrentBranch();
        System.out.println("[" + branch + " " + shortSha + "] " + message);

        return commitSha;
    }

    public void log() throws Exception {

        String currentSha = refStore.resolveHead();

        if (currentSha == null) {
            System.out.println("fatal: your current branch 'main' " +
                    "does not have any commits yet");
            return;
        }

        while (currentSha != null) {

            GitObject obj = objectStore.readObject(currentSha);

            if (!(obj instanceof GitObject.Commit commit)) {
                System.out.println("error: object " + currentSha + " is not a commit");
                break;
            }

            System.out.println("commit " + currentSha);
            System.out.println("Author: " + commit.author());
            System.out.println("Date:   " + commit.committer());
            System.out.println();
            System.out.println("    " + commit.message());
            System.out.println();

            currentSha = commit.parentSha();
        }
    }

    public void status() throws Exception {

        // 1. print current branch
        System.out.println("On branch " + refStore.getCurrentBranch());
        System.out.println();

        // 2. get staged entries from index
        Map<String, String> indexEntries = index.getEntries();

        // 3. get last commit tree entries
        Map<String, String> committedEntries = new LinkedHashMap<>();
        String commitSha = refStore.resolveHead();

        if (commitSha != null) {
            // read commit → get tree SHA
            GitObject obj = objectStore.readObject(commitSha);
            if (obj instanceof GitObject.Commit commit) {
                // read tree → get entries
                GitObject treeObj = objectStore.readObject(commit.treeSha());
                if (treeObj instanceof GitObject.Tree tree) {
                    for (GitObject.TreeEntry entry : tree.entries()) {
                        committedEntries.put(entry.name(), entry.sha());
                    }
                }
            }
        }

        // 4. compare index vs last commit
        // → find staged changes
        List<String> newFiles = new ArrayList<>();
        List<String> modifiedStaged = new ArrayList<>();

        for (Map.Entry<String, String> entry : indexEntries.entrySet()) {
            String filepath = entry.getKey();
            String indexSha = entry.getValue();

            if (!committedEntries.containsKey(filepath)) {
                // in index but not in last commit → new file staged
                newFiles.add(filepath);
            } else if (!committedEntries.get(filepath).equals(indexSha)) {
                // in both but sha different → modified and staged
                modifiedStaged.add(filepath);
            }
        }

        // 5. print staged changes
        if (!newFiles.isEmpty() || !modifiedStaged.isEmpty()) {
            System.out.println("Changes to be committed:");
            System.out.println("  (use \"git restore --staged <file>\" to unstage)");
            System.out.println();
            for (String f : newFiles)
                System.out.println("\tnew file:   " + f);
            for (String f : modifiedStaged)
                System.out.println("\tmodified:   " + f);
            System.out.println();
        }

        // 6. compare working directory vs index
        // → find unstaged changes + untracked files
        List<String> modifiedUnstaged = new ArrayList<>();
        List<String> untracked = new ArrayList<>();

        Files.walk(repoRoot)
                .filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(repoRoot.resolve(".git")))
                .forEach(p -> {
                    try {
                        String relative = repoRoot.relativize(p).toString();
                        byte[] diskBytes = Files.readAllBytes(p);

                        // hash without writing
                        String diskSha = objectStore.hashObject(diskBytes, "blob", false);

                        if (!indexEntries.containsKey(relative)) {
                            // not in index at all → untracked
                            untracked.add(relative);
                        } else if (!indexEntries.get(relative).equals(diskSha)) {
                            // in index but disk content changed → modified not staged
                            modifiedUnstaged.add(relative);
                        }
                    } catch (Exception e) {
                        System.out.println("error reading " + p + ": " + e.getMessage());
                    }
                });

        // 7. print unstaged changes
        if (!modifiedUnstaged.isEmpty()) {
            System.out.println("Changes not staged for commit:");
            System.out.println("  (use \"git add <file>\" to update what will be committed)");
            System.out.println();
            for (String f : modifiedUnstaged)
                System.out.println("\tmodified:   " + f);
            System.out.println();
        }

        // 8. print untracked files
        if (!untracked.isEmpty()) {
            System.out.println("Untracked files:");
            System.out.println("  (use \"git add <file>\" to include in what will be committed)");
            System.out.println();
            for (String f : untracked)
                System.out.println("\t" + f);
            System.out.println();
        }

        // 9. print clean message if nothing to report
        if (newFiles.isEmpty() && modifiedStaged.isEmpty()
                && modifiedUnstaged.isEmpty() && untracked.isEmpty()) {
            System.out.println("nothing to commit, working tree clean");
        }
    }

    public void branch(String name, boolean delete) throws Exception {
        if (name == null && !delete) {
            listBranches();
            return;
        }

        if (delete) {
            assert name != null;
            deleteBranch(name);
            return;
        }

        createBranch(name);
    }

    private void createBranch(String name) throws IOException {
        Path branchFile = repoRoot.resolve(".git")
                .resolve("refs")
                .resolve("heads")
                .resolve(name);

        if (Files.exists(branchFile)) {
            System.out.println("fatal: a branch named '" + name
                    + "' already exists");
            return;
        }

        // get current commit SHA
        String currentSha = refStore.resolveHead();
        if (currentSha == null) {
            System.out.println("fatal: not a valid object name: 'main'");
            return;
        }

        // create branch pointing to current commit
        refStore.createBranch(name, currentSha);
        System.out.println("created branch '" + name + "'");
    }

    private void listBranches() throws IOException {
        Path headDir = repoRoot.resolve(".git")
                .resolve("refs")
                .resolve("heads");

        String currentBranch = refStore.getCurrentBranch();

        try (var branches = Files.list(headDir)) {
            branches.map(p -> p.getFileName().toString())
                    .sorted()
                    .forEach(branch -> {
                        if (currentBranch != null && branch.equals(currentBranch)) {
                            System.out.println("* " + branch);
                        } else {
                            System.out.println("  " + branch);
                        }
                    });
        }
    }

    private void deleteBranch(String name) throws Exception {
        // cannot delete current branch
        if (name.equals(refStore.getCurrentBranch())) {
            System.out.println("error: cannot delete branch '" + name
                    + "' checked out at '" + repoRoot.toAbsolutePath() + "'");
            return;
        }

        Path branchFile = repoRoot.resolve(".git")
                .resolve("refs")
                .resolve("heads")
                .resolve(name);

        if (!Files.exists(branchFile)) {
            System.out.println("error: branch '" + name + "' not found");
            return;
        }

        Files.delete(branchFile);
        System.out.println("deleted branch '" + name + "'");
    }

    public void checkout(String branchName, boolean createNew) throws Exception {
        checkRepo();

        if (createNew) {
            createBranch(branchName);
        }

        Path branchFile = repoRoot.resolve(".git")
                .resolve("refs")
                .resolve("heads")
                .resolve(branchName);

        if (!Files.exists(branchFile)) {
            System.out.println("error: pathspec '" + branchName
                    + "' did not match any known branch");
            return;
        }

        if (branchName.equals(refStore.getCurrentBranch())) {
            System.out.println("already on '" + branchName + "'");
            return;
        }

        String targetSha = Files.readString(branchFile).trim();

        Map<String, String> currentIndex = index.getEntries();
        for (Map.Entry<String, String> entry : currentIndex.entrySet()) {
            Path filePath = repoRoot.resolve(entry.getKey());
            if (Files.exists(filePath)) {
                byte[] diskBytes = Files.readAllBytes(filePath);
                String diskSha = objectStore.hashObject(diskBytes, "blob", false);
                if (!diskSha.equals(entry.getValue())) {
                    System.out.println("error: your local changes would be " +
                            "overwritten by checkout. commit or stash first.");
                    return;
                }
            }
        }

        restoreTree(targetSha);

        Files.writeString(
                repoRoot.resolve(".git").resolve("HEAD"),
                "ref: refs/heads/" + branchName + "\n");

        System.out.println("switched to branch '" + branchName + "'");
    }

    private void restoreTree(String commitSha) throws Exception {

        // get current index before we clear it
        Map<String, String> currentFiles = index.getEntries();

        // read target commit → get target tree entries
        GitObject commitObj = objectStore.readObject(commitSha);
        if (!(commitObj instanceof GitObject.Commit commit))
            return;

        // build set of files in target branch
        Map<String, String> targetFiles = new LinkedHashMap<>();
        collectTreeEntries(commit.treeSha(), repoRoot, targetFiles);

        // delete files that exist in current branch but NOT in target branch
        for (String filepath : currentFiles.keySet()) {
            if (!targetFiles.containsKey(filepath)) {
                Path fileToDelete = repoRoot.resolve(filepath);
                Files.deleteIfExists(fileToDelete);
            }
        }

        // delete empty directories left behind
        for (String filepath : currentFiles.keySet()) {
            if (!targetFiles.containsKey(filepath)) {
                Path parent = repoRoot.resolve(filepath).getParent();
                // delete directory if empty and not repo root
                if (!parent.equals(repoRoot) && Files.isDirectory(parent)) {
                    try (var entries = Files.list(parent)) {
                        if (entries.findAny().isEmpty()) {
                            Files.deleteIfExists(parent);
                        }
                    }
                }
            }
        }

        // clear current index
        index.clear();

        // restore target branch files to disk
        restoreTreeEntries(commit.treeSha(), repoRoot);
    }

    private void collectTreeEntries(String treeSha, Path targetDir,
            Map<String, String> result) throws Exception {

        GitObject treeObj = objectStore.readObject(treeSha);
        if (!(treeObj instanceof GitObject.Tree tree))
            return;

        for (GitObject.TreeEntry entry : tree.entries()) {
            Path entryPath = targetDir.resolve(entry.name());

            if (entry.mode().equals("040000")) {
                // recurse into subdirectory
                collectTreeEntries(entry.sha(), entryPath, result);
            } else {
                // file — add to map
                String relativePath = repoRoot.relativize(entryPath).toString();
                result.put(relativePath, entry.sha());
            }
        }
    }

    private void restoreTreeEntries(String treeSha, Path targetDir) throws IOException {
        GitObject treeObj = objectStore.readObject(treeSha);
        if (!(treeObj instanceof GitObject.Tree tree))
            return;

        for (GitObject.TreeEntry entry : tree.entries()) {

            Path entryPath = targetDir.resolve(entry.name());

            if (entry.mode().equals("040000")) {
                // directory — recurse
                Files.createDirectories(entryPath);
                restoreTreeEntries(entry.sha(), entryPath);

            } else {
                // file — read blob and write to disk
                GitObject blobObj = objectStore.readObject(entry.sha());
                if (!(blobObj instanceof GitObject.Blob blob))
                    continue;

                // create parent directories if needed
                Files.createDirectories(entryPath.getParent());

                // write file content to disk
                Files.write(entryPath, blob.content());

                // update index with restored file
                String relativePath = repoRoot.relativize(entryPath).toString();
                index.add(relativePath, entry.sha());
            }
        }
    }

    public void diff(boolean cached, boolean head) throws Exception {
        checkRepo();

        DiffEngine engine = new DiffEngine();
        Map<String, String> indexEntries = index.getEntries();

        if (cached) {
            // index vs last commit
            diffIndexVsCommit(engine, indexEntries);
        } else if (head) {
            // working directory vs last commit
            diffWorkingVsCommit(engine, indexEntries);
        } else {
            // working directory vs index (default)
            diffWorkingVsIndex(engine, indexEntries);
        }
    }

    private void diffWorkingVsIndex(DiffEngine engine,
            Map<String, String> indexEntries) throws Exception {

        for (Map.Entry<String, String> entry : indexEntries.entrySet()) {
            String filepath = entry.getKey();
            String indexSha = entry.getValue();

            Path filePath = repoRoot.resolve(filepath);
            if (!Files.exists(filePath))
                continue;

            // read current file from disk
            byte[] diskBytes = Files.readAllBytes(filePath);
            String diskSha = objectStore.hashObject(diskBytes, "blob", false);

            // only diff if changed
            if (!diskSha.equals(indexSha)) {
                // get old content from index blob
                GitObject oldObj = objectStore.readObject(indexSha);
                String oldContent = "";
                if (oldObj instanceof GitObject.Blob b) {
                    oldContent = new String(b.content());
                }

                String newContent = new String(diskBytes);
                List<DiffLine> lines = engine.diff(oldContent, newContent);
                System.out.print(engine.format(lines, filepath, filepath));
            }
        }
    }

    private void diffIndexVsCommit(DiffEngine engine,
            Map<String, String> indexEntries) throws Exception {

        String commitSha = refStore.resolveHead();
        if (commitSha == null) {
            System.out.println("no commits yet");
            return;
        }

        // get committed files
        Map<String, String> committedEntries = getCommittedEntries(commitSha);

        // find all files to compare
        for (Map.Entry<String, String> entry : indexEntries.entrySet()) {
            String filepath = entry.getKey();
            String indexSha = entry.getValue();
            String commitShaForFile = committedEntries.get(filepath);

            // skip if same
            if (indexSha.equals(commitShaForFile))
                continue;

            String oldContent = "";
            if (commitShaForFile != null) {
                GitObject oldObj = objectStore.readObject(commitShaForFile);
                if (oldObj instanceof GitObject.Blob b) {
                    oldContent = new String(b.content());
                }
            }

            GitObject newObj = objectStore.readObject(indexSha);
            String newContent = "";
            if (newObj instanceof GitObject.Blob b) {
                newContent = new String(b.content());
            }

            List<DiffLine> lines = engine.diff(oldContent, newContent);
            System.out.print(engine.format(lines, filepath, filepath));
        }
    }

    private void diffWorkingVsCommit(DiffEngine engine,
            Map<String, String> indexEntries) throws Exception {

        String commitSha = refStore.resolveHead();
        if (commitSha == null) {
            System.out.println("no commits yet");
            return;
        }

        Map<String, String> committedEntries = getCommittedEntries(commitSha);

        for (Map.Entry<String, String> entry : committedEntries.entrySet()) {
            String filepath = entry.getKey();
            String commitedSha = entry.getValue();

            Path filePath = repoRoot.resolve(filepath);
            if (!Files.exists(filePath))
                continue;

            byte[] diskBytes = Files.readAllBytes(filePath);
            String diskSha = objectStore.hashObject(diskBytes, "blob", false);

            if (!diskSha.equals(commitedSha)) {
                GitObject oldObj = objectStore.readObject(commitedSha);
                String oldContent = "";
                if (oldObj instanceof GitObject.Blob b) {
                    oldContent = new String(b.content());
                }

                String newContent = new String(diskBytes);
                List<DiffLine> lines = engine.diff(oldContent, newContent);
                System.out.print(engine.format(lines, filepath, filepath));
            }
        }
    }

    private Map<String, String> getCommittedEntries(String commitSha)
            throws Exception {

        Map<String, String> result = new LinkedHashMap<>();
        GitObject obj = objectStore.readObject(commitSha);

        if (obj instanceof GitObject.Commit commit) {
            collectFlatEntries(commit.treeSha(), "", result);
        }
        return result;
    }

    private void collectFlatEntries(String treeSha, String prefix,
            Map<String, String> result) throws Exception {

        GitObject treeObj = objectStore.readObject(treeSha);
        if (!(treeObj instanceof GitObject.Tree tree))
            return;

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

    public void diffBranches(String branch1, String branch2) throws Exception {
        // get commit SHA for each branch
        String sha1 = Files.readString(
                repoRoot.resolve(".git/refs/heads/" + branch1)).trim();
        String sha2 = Files.readString(
                repoRoot.resolve(".git/refs/heads/" + branch2)).trim();

        // get all files from each branch
        Map<String, String> files1 = getCommittedEntries(sha1);
        Map<String, String> files2 = getCommittedEntries(sha2);

        DiffEngine engine = new DiffEngine();

        // files changed or added in branch2
        for (Map.Entry<String, String> entry : files2.entrySet()) {
            String filepath = entry.getKey();
            String sha2File = entry.getValue();
            String sha1File = files1.get(filepath);

            if (!entry.getValue().equals(sha1File)) {
                String oldContent = "";
                if (sha1File != null) {
                    GitObject obj = objectStore.readObject(sha1File);
                    if (obj instanceof GitObject.Blob b)
                        oldContent = new String(b.content());
                }

                GitObject obj = objectStore.readObject(sha2File);
                String newContent = "";
                if (obj instanceof GitObject.Blob b)
                    newContent = new String(b.content());

                List<DiffLine> lines = engine.diff(oldContent, newContent);
                System.out.print(engine.format(lines,
                        branch1 + "/" + filepath,
                        branch2 + "/" + filepath));
            }
        }
    }

    public void merge(String branch) throws Exception {
        checkRepo();
        mergeService.merge(branch);
    }

    public void restoreStaged(String filepath) throws Exception {
        checkRepo();
        restoreService.restoreStaged(filepath);
    }

    public void reset(String mode, String target) throws Exception {
        checkRepo();
        resetService.reset(mode, target);
    }

    public void stash() throws Exception {
        checkRepo();
        stashService.stash();
    }

    public void stashPop() throws Exception {
        checkRepo();
        stashService.pop();
    }

    public void stashList() throws Exception {
        checkRepo();
        stashService.list();
    }

    private void checkRepo() throws IOException {
        if (!Files.exists(repoRoot.resolve(".git"))) {
            throw new IllegalStateException(
                    "fatal: not a git repository (or any parent directories): .git");
        }
    }
}
