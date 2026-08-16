package org.divesh.commands;

import org.divesh.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "reset",
        description = "Reset current HEAD to a previous state"
)
public class ResetCommand implements Runnable {

    @Option(
            names = "--soft",
            description = "Move HEAD only"
    )
    boolean soft;

    @Option(
            names = "--mixed",
            description = "Move HEAD and reset index (default)"
    )
    boolean mixed;

    @Option(
            names = "--hard",
            description = "Move HEAD, reset index and files"
    )
    boolean hard;

    @Parameters(
            paramLabel = "<target>",
            description = "Commit to reset to e.g. HEAD~1 or a SHA",
            defaultValue = "HEAD"
    )
    String target;

    @Override
    public void run() {
        try {
            Repository repo = new Repository(Path.of("."));

            String mode = "--mixed"; // default
            if (soft)  mode = "--soft";
            if (hard)  mode = "--hard";

            repo.reset(mode, target);
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}