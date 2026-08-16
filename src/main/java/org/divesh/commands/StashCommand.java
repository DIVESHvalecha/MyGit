package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "stash",
        description = "Stash changes in working directory"
)
public class StashCommand implements Runnable {

    @Parameters(
            paramLabel = "<subcommand>",
            description = "pop or list (default: save)",
            arity = "0..1",
            defaultValue = ""
    )
    String subcommand;

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));
            switch (subcommand) {
                case "pop"  -> repo.stashPop();
                case "list" -> repo.stashList();
                default     -> repo.stash();
            }
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}