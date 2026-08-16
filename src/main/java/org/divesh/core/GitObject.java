package org.divesh.core;

import java.util.List;

public sealed interface GitObject permits GitObject.Blob, GitObject.Tree, GitObject.Commit, GitObject.Tag {

    String type();

    // ─── Blob ────────────────────────────────────────────────────────────────
    record Blob(byte[] content) implements GitObject {
        @Override
        public String type() { return "blob"; }
    }

    // ─── Tree ────────────────────────────────────────────────────────────────
    record Tree(List<TreeEntry> entries) implements GitObject {
        @Override
        public String type() { return "tree"; }
    }

    // ─── Tree entry (one row inside a tree object) ────────────────────────────
    record TreeEntry(String mode, String name, String sha) {}

    // ─── Commit ──────────────────────────────────────────────────────────────
    record Commit(
            String treeSha,
            String parentSha,   // null for first commit
            String author,
            String committer,
            String message
    ) implements GitObject {
        @Override
        public String type() { return "commit"; }
    }

    // ─── Tag ─────────────────────────────────────────────────────────────────
    record Tag(
            String objectSha,
            String name,
            String tagger,
            String message
    ) implements GitObject {
        @Override
        public String type() { return "tag"; }
    }
}