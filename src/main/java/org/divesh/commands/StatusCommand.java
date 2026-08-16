package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;

import java.nio.file.Path;

@Command(
        name = "status",
        description = "Show working tree status"
)
public class StatusCommand implements Runnable {

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));
            repo.status();
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}