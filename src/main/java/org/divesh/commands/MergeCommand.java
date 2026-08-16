package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "merge",
        description = "Join two branches together"
)
public class MergeCommand implements Runnable {

    @Parameters(
            paramLabel = "<branch>",
            description = "Branch to merge into current branch"
    )
    String branch;

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));
            repo.merge(branch);
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}