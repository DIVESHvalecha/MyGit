package org.divesh.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RefStore {
    Path gitDir;

    public RefStore(Path repoRoot) {
        this.gitDir = repoRoot.resolve(".git");
    }

    public String readHead() throws IOException {
        return Files.readString(gitDir.resolve("HEAD")).trim();
    }

    public String resolveHead() throws IOException {
        String head = readHead();

        if (head.startsWith("ref: ")) {
            // attached HEAD — follow the ref to get the SHA
            String refPath = head.substring(5);  // strip "ref: "
            Path refFile = gitDir.resolve(refPath);

            // branch file might not exist yet (no commits made)
            if (!Files.exists(refFile)) return null;

            return Files.readString(refFile).trim();
        } else {
            // detached HEAD — already a SHA
            return head;
        }
    }

    public void updateHead(String commitSha) throws IOException {
        String head = readHead();

        if (head.startsWith("ref: ")) {
            // attached — update the branch file, not HEAD itself
            String refPath = head.substring(5);  // "refs/heads/main"
            Path refFile = gitDir.resolve(refPath);

            // make sure parent dirs exist (.git/refs/heads/)
            Files.createDirectories(refFile.getParent());

            Files.writeString(refFile, commitSha + "\n");
        } else {
            // detached HEAD — update HEAD directly
            Files.writeString(gitDir.resolve("HEAD"), commitSha + "\n");
        }
    }

    public void createBranch(String name, String sha) throws IOException {
        Path branchFile = gitDir.resolve("refs").resolve("heads").resolve(name);

        // create .git/refs/heads/ if it doesn't exist
        Files.createDirectories(branchFile.getParent());

        Files.writeString(branchFile, sha + "\n");
    }

    public String getCurrentBranch() throws IOException {
        String head = readHead();

        if (head.startsWith("ref: refs/heads/")) {
            return head.substring(16);  // strip "ref: refs/heads/"
        } else {
            return "HEAD detached at " + head.substring(0, 7);
        }
    }

}
