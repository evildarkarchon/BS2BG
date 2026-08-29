## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues for `evildarkarchon/BS2BG`; external pull requests are not a triage request surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Triage uses the canonical `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix` labels. See `docs/agents/triage-labels.md`.

### Domain docs

This repository uses the single-context domain documentation layout. See `docs/agents/domain.md`.

### Build and verification

The complete application gate is one repository-owned command, `tools/java25/verify-java25.ps1`, which checksum-provisions the pinned Temurin 25 / JavaFX 25 inputs, asserts that `pom.xml` compiles every production source with full lint enforcement, and invokes the committed Maven Wrapper with the `.mvn/toolchains.xml` toolchain. Plain `mvnw` fails closed unless `BS2BG_JDK25_HOME` is set. A source-filtered build cannot be reported as the gate; see `docs/build/java25-verification.md`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
