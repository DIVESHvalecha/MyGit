---
id: my-git
title: mygit — Git Implementation from Scratch
source: personal
role: author
repo: https://github.com/DIVESHvalecha/MyGit
demo: ""
timeframe: 2026-03 → 2026-03
status: maintained
domain: [developer-tools, version-control, developer-education]
shapes: [0-to-1, dx]
stack: [Java 21, Picocli, Maven, JDK HttpServer, OpenAI API]
adoption: "[MISSING] — no verified users, downloads, stars, or awards; the 24-hour build time is author-stated, not adoption."
evidence_strength: medium
impact:
  - "Implemented a working local Git subset: init, add, commit, log, status, branch, checkout, diff, merge, reset, restore, stash, and object inspection."
  - "Verified byte-for-byte SHA-1 compatibility with real Git for blob hashing (ce013625030ba8dba906f756967f9e9ca394464a for 'hello\\n'), and real Git can read mygit commit and tree objects."
  - "Built in a 24-hour hackathon (author-stated; the project guide cites Hacksplosion 2026)."
keywords: [git internals, version control, content-addressable storage, SHA-1, zlib, three-way merge, LCS, Java 21, Picocli, CLI, OpenAI API, developer tooling]
resume_bullets:
  - "Built a runnable Git subset from scratch — content-addressable object store, commits, branching, three-way merge, stash, and .gitignore — with blob SHA-1 IDs verified against real Git."
  - "Added an OpenAI-backed natural-language assistant that maps plain-English requests to mygit commands and requires confirmation before destructive operations."
strength: 3
last_used: 2026-03
confidential: false
resume_eligible: true
links: []
---

## Context

`mygit` is a from-scratch reimplementation of a useful subset of Git in Java 21, built for a 24-hour hackathon and kept as a learning/portfolio project. It targets developers who want to see how Git's object model, staging area, refs, and merge logic work without reading the real Git codebase.

## Problem

Git's internals are usually learned through documentation and guesswork: how objects are hashed, why the index exists, what a tree actually stores, and how a merge finds a common ancestor. The project turns those concepts into a runnable CLI so the mechanics can be inspected directly with `hash-object`, `cat-file`, and the files under `.git/`.

## My ownership

I designed and wrote the CLI, object store, index, refs, repository facade, diff engine, merge/reset/restore/stash services, web dashboard, and OpenAI command assistant. I used AI assistance during development; the repository has a single commit by `DIVESHvalecha`, so commit history cannot independently prove line-level authorship.

I did not own or adapt a template, tutorial repository, or upstream fork. There is no upstream remote and the repository starts from a single root commit. I did not build a production service, a hosted demo, or a test suite.

## Architecture

- **CLI surface:** `Main` is a Picocli command with 15 subcommands (`init`, `add`, `commit`, `log`, `status`, `hash-object`, `cat-file`, `branch`, `checkout`, `diff`, `merge`, `restore`, `reset`, `stash`, `ai`). Each command constructs a `Repository` rooted at the current directory.
- **Object store:** `ObjectStore` builds the Git header (`"<type> <length>\0"`), hashes header + content with SHA-1, and optionally zlib-deflates the result into `.git/objects/<first-2>/<remaining-38>`. `readObject` reverses the process and parses blob, tree, commit, or tag content into a sealed `GitObject` hierarchy.
- **Index:** a deliberately simple text file at `.git/index`, with one line per entry (`"<sha>  <path>"`). Trees are built recursively from the flat index, using modes `100644` for files and `040000` for directories.
- **Refs:** `RefStore` reads and writes `.git/HEAD` and `.git/refs/heads/*`. Branches are plain files; there are no packed refs, reflogs, or remote-tracking refs.
- **Commit path:** `Repository.commit` writes the tree, builds a commit object with tree/parent/author/committer lines, stores it, and advances the current branch ref.
- **Diff:** `DiffEngine` builds an LCS dynamic-programming table over lines and backtracks into ADD / REMOVE / EQUAL records. It is used for `diff`, `diff --cached`, `diff --HEAD`, and branch diffs.
- **Merge:** `MergeService` detects fast-forwards by walking the target branch's parent chain, otherwise finds the common ancestor and compares files by blob SHA using six whole-file cases. Conflicts write whole-file marker lines made of seven `<`, `=`, and `>` characters; a clean merge creates a two-parent commit.
- **Stash:** `StashService` writes a stash commit to `.git/refs/stash`, hard-resets the working tree and index to HEAD, and restores on `stash pop`. Only one stash entry is supported.
- **AI assistant:** `OpenAIClient` sends a fixed system prompt and the user's text to OpenAI's chat-completions endpoint (`gpt-4o-mini`), parses a `COMMAND:` / `EXPLAIN:` response, and `CommandExecutor` launches the built jar as a subprocess. Destructive commands require a `y/n` confirmation.
- **Web dashboard:** `WebServer` uses the JDK's `com.sun.net.httpserver` to serve an HTML command console on port 8080 and a `POST /run` endpoint that executes submitted `mygit` commands.

## Approach & tradeoffs

- **Custom text index over Git's binary index.** Chosen for readability and fast iteration. The cost is real: `git fsck` on a mygit repository reports `fatal: index file corrupt`, so object compatibility does not extend to index compatibility.
- **Whole-file three-way merge over line-level merge.** Chosen to fit the hackathon scope. The cost is coarse conflicts: two branches editing different lines of the same file still conflict, and conflict markers contain entire file versions. LCS is used for diff display, not for merge.
- **Shelling out from the AI agent over calling internals.** This keeps the agent decoupled from repository classes and exercises the real CLI. The cost is a new JVM per action, a dependency on an already-built jar, and no streaming or cancellation.
- **JDK `HttpServer` over Spring Boot.** This avoids the shade-plugin conflict noted in the project guide and keeps the artifact small. The cost is no authentication, no framework-level safety, and a hand-written JSON parser.
- **Single stash ref over a stash stack.** This keeps stash logic small and easy to reason about. The cost is no `stash@{n}` history and no partial stash.

## Results

- Implemented a working local Git subset: init, add, commit, log, status, branch, checkout, diff, merge, reset, restore, stash, and object inspection.
- Verified byte-for-byte SHA-1 compatibility with real Git for blob hashing (`ce013625030ba8dba906f756967f9e9ca394464a` for `hello\n`), and real Git can read mygit commit and tree objects.
- Built in a 24-hour hackathon (author-stated; the project guide cites Hacksplosion 2026).

No user, download, star, award, or performance numbers are verified.

## Repo health

- **Tests:** none. `src/test/java` exists but is empty; JUnit 5 and AssertJ are declared in `pom.xml` but no test sources are tracked.
- **CI:** none. No `.github/workflows`, no build badge, no release workflow.
- **Docs:** no `README.md`. The main documentation is a 19-page PDF revision guide plus a Word document; both are useful but not browsable on GitHub. The guide is partly stale: it says the checkout safety check is not implemented, while `Repository.checkout` does check indexed files against disk.
- **Build/release:** Maven project targeting Java 21, with the shade plugin producing `target/MyGit-1.0-SNAPSHOT.jar`. `mvn` was not installed in the review environment, so the build command is unverified; the existing jar runs. No tags or GitHub releases.
- **Hygiene:** no root `.gitignore`; `target/` build output, `.idea/` files, `.DS_Store`, a resume PDF, and a `dependency-reduced-pom.xml` are tracked. A 19-page PDF and a Word document sit at the repository root.
- **License:** none.
- **Security:** no hardcoded API key was found; the OpenAI key comes from `OPENAI_API_KEY` or `--key`. The web dashboard has no authentication, binds port 8080, allows `Access-Control-Allow-Origin: *`, and executes submitted commands; it should not be exposed beyond localhost. The OpenAI client has no explicit timeout or retry policy.
- **History:** one commit (`b5aa260`, 2026-08-16) by DIVESHvalecha; the file timestamps and project guide point to March 2026 as the build period, so the commit date is an upload date rather than a development timeline.
- **Error handling:** commands generally catch exceptions and print `error: ...`, often leaving the process exit code at 0; this is acceptable for a demo but not for scripting.

## Interview talking points

- **Object-format correctness:** how the `"<type> <length>\0"` header, SHA-1, and zlib layout produce IDs that real Git accepts, and why the custom text index still breaks `git fsck`.
- **Merge design tradeoff:** why whole-file three-way merge was the right scope for 24 hours, what it cannot do, and how I would extend `DiffEngine`'s LCS into a line-level three-way merge with conflict hunking and `MERGE_HEAD`.
- **AI safety boundary:** translating natural language into commands, constraining the model with a fixed command list, showing the command before execution, and requiring confirmation for `--hard`, `--mixed`, and branch deletion.

## Best fit for

Entry-level backend or developer-tools roles, especially Java CLI/tooling work, version-control or developer-experience internals, and AI-assisted developer tooling. Strongest as evidence of 0-to-1 implementation ability, debugging low-level formats, and making a technical concept inspectable.

## Do not claim

Do not claim production readiness, full Git parity, a complete Git implementation, line-level/LCS-based merge, scale or performance, security hardening, external adoption, or a hosted demo. Do not claim sole line-level authorship without mentioning AI assistance. Do not claim that real Git can operate on a mygit working tree end-to-end: it can read the object store, but the custom index is incompatible.

## Evidence

Repository and history:

```bash
git remote -v                 # origin https://github.com/DIVESHvalecha/MyGit.git
git log -1 --format='%H %ad %an' --date=iso
# b5aa26011d586563e4011b08c7e0973b84dfa2c5 2026-08-16 11:45:25 +0530 DIVESHvalecha
git rev-list --count HEAD     # 1
git tag                       # (none)
git branch -a                 # main; origin/main
```

Size and surface:

```bash
find src -name '*.java' | wc -l          # 33
find src -name '*.java' | xargs wc -l    # 3,643 total
java -jar target/MyGit-1.0-SNAPSHOT.jar --help
# 15 subcommands listed: init ... ai
```

Verified behavior (isolated temp directory, existing jar):

```bash
printf 'hello\n' > hello.txt
java -jar target/MyGit-1.0-SNAPSHOT.jar hash-object hello.txt
# ce013625030ba8dba906f756967f9e9ca394464a
git hash-object hello.txt
# ce013625030ba8dba906f756967f9e9ca394464a

java -jar target/MyGit-1.0-SNAPSHOT.jar init .
java -jar target/MyGit-1.0-SNAPSHOT.jar add hello.txt
java -jar target/MyGit-1.0-SNAPSHOT.jar commit -m first
git log --oneline            # 83c7695 first  (real Git can read the commit)
git cat-file -p HEAD         # tree + author + message readable
git fsck --full              # fatal: index file corrupt
```

Merge and stash behavior:

```bash
# Diverged branches editing the same file produced whole-file markers.
# The conflicted file contained the current version, a separator line,
# and the incoming version, wrapped by three marker lines.
#
# mygit merge reported: CONFLICT: f.txt / Automatic merge failed.
# mygit stash saved a single WIP commit; mygit stash list showed stash@{0};
# mygit stash pop restored it.
```

| Marker line | Content in the conflicted file |
|---|---|
| A line of seven `<` characters, then `main` | the current branch's full version |
| A line of seven `=` characters | separator between the two versions |
| A line of seven `>` characters, then `topic` | the incoming branch's full version |

Project guide and build environment:

```bash
pdftotext mygit-revision-guide.pdf - | head -2
# mygit — Complete Revision Guide
# Built from scratch in Java in 24 hours
pdftotext mygit-revision-guide.pdf - | grep -i 'Hacksplosion\|MERGE_HEAD'
# Built in 24 hours at Hacksplosion 2026.
# MERGE_HEAD ... We skipped this for the hackathon ...
mvn -v                        # zsh: command not found: mvn (build unverified)
find src/test -type f          # (empty)
```

Secret scan:

```bash
grep -rnIiE '(api[_-]?key|secret|token|password|BEGIN .*PRIVATE KEY)' \
  --include='*.java' --include='*.xml' --include='*.md' --include='*.txt' \
  --include='*.yml' --include='*.yaml' . | grep -v '/target/'
# Only variable/parameter names and the OPENAI_API_KEY env-var reference; no key value.
```
