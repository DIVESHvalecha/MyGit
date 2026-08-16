package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "checkout",
        description = "Switch branches or restore files"
)
public class CheckoutCommand implements Runnable {

    @Option(
            names = {"-b"},
            description = "Create and switch to new branch"
    )
    boolean createNew;

    @Parameters(
            paramLabel = "<branch>",
            description = "Branch to switch to"
    )
    String branch;

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));
            repo.checkout(branch, createNew);
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}