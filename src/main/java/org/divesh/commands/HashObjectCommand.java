package org.divesh.commands;

import org.divesh.core.ObjectStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;

@Command(
        name = "hash-object",
        description = "Compute SHA-1 hash of a file"
)
public class HashObjectCommand implements Runnable {

    @Option(
            names = "-w",
            description = "Write object to store"
    )
    boolean write;

    @Parameters(
            paramLabel = "<file>",
            description = "File to hash"
    )
    String file;

    @Override
    public void run() {
        try {
            byte[] content = Files.readAllBytes(Path.of(file));
            ObjectStore store = new ObjectStore(Path.of("."));
            String sha = store.hashObject(content, "blob", write);
            System.out.println(sha);
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}