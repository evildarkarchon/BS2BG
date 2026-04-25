# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**BS2BG (Bodyslide to Bodygen)** — a C# port of the Java/JavaFX tool `jBS2BG` v1.1.2 (original author: Totiman / asdasfa). Desktop utility for Skyrim SE / Fallout 4 modders that converts BodySlide preset XML into BodyGen `templates.ini` + `morphs.ini` and BoS JSON exports.

**Current status: M0–M7 milestones implemented and archived.** Core import/generation/export, Avalonia UI with Templates/Morphs tabs and slider inspector, NPC import + assignment, undo/redo, project round-trip serialization, and release packaging are all in place. Subsequent work continues in the OpenSpec workflow (see `openspec/`).

## Canonical specs — read these before non-trivial work

- `PRD.md` — product spec (parity checklist, UI flows, architecture, risks, milestones, open questions). Read relevant sections before touching the area they describe.
- `tests/fixtures/inputs/minimal/MATH-WALKTHROUGH.md` — hand-traced slider-math expected values. Source of truth for unit test assertions on rounding, inversion, multipliers, and float formatting.
- `tests/fixtures/README.md` — fixture corpus layout and regeneration workflow.
- `openspec/changes/archive/` — completed change proposals (M0–M7, FluentAssertions migration). Useful for "why was this done this way?" questions; don't re-litigate decisions captured here.
- `openspec/specs/` — current delta specs by capability (e.g. `template-generation-flow`, `morph-assignment-flow`, `release-polish`). Update when adding to those capabilities.

## Solution layout

`BS2BG.sln` contains three projects (centralized package versions in `Directory.Packages.props`, common build properties in `Directory.Build.props`):

- **`src/BS2BG.Core/`** — `netstandard2.1`, `LangVersion 13`. Pure domain + I/O, no Avalonia or platform deps. Subfolders:
  - `Formatting/` — `SliderMathFormatter`, `JavaFloatFormatting`, slider/profile model classes used by the formatter.
  - `Generation/` — `TemplateGenerationService`, `MorphGenerationService`, `TemplateProfileCatalog`, `SliderProfileJsonService`.
  - `Import/` — `BodySlideXmlParser`, `NpcTextParser`, plus result/diagnostic types.
  - `Export/` — `BodyGenIniExportWriter`, `BosJsonExportWriter`.
  - `Models/` — `ProjectModel`, `ProjectProfile`, `Npc`, `CustomMorphTarget`, etc.
  - `Morphs/` — `MorphAssignmentService` and the random-assignment provider abstraction.
  - `Serialization/` — `ProjectFileService` (project file save/load).
- **`src/BS2BG.App/`** — `net10.0`, `LangVersion 14`, `WinExe`. Avalonia 12 UI. Subfolders:
  - `Views/` — `MainWindow.axaml(.cs)`.
  - `ViewModels/` — `MainWindowViewModel`, `TemplatesViewModel`, `MorphsViewModel`, `SetSliderInspectorRowViewModel`, plus the `RelayCommand`/`AsyncRelayCommand` infrastructure.
  - `Services/` — file dialog, clipboard, NPC text picker, image-view, user-preferences, etc.
  - `Themes/` — `ThemeResources.axaml`.
  - Root: `Program.cs`, `App.axaml(.cs)`, `AppShell.cs`, `AppBootstrapper.cs`.
- **`tests/BS2BG.Tests/`** — `net10.0`, `LangVersion 14`. xUnit v3 + FluentAssertions + `Avalonia.Headless.XUnit` for headless UI tests. Golden-file snapshot tests for formatters/exports/parsers; ViewModel and service tests for the App layer.

Legacy directories at the root that are **not** part of the C# build:
- `src/com/asdasfa/jbs2bg/` — Java reference (see "Java reference" below).
- `src/jfx-8u60-b08/` — embedded OpenJFX 8 source snapshot. Ignore.
- `assets/`, `bin/`, `build.fxbuild` — leftovers from the Java/JavaFX project (SceneBuilder file, packaged binaries). Ignore unless explicitly working on the Java reference.
- `tools/release/`, `docs/release/`, `artifacts/` — release packaging scripts, docs, and build outputs (`artifacts/codex-build`, `artifacts/release`, `artifacts/test-out`).

## Target stack — actual usage

- **.NET 10 / C# 14** for App and Tests; **netstandard2.1 / C# 13** for Core (so the formatter/export logic stays portable to any future headless or CLI build).
- **Avalonia 12** with **compiled bindings on by default** (`AvaloniaUseCompiledBindingsByDefault=true` in `BS2BG.App.csproj`). Every AXAML file must declare `x:DataType` on its root and on every `DataTemplate` — there are working examples in `MainWindow.axaml`.
- **ReactiveUI patterns** (restored by the `restore-reactiveui-patterns` change): ViewModels inherit `ReactiveObject` for INPC; notifying properties use `[Reactive]` (from `ReactiveUI.SourceGenerators` — Fody is incompatible with the ReactiveUI 23.x line that ships with `ReactiveUI.Avalonia 12.0.1`); commands are `ReactiveCommand` instances created via `ReactiveCommand.Create*` factories (sync) or `ReactiveCommand.CreateFromTask((CancellationToken) => Task)` (async — preserves cancellation); `canExecute` is supplied as `IObservable<bool>` from `this.WhenAnyValue(...)` / `Observable.CombineLatest(...)`, not a `Func<bool>` callback; derived state (e.g. aggregate `IsAnyBusy`) flows through `WhenAnyValue(...).ToProperty(...)` / `Observable.CombineLatest(...).ToProperty(...)`. Background work runs on `RxApp.TaskpoolScheduler`; ViewModels MUST NOT call `Dispatcher.UIThread.InvokeAsync` directly. The contract is captured in `openspec/specs/reactive-mvvm-conventions/spec.md`. The custom `RelayCommand` / `RelayCommandOfT` / `AsyncRelayCommand` types and the `RaiseAndSetIfChanged` setter pattern have been retired — do not reintroduce them.
- **Plain `Window`, not `ReactiveWindow`** — `MainWindow` inherits `Avalonia.Controls.Window`. The base-class change started in commit `2a0fb46` (which only updated the AXAML) and was completed by `restore-reactiveui-patterns` (which flipped the code-behind too). Keep new windows plain unless you specifically need `ReactiveWindow` lifecycle.
- **Test bootstrapping** — ReactiveUI 23.x requires explicit init. `tests/BS2BG.Tests/TestModuleInitializer.cs` calls `RxAppBuilder.CreateReactiveUIBuilder().WithCoreServices()` and pins both `MainThreadScheduler` and `TaskpoolScheduler` to `ImmediateScheduler.Instance` so `ReactiveCommand.IsExecuting` and `ToProperty` updates propagate synchronously in tests. New ViewModel tests need no extra setup; the module initializer fires at assembly load time.
- **`System.Text.Json`** for JSON, **`XDocument`** for BodySlide XML.
- **xUnit v3** for tests (required by `Avalonia.Headless.XUnit` 12) + **FluentAssertions** for assertions (migration archived in `openspec/changes/archive/2026-04-24-migrate-tests-to-fluentassertions`). Use FluentAssertions style (`x.Should().Be(...)`) in new tests, not bare `Assert.*`.

## Sacred files — do not edit without asking

- `tests/fixtures/expected/**` — golden-file corpus (CBBE / UUNP / FO4-CBBE INI + BoS JSON outputs). Regenerating requires the Java reference build (JDK 8 + JavaFX 8) via `tests/tools/generate-expected.ps1`. **If a test fails, the C# code is almost certainly wrong, not the expected file.**
- `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs` — the dual float formatters (`FormatForText` Java-like, `FormatForMinimalJsonNumber` minimal-json-like) plus `RoundHalfUpToTwoDecimals` (uses `MidpointRounding.AwayFromZero`). Changing either silently invalidates golden tests.
- `src/BS2BG.Core/Formatting/SliderMathFormatter.cs` — the templates/BoS line-format pipeline, including missing-default injection and inversion/multiplier application.
- `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs` — INI uses `\r\n`; BoS JSON uses `\n` only with **no trailing newline**.

Flag proposed changes to any of these before making them.

## Byte-identical output is load-bearing

Golden-file tests compare C# output against Java output byte-for-byte. Things that will silently break tests:

- **Line endings**: INI files write `\r\n`. BoS JSON (mirroring Java's `minimal-json`) writes `\n` only and **no trailing newline**.
- **Rounding**: `Math.Round(x, 2, MidpointRounding.AwayFromZero)` — matches Java `BigDecimal.ROUND_HALF_UP`. Do not use default banker's rounding.
- **Two float formatters, context-dependent**:
  - `templates.ini` → Java-like (`JavaFloatFormatting.FormatForText`): integer-valued floats keep `.0` (e.g. `0.0f → "0.0"`).
  - BoS JSON → `minimal-json`-like (`JavaFloatFormatting.FormatForMinimalJsonNumber`): integer-valued floats drop `.0` (e.g. `0.0f → "0"`).
- **Missing-default injection**: if a slider is absent from the current profile's Defaults table, all 12 CBBE defaults (per profile) are injected by `SliderMathFormatter`. See `data/Settings.java` + `MainController.java` in the Java reference.
- **Profile switching**: CBBE vs UUNP vs FO4-CBBE swaps Defaults / Multipliers / Inverted lookup tables. Do not share state across profiles.

## OpenSpec workflow

This project uses the experimental OpenSpec workflow (`openspec/`) to track scoped changes:

- `openspec/changes/` — open proposals; `openspec/changes/archive/` — completed proposals (one per milestone).
- `openspec/specs/` — current delta specs grouped by capability.
- Use the `openspec-*` / `opsx:*` skills to create, continue, fast-forward, verify, and archive changes. For non-trivial features prefer `opsx:propose` (one-shot) or `opsx:new` → `opsx:continue` (stepwise). Archive with `opsx:archive` once the implementation matches the proposal.

## Java reference

The authoritative implementation lives in `src/com/asdasfa/jbs2bg/`. When porting or cross-checking a feature, consult:

- Slider math + formatting — `MainController.java` (~2000 lines, monolithic) and `data/SliderPreset.java`. C# equivalents: `Core/Formatting/SliderMathFormatter.cs`, `Core/Formatting/JavaFloatFormatting.cs`.
- Settings / profiles — `data/Settings.java`, root-level `settings.json` and `settings_UUNP.json`. C# equivalent: `Core/Generation/SliderProfileJsonService.cs` + `TemplateProfileCatalog.cs`.
- NPC model + import — `data/NPC.java` (pipe-delimited `Mod|Name|EditorID|Race|FormID`). C# equivalent: `Core/Import/NpcTextParser.cs`, `Core/Models/Npc.cs`.
- TableFilter — `controlsfx/table/*.java`. No Avalonia equivalent yet; if the inspector grows to need filtering, a custom control is required.
- Headless test harness — `testharness/FixtureDriver.java`. Used by `tests/tools/generate-expected.ps1` to produce the golden corpus.
- Custom morphs — `data/CustomMorphTarget.java`, `data/MorphTarget.java`. C# equivalents: `Core/Models/CustomMorphTarget.cs`, `Core/Models/MorphTargetBase.cs`.

The `java-ref` skill maps porting topics to specific Java files — use it before porting a new feature so the C# implementation follows the authoritative Java logic.

## Building and testing

- Build: `dotnet build BS2BG.sln`
- All tests: `dotnet test`
- Run the App: `dotnet run --project src/BS2BG.App/BS2BG.App.csproj`
- Golden-file snapshot tests live in `tests/BS2BG.Tests/`; failures print the first diverging byte offset.
- Regenerating the `expected/` corpus requires the Java reference build + JavaFX 8; see `tests/tools/generate-expected.ps1` and `tests/fixtures/README.md`. **Do not regenerate to silence a failing test.**

## Known risks (from PRD §7)

1. TableFilter has no Avalonia equivalent — custom control required if filtering is added.
2. Dual float formatters (see above).
3. Rounding parity (see above).
4. NPC import encoding — Java uses `juniversalchardet`; C# detects UTF-8 BOM and falls back to UTF-8 decode (`Core/Import/NpcTextParser.cs`).
5. Fallout 4 profile ships experimental (defaults-only); tuning deferred post-v2.0.

## Environment

- Primary dev platform: Windows. **Use PowerShell, never Bash.** (The user's global CLAUDE.md forbids Bash; honor it here.)
- Never redirect output to `nul` on Windows — it creates an undeletable file on the system drive. Use `$null` or `| Out-Null` in PowerShell.
- MCP docs servers: `avalonia-docs` for Avalonia API questions; `context7` / `Ref` for other library docs; `Microsoft-Learn` for .NET / MSBuild.
