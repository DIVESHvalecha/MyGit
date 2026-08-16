package org.divesh.commands;

import org.divesh.agent.CommandExecutor;
import org.divesh.agent.GitAgent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "ai",
        description = "Natural language Git assistant"
)
public class AgentCommand implements Runnable {

    @Option(
            names = {"--key", "-k"},
            description = "OpenAI API key " +
                    "(or set OPENAI_API_KEY env variable)"
    )
    String apiKey;

    @Option(
            names = {"--dir", "-d"},
            description = "Working directory (default: .)",
            defaultValue = "."
    )
    String workingDir;

    @Override
    public void run() {
        // get API key from flag or environment
        String key = apiKey != null
                ? apiKey
                : System.getenv("OPENAI_API_KEY");

        if (key == null || key.isEmpty()) {
            System.out.println(
                    "error: no API key found.\n" +
                            "set OPENAI_API_KEY environment variable\n" +
                            "or use --key flag");
            return;
        }

        String jarPath = CommandExecutor.detectJarPath();

        try {
            GitAgent agent = new GitAgent(key, jarPath, workingDir);
            agent.start();
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}