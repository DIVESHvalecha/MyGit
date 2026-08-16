package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "branch",
        description = "List, create or delete branches"
)
public class BranchCommand implements Runnable {

    @Parameters(
            paramLabel = "<name>",
            description = "Branch name",
            arity = "0..1"    // optional — no name = list branches
    )
    String name;

    @Option(
            names = {"-d", "--delete"},
            description = "Delete a branch"
    )
    boolean delete;

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));
            repo.branch(name, delete);
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}