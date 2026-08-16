package org.divesh.core;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

public class GitIgnoreService {

    private final Path repoRoot;
    private final List<PathMatcher> matchers = new ArrayList<>();
    private final List<String> patterns      = new ArrayList<>();

    public GitIgnoreService(Path repoRoot) {
        this.repoRoot = repoRoot;
        loadPatterns();
    }

    private void loadPatterns() {
        Path gitignore = repoRoot.resolve(".gitignore");
        if (!Files.exists(gitignore)) return;

        try {
            List<String> lines = Files.readAllLines(gitignore);
            for (String line : lines) {
                // skip blank lines and comments
                if (line.trim().isEmpty()
                        || line.trim().startsWith("#")) continue;

                patterns.add(line.trim());

                // build glob matcher for filename matching
                try {
                    PathMatcher matcher = FileSystems.getDefault()
                            .getPathMatcher("glob:" + line.trim());
                    matchers.add(matcher);
                } catch (Exception e) {
                    // invalid pattern — skip
                }
            }
        } catch (IOException e) {
            // no .gitignore or unreadable — ignore
        }
    }

    public boolean isIgnored(Path filePath) {
        // get relative path from repo root
        Path relative = repoRoot.toAbsolutePath()
                .relativize(filePath.toAbsolutePath());

        String pathStr   = relative.toString();
        String fileName  = filePath.getFileName().toString();

        for (String pattern : patterns) {
            // directory pattern e.g. target/
            if (pattern.endsWith("/")) {
                String dirName = pattern.substring(
                        0, pattern.length() - 1);
                if (pathStr.contains(dirName + "/")
                        || pathStr.startsWith(dirName)) {
                    return true;
                }
                continue;
            }

            // exact filename match
            if (!pattern.contains("*")
                    && !pattern.contains("?")
                    && !pattern.contains("/")) {
                if (fileName.equals(pattern)) return true;
                continue;
            }

            // glob pattern e.g. *.class
            try {
                PathMatcher matcher = FileSystems.getDefault()
                        .getPathMatcher("glob:" + pattern);
                // check filename only
                if (matcher.matches(Path.of(fileName))) return true;
                // check full relative path
                if (matcher.matches(relative)) return true;
            } catch (Exception e) {
                // invalid pattern — skip
            }
        }
        return false;
    }
}