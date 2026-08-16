package org.divesh.agent;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandExecutor {

    private final String jarPath;
    private final String workingDir;

    public CommandExecutor(String jarPath, String workingDir) {
        this.jarPath    = jarPath;
        this.workingDir = workingDir;
    }

    public String execute(String command) throws Exception {
        // command = "mygit reset --soft HEAD~1"
        // split into parts
        String[] parts = command.trim().split("\\s+");

        if (parts.length == 0 || !parts[0].equals("mygit")) {
            return "error: invalid command format";
        }

        // build: java -jar MyGit.jar <subcommand> <args...>
        List<String> cmdList = new ArrayList<>();
        cmdList.add("java");
        cmdList.add("-jar");
        cmdList.add(jarPath);

        // add everything after "mygit"
        cmdList.addAll(Arrays.asList(parts).subList(1, parts.length));

        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);  // merge stdout + stderr

        Process process = pb.start();

        // read output
        String output = new String(
                process.getInputStream().readAllBytes());

        // wait for process to finish
        int exitCode = process.waitFor();

        if (output.trim().isEmpty()) {
            return exitCode == 0
                    ? "(command completed successfully)"
                    : "(command failed with exit code " + exitCode + ")";
        }

        return output.trim();
    }

    // auto detect jar path from current running jar
    public static String detectJarPath() {
        try {
            String path = CommandExecutor.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            return path;
        } catch (Exception e) {
            // fallback — look in common locations
            String[] candidates = {
                    "target/MyGit-1.0-SNAPSHOT.jar",
                    "../target/MyGit-1.0-SNAPSHOT.jar"
            };
            for (String candidate : candidates) {
                if (new File(candidate).exists()) return candidate;
            }
            return "target/MyGit-1.0-SNAPSHOT.jar";
        }
    }
}