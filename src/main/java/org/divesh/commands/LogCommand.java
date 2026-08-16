package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;

import java.nio.file.Path;

@Command(
        name = "log",
        description = "Show commit history"
)
public class LogCommand implements Runnable {

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));
            repo.log();
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}