package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;

@Command(
        name = "restore",
        description = "Restore working tree files"
)
public class RestoreCommand implements Runnable {

    @Option(
            names = "--staged",
            description = "Unstage a file"
    )
    boolean staged;

    @Parameters(
            paramLabel = "<files>",
            description = "Files to restore",
            arity = "1..*"
    )
    List<String> files;

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));
            for (String file : files) {
                if (staged) {
                    repo.restoreStaged(file);
                } else {
                    System.out.println(
                            "hint: use --staged to unstage");
                }
            }
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}