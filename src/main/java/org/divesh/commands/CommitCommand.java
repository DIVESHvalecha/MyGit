package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(
        name = "commit",
        description = "Record changes to the repository"
)
public class CommitCommand implements Runnable {

    @Option(
            names = {"-m", "--message"},
            description = "Commit message",
            required = true
    )
    String message;

    @Option(
            names = {"-a", "--author"},
            description = "Author name",
            defaultValue = "Unknown <unknown@email.com>"
    )
    String author;

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));
            repo.commit(message, author);
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}