package org.divesh.agent;

import java.util.Scanner;

public class GitAgent {

    private final OpenAIClient openAI;
    private final CommandExecutor executor;

    public GitAgent(String apiKey, String jarPath,
                    String workingDir) {
        this.openAI   = new OpenAIClient(apiKey);
        this.executor = new CommandExecutor(jarPath, workingDir);
    }

    public void start() {
        printBanner();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\nmygit ai> ");
            String input = scanner.nextLine().trim();

            // exit
            if (input.equalsIgnoreCase("exit")
                    || input.equalsIgnoreCase("quit")) {
                System.out.println("\nbye!");
                break;
            }

            // skip empty input
            if (input.isEmpty()) continue;

            // translate
            AgentResponse response = translate(input);
            if (response == null) continue;

            // unknown command
            if (response.command().equals("UNKNOWN")) {
                System.out.println("\n  could not translate: "
                        + response.explanation());
                printDivider();
                continue;
            }

            // show command + explanation
            printResponse(response);

            // warn if dangerous
            if (response.dangerous()) {
                System.out.println(
                        "\n  ⚠  WARNING: this is a destructive"
                                + " operation — changes may be lost");
            }

            // ask for confirmation
            System.out.print("\n  execute? (y/n): ");
            String confirm = scanner.nextLine().trim();

            if (confirm.equalsIgnoreCase("y")
                    || confirm.equalsIgnoreCase("yes")) {
                // execute
                String output = run(response.command());
                System.out.println("\n" + output);
            } else {
                System.out.println("\n  cancelled.");
            }

            printDivider();
        }

        scanner.close();
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private AgentResponse translate(String input) {
        try {
            System.out.println("\n  thinking...");
            return openAI.translate(input);
        } catch (Exception e) {
            System.out.println("\n  error calling API: "
                    + e.getMessage());
            return null;
        }
    }

    private String run(String command) {
        try {
            return executor.execute(command);
        } catch (Exception e) {
            return "error executing command: " + e.getMessage();
        }
    }

    private void printResponse(AgentResponse response) {
        System.out.println();
        System.out.println("  command:      "
                + response.command());
        System.out.println("  explanation:  "
                + response.explanation());
    }

    private void printBanner() {
        System.out.println();
        System.out.println(
                "╔══════════════════════════════════════════════════╗");
        System.out.println(
                "║       mygit AI — natural language git            ║");
        System.out.println(
                "║   type in plain English  |  'exit' to quit       ║");
        System.out.println(
                "╚══════════════════════════════════════════════════╝");
    }

    private void printDivider() {
        System.out.println(
                "  ──────────────────────────────────────────────────");
    }
}