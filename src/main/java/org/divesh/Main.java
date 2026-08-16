package org.divesh;

import org.divesh.commands.*;
import org.divesh.web.WebServer;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;

@Command(
        name = "mygit",
        subcommands = {
                InitCommand.class,
                AddCommand.class,
                CommitCommand.class,
                LogCommand.class,
                StatusCommand.class,
                HashObjectCommand.class,
                CatFileCommand.class,
                BranchCommand.class,
                CheckoutCommand.class,
                DiffCommand.class,
                MergeCommand.class,
                RestoreCommand.class,
                ResetCommand.class,
                StashCommand.class,
                AgentCommand.class
        },
        mixinStandardHelpOptions = true,
        version = "mygit 1.0",
        description = "A simplified Git implementation in Java"
)
public class Main implements Runnable {

    public static void main(String[] args) throws IOException, InterruptedException {
        for (String arg : args) {
            if (arg.equals("--web")) {
                String workingDir = System.getProperty("user.dir");
                WebServer server  = new WebServer(workingDir);
                server.start(8080);
                // keep alive
                Thread.currentThread().join();
                return;
            }
        }

        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // no subcommand given — print help
        new CommandLine(this).usage(System.out);
    }
}

// export OPENAI_API_KEY