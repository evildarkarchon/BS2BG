# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**BS2BG (Bodyslide to Bodygen)** — a C# port of the Java/JavaFX tool `jBS2BG` v1.1.2 (original author: Totiman / asdasfa). Desktop utility for Skyrim SE / Fallout 4 modders that converts BodySlide preset XML into BodyGen `templates.ini` + `morphs.ini` and BoS JSON exports.

Current status: **planning only, no C# code yet**. Java reference and test fixtures are in place; M0 scaffolding is the next step.

## Canonical specs — read these before non-trivial work

- `PRD.md` — 430-line product spec (parity checklist, UI flows, architecture, risks, milestones, open questions). Read relevant sections before touching the area they describe.
- `tests/fixtures/inputs/minimal/MATH-WALKTHROUGH.md` — hand-traced slider-math expected values. Source of truth for unit test assertions on rounding, inversion, multipliers, and float formatting.
- `tests/fixtures/README.md` — fixture corpus layout and regeneration workflow.

## Target stack

- .NET 9, C# 13
- Avalonia 11 with `CommunityToolkit.Mvvm` (ObservableProperty / RelayCommand source generators)
- `System.Text.Json` for JSON, `XDocument` for BodySlide XML
- xUnit for tests
- Planned solution: `BS2BG.sln` with `src/BS2BG.Core` (netstandard2.1, pure domain + I/O), `src/BS2BG.App` (Avalonia UI), `src/BS2BG.Tests` (xUnit + golden-file snapshots)

## Sacred files — do not edit without asking

- `tests/fixtures/expected/**` — golden-file corpus. Regenerating requires the Java reference build (JDK 8 + JavaFX 8) via `tests/tools/generate-expected.ps1`. If a test fails, the C# code is almost certainly wrong, not the expected file.
- `SliderMathFormatter`, the dual float formatters, and any INI/BoS JSON writer (once written) — changing these invalidates golden tests. Flag proposed changes before making them.

## Byte-identical output is load-bearing

Golden-file tests compare C# output against Java output byte-for-byte. Things that will silently break tests:

- **Line endings**: INI files must write `\r\n`. BoS JSON is written by Java's `minimal-json` — matching means `\n` only and no trailing newline.
- **Rounding**: `Math.Round(x, 2, MidpointRounding.AwayFromZero)` — matches Java `BigDecimal.ROUND_HALF_UP`. Do not use default banker's rounding.
- **Two float formatters, context-dependent**:
  - `templates.ini` → Java-like: integer-valued floats keep `.0` (e.g. `0.0f → "0.0"`).
  - BoS JSON → `minimal-json`-like: integer-valued floats drop `.0` (e.g. `0.0f → "0"`).
- **Missing-default injection**: if a slider is absent from the current profile's Defaults table, all 12 CBBE defaults (per profile) are injected. See `data/Settings.java` + `MainController.java` in the Java reference.
- **Profile switching**: CBBE vs UUNP vs FO4-CBBE swaps Defaults / Multipliers / Inverted lookup tables. Do not share state across profiles.

## Java reference

The authoritative implementation lives in `src/com/asdasfa/jbs2bg/`. When porting a specific feature, consult:

- Slider math + formatting — `MainController.java` (~2000 lines, monolithic) and `data/SliderPreset.java`
- Settings / profiles — `data/Settings.java`, root-level `settings.json` and `settings_UUNP.json`
- NPC model + import — `data/NPC.java` (pipe-delimited `Mod|Name|EditorID|Race|FormID`)
- TableFilter (no Avalonia equivalent; must be ported as a custom control) — `controlsfx/table/*.java`
- Headless test harness — `testharness/FixtureDriver.java`
- Custom morphs — `data/CustomMorphTarget.java`, `data/MorphTarget.java`

Ignore `src/jfx-8u60-b08/` — it's an embedded OpenJFX 8 source snapshot, not live code.

## Running tests (once scaffolded)

- All tests: `dotnet test`
- Golden-file snapshot tests live in `BS2BG.Tests`; failures print the first diverging byte offset.
- Regenerating the `expected/` corpus requires the Java reference build + JavaFX 8; see `tests/tools/generate-expected.ps1` and `tests/fixtures/README.md`. Do not regenerate to silence a failing test.

## Known risks (from PRD §7)

1. TableFilter has no Avalonia equivalent — custom control required.
2. Dual float formatters (see above).
3. Rounding parity (see above).
4. NPC import encoding — Java uses `juniversalchardet`; C# should detect UTF-8 BOM and fall back to UTF-8 decode.
5. Fallout 4 profile ships experimental (defaults-only); tuning deferred post-v2.0.

## Environment

- Primary dev platform: Windows. **Use PowerShell, never Bash.** (The user's global CLAUDE.md forbids Bash; honor it here.)
- Never redirect output to `nul` on Windows — it creates an undeletable file on the system drive. Use `$null` or `| Out-Null` in PowerShell.
- MCP docs servers: `avalonia-docs` for Avalonia API questions; `context7` / `Ref` for other library docs; `Microsoft-Learn` for .NET / MSBuild.
