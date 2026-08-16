package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "init",
        description = "Initialize a new git repository"
)
public class InitCommand implements Runnable {

    @Parameters(
            paramLabel = "<directory>",
            description = "Directory to initialize (default: current dir)",
            defaultValue = "."
    )
    String directory;

    @Override
    public void run() {
        try {
            Repository.init(Path.of(directory));
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}