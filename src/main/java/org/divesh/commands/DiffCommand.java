package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "diff",
        description = "Show changes between working directory, index and commits"
)
public class DiffCommand implements Runnable {

    @Option(
            names = "--cached",
            description = "Compare index vs last commit"
    )
    boolean cached;

    @Option(
            names = "--HEAD",
            description = "Compare working directory vs last commit"
    )
    boolean head;

    @Parameters(
            paramLabel = "<branches>",
            description = "Two branches to compare e.g. main..feature",
            arity = "0..1"
    )
    String branches;

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));

            if (branches != null && branches.contains("..")) {
                // branch diff mode
                String[] parts = branches.split("\\.\\.");
                repo.diffBranches(parts[0], parts[1]);
            } else {
                repo.diff(cached, head);
            }
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}