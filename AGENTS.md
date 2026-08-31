# Repository Guidelines

## Project Structure & Module Organization

Production Java lives in `src/com/asdasfa/jbs2bg/`; JavaFX FXML and CSS are colocated with the classes that load them. Tests mirror production packages under `test/`, with byte-sensitive fixtures and golden files in `test-resources/`. Application artwork is in `assets/res/`. Build and packaging automation belongs in `tools/java25/`; architecture decisions and operational details live in `docs/adr/` and `docs/build/`. Treat `target/` as generated output.

## Build, Test, and Development Commands

- `.\tools\java25\verify-java25.ps1` is the canonical gate. It provisions checksum-pinned Temurin/JavaFX inputs and runs the complete clean Maven verification.
- `.\mvnw.cmd test` is a faster developer loop after `BS2BG_JDK25_HOME` points to the verified JDK 25 toolchain. Run one suite with `.\mvnw.cmd -Dtest=ProjectSessionTest test`.
- `Invoke-Pester -Path tools/java25 -Output Detailed` checks the PowerShell toolchain and packaging helpers.
- `.\tools\java25\package-java25.ps1` builds the Windows x64 app image and drives UI Automation smoke tests. Use a clean committed checkout and do not interact with the desktop during the smoke run.

## Coding Style & Naming Conventions

Follow `.editorconfig`: UTF-8, final newline, trimmed trailing whitespace, and four-space indentation where specified; match surrounding legacy Java where it differs. Use `PascalCase` for types/files, `camelCase` for methods and fields, and `UPPER_SNAKE_CASE` for constants. Java targets release 25, and `javac -Xlint:all -Werror` is enforced. Add concise Javadoc to new or substantially rewritten methods and comments that explain non-obvious constraints or lifecycle behavior. Use the domain terms in `CONTEXT.md` and preserve accepted decisions in `docs/adr/`.

## Testing Guidelines

Use JUnit Jupiter and name test classes `*Test.java`; test methods should describe behavior in lower camel case. Add focused regression tests beside the affected package and fixtures under `test-resources/` when serialization bytes matter. Do not reformat golden JSON. There is no numeric coverage threshold; the required standard is a green complete gate with no skipped or filtered tests.

## Commit & Pull Request Guidelines

Write short imperative subjects. Recent history favors `feat:`, `fix:`, and `test:` prefixes, while checkpoint commits use forms such as `Implement ... (#84)`; append the issue number when applicable. Pull requests should link the issue, summarize behavioral or architectural impact, list exact verification commands, and include screenshots for JavaFX UI changes. Call out ADR or fixture-format changes explicitly.

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues for `evildarkarchon/BS2BG`; external pull requests are not a triage request surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Triage uses the canonical `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix` labels. See `docs/agents/triage-labels.md`.

### Domain docs

This repository uses the single-context domain documentation layout. See `docs/agents/domain.md`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).