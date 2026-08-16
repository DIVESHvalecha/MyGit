package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;

@Command(
        name = "add",
        description = "Add file contents to the index"
)
public class AddCommand implements Runnable {

    @Parameters(
            paramLabel = "<files>",
            description = "Files to add. Use '.' to add all files",
            arity = "1..*"   // at least one file required
    )
    List<String> files;

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));

            for (String file : files) {
                if (file.equals(".")) {
                    // add all files
                    repo.addAll(Path.of("."));
                } else {
                    repo.add(Path.of(file));
                }
            }
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}