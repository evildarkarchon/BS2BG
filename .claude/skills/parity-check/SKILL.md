---
name: parity-check
description: Run BS2BG golden-file tests and summarize byte-identical failures. Use when validating a port of a formatter, writer, or slider-math change.
disable-model-invocation: true
---

# parity-check

Run the full BS2BG test suite and report byte-identical snapshot failures with diverging byte offsets. Use after porting any formatter, math routine, or file writer — or any time the user says "parity" or "golden test".

## What to run

Preflight from the repo root (`J:\jBS2BG`) using PowerShell:

1. Check that `BS2BG.sln` exists. If not, report that scaffolding is incomplete and stop — do not attempt `dotnet test`.
2. `dotnet test --nologo --verbosity quiet` from the solution root.
3. If the suite fails, re-run the failing tests with `--logger "console;verbosity=detailed"` so diff output (first diverging byte, expected vs. actual) is visible.

## What to report

- Pass / fail counts and elapsed time.
- For each failed golden-file test: fixture scenario (`minimal`, `skyrim-cbbe`, `fallout4-cbbe`, `skyrim-uunp`), target file (`templates.ini`, `morphs.ini`, `bos-json/<preset>.json`), first diverging byte offset, expected vs. actual bytes/chars around the divergence.
- If failures cluster by output type (e.g. all BoS JSON failures), call out the likely cause — usually a float formatter or line-ending issue — and point at the relevant Java reference (`minimal-json` for BoS, `MainController.java` template writer for INI).

## What NOT to do

- Never edit files under `tests/fixtures/expected/**` to make tests pass. They are regenerated only from the Java reference build (`tests/tools/generate-expected.ps1`) and only deliberately.
- Never assume a test is flaky — these are deterministic file comparisons.
