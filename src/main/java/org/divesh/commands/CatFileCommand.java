package org.divesh.commands;

import org.divesh.core.GitObject;
import org.divesh.core.ObjectStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "cat-file",
        description = "Read a git object"
)
public class CatFileCommand implements Runnable {

    @Option(
            names = "-p",
            description = "Pretty print object contents"
    )
    boolean prettyPrint;

    @Option(
            names = "-t",
            description = "Show object type"
    )
    boolean showType;

    @Parameters(
            paramLabel = "<sha>",
            description = "SHA of the object to read"
    )
    String sha;

    @Override
    public void run() {
        try {
            ObjectStore store = new ObjectStore(Path.of("."));
            GitObject obj = store.readObject(sha);

            if (showType) {
                System.out.println(obj.type());
                return;
            }

            if (prettyPrint) {
                switch (obj) {
                    case GitObject.Blob b ->
                            System.out.print(new String(b.content()));

                    case GitObject.Commit c -> {
                        System.out.println("tree "      + c.treeSha());
                        if (c.parentSha() != null)
                            System.out.println("parent "    + c.parentSha());
                        System.out.println("author "    + c.author());
                        System.out.println("committer " + c.committer());
                        System.out.println();
                        System.out.println(c.message());
                    }

                    case GitObject.Tree t -> {
                        for (GitObject.TreeEntry e : t.entries()) {
                            System.out.println(e.mode() + " blob " +
                                    e.sha()  + "\t" + e.name());
                        }
                    }

                    case GitObject.Tag tag ->
                            System.out.println("tag " + tag.name() +
                                    " → "  + tag.objectSha());
                }
            }
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}