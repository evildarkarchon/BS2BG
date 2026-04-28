# Testing Patterns

**Analysis Date:** 2026-04-28

## Test Framework

**Runner:**
- xUnit v3 `3.2.2`, configured in `tests/BS2BG.Tests/BS2BG.Tests.csproj` via `xunit.v3`, `xunit.runner.visualstudio`, and `Microsoft.NET.Test.Sdk`.
- Avalonia UI tests use `Avalonia.Headless.XUnit` `12.0.1`, configured in `tests/BS2BG.Tests/BS2BG.Tests.csproj` and bootstrapped by `tests/BS2BG.Tests/AvaloniaTestApp.cs`.
- ReactiveUI test scheduling is initialized once in `tests/BS2BG.Tests/TestModuleInitializer.cs`, which calls `RxAppBuilder.CreateReactiveUIBuilder().WithCoreServices()` and pins both main-thread and task-pool schedulers to `ImmediateScheduler.Instance`.
- Config: `tests/BS2BG.Tests/BS2BG.Tests.csproj`, `Directory.Packages.props`, `Directory.Build.props`, `tests/BS2BG.Tests/AvaloniaTestApp.cs`, `tests/BS2BG.Tests/TestModuleInitializer.cs`.

**Assertion Library:**
- FluentAssertions `8.9.0` is the project assertion style. `tests/BS2BG.Tests/FluentAssertionsSetup.cs` defines `global using FluentAssertions;` and accepts the license in a module initializer.
- Use `actual.Should().Be(...)`, `.Contain(...)`, `.NotBeNull()`, `.BeEquivalentTo(...)`, `.Throw<T>()`, and `.OnlyContain(...)` instead of new bare `Assert.*` calls. Existing examples: `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/ExportWriterTests.cs`, `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`.

**Run Commands:**
```bash
dotnet test                                      # Run all tests
dotnet test --nologo --verbosity quiet          # Quiet parity-check style run
dotnet test --logger "console;verbosity=detailed" # Detailed failure output
dotnet test --collect:"XPlat Code Coverage"     # Coverage collection if local collector support is available
```

## Test File Organization

**Location:**
- Tests are centralized in `tests/BS2BG.Tests/` and reference all three projects through `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- Golden-file input and expected corpora live outside the test assembly source tree under `tests/fixtures/inputs/` and `tests/fixtures/expected/`.
- Fixture regeneration tooling lives under `tests/tools/generate-expected.ps1`; do not edit `tests/fixtures/expected/**` to silence failures.

**Naming:**
- Test classes use the subject or feature name plus `Tests`: `SliderMathFormatterTests`, `ExportWriterTests`, `MorphsViewModelStrategyTests`, `MainWindowHeadlessTests`, `CliGenerationTests`.
- Test methods use behavior-oriented names without underscores: `BodyGenIniExportWriterWritesTemplatesAndMorphsWithCrLfUtf8NoBom` in `tests/BS2BG.Tests/ExportWriterTests.cs`, `SavingStrategyConfigurationUpdatesProjectAndMarksDirty` in `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`, `ProfilesWorkspaceTabAndRequiredControlsArePresent` in `tests/BS2BG.Tests/MainWindowHeadlessTests.cs`.
- Use `[Fact]` for single scenarios, `[Theory]` plus `[InlineData]` for table-driven scalar cases, and `[AvaloniaFact]` for headless UI scenarios.

**Structure:**
```
tests/
├── BS2BG.Tests/                 # xUnit v3 test project
│   ├── *Tests.cs                # Subject-oriented test classes
│   ├── TestProfiles.cs          # Shared profile/project fixture helpers
│   ├── FluentAssertionsSetup.cs # Global FluentAssertions using + license acceptance
│   ├── TestModuleInitializer.cs # ReactiveUI scheduler initialization
│   └── AvaloniaTestApp.cs       # Avalonia headless test app bootstrap
├── fixtures/
│   ├── inputs/                  # XML/profile/NPC fixture inputs
│   ├── expected/                # Java-generated golden outputs; sacred corpus
│   └── README.md                # Fixture purpose and regeneration workflow
└── tools/
    └── generate-expected.ps1    # Java-reference golden output regeneration
```

## Test Structure

**Suite Organization:**
```typescript
// C# pattern used throughout tests/BS2BG.Tests/*.cs
public sealed class SubjectTests
{
    [Fact]
    public void MethodOrScenarioExpectedBehavior()
    {
        var subject = CreateSubjectOrService();

        var actual = subject.ExecuteDomainOperation();

        actual.Should().Be(expectedValue);
    }

    [Theory]
    [InlineData("minimal", "minimal.xml", "settings.json")]
    public void ScenarioMatrixMatchesExpectedOutput(string scenario, string xmlFileName, string profileFileName)
    {
        var profile = LoadProfile(profileFileName);
        var presets = LoadPresets(scenario, xmlFileName);

        var actual = FormatOutput(presets, profile);

        AssertFixtureText(scenario, "templates.ini", actual);
    }
}
```

**Patterns:**
- Arrange/Act/Assert is common even without comments: instantiate services or ViewModels, execute one operation, then assert visible state or file output. Examples: `tests/BS2BG.Tests/ExportWriterTests.cs`, `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`.
- Prefer real Core services for deterministic domain tests: `TemplateGenerationService`, `MorphGenerationService`, `BodyGenIniExportWriter`, `BosJsonExportWriter`, and `ProjectFileService` are directly instantiated in `tests/BS2BG.Tests/ExportWriterTests.cs` and `tests/BS2BG.Tests/TestProfiles.cs`.
- Use helper builders for repeated setup: `CreateProjectWithPresetsAndNpcs` and `CreateViewModel` in `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`; `CreateProfile` and `CreateRequestScopedCatalog` in `tests/BS2BG.Tests/TestProfiles.cs`.
- Use repository-root discovery helpers when tests must read source or fixtures from the checkout. Examples: `RepositoryRoot` in `tests/BS2BG.Tests/SliderMathFormatterTests.cs` and `FindRepositoryRoot` in `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`.
- Use local `TemporaryDirectory` helpers with `using var directory = new TemporaryDirectory();` for file-system tests; examples are in `tests/BS2BG.Tests/ExportWriterTests.cs`, `tests/BS2BG.Tests/AtomicFileWriterOutcomeTests.cs`, and `tests/BS2BG.Tests/CliGenerationTests.cs`.
- Use `TestContext.Current.CancellationToken` when awaiting ReactiveCommand execution in async xUnit tests: `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`.

## Mocking

**Framework:** hand-written fakes/stubs/null objects; no Moq/NSubstitute package detected in `Directory.Packages.props` or `tests/BS2BG.Tests/BS2BG.Tests.csproj`.

**Patterns:**
```typescript
// C# fake provider pattern from tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs
private sealed class SequenceRandomAssignmentProvider(params int[] values) : IRandomAssignmentProvider
{
    private readonly Queue<int> values = new(values);

    public int NextIndex(int exclusiveMax) => values.Count == 0 ? 0 : values.Dequeue();
}

private sealed class EmptyClipboardService : IClipboardService
{
    public Task SetTextAsync(string text, CancellationToken cancellationToken) => Task.CompletedTask;
}
```

**What to Mock:**
- Mock UI shell boundaries and OS integration: file dialogs (`IFileDialogService`, `IBodySlideXmlFilePicker`, `INpcTextFilePicker`), clipboard (`IClipboardService`), dialogs (`IAppDialogService`), image viewing (`IImageViewService`), and random assignment (`IRandomAssignmentProvider`). Examples appear in `tests/BS2BG.Tests/MainWindowViewModelTests.cs`, `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`, and `tests/BS2BG.Tests/PortableBundleServiceTests.cs`.
- Mock nondeterminism with deterministic providers instead of seeding global state: `SequenceRandomAssignmentProvider` in `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs`, `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`, and `tests/BS2BG.Tests/AssignmentStrategyReplayServiceTests.cs`.
- Use null object services for behavior that is irrelevant to the scenario: `NullAppDialogService` in `tests/BS2BG.Tests/PortableBundleServiceTests.cs`, `NullImageViewService` and `NullNoPresetNotificationService` in ViewModel constructors from `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.

**What NOT to Mock:**
- Do not mock formatter/export math when testing byte-identical output; call `SliderMathFormatter`, `TemplateGenerationService`, `MorphGenerationService`, `BodyGenIniExportWriter`, and `BosJsonExportWriter` directly. Examples: `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/ExportWriterTests.cs`.
- Do not mock the Java-generated expected corpus; compare actual output to files under `tests/fixtures/expected/**` and treat diffs as C# implementation failures unless a deliberate fixture regeneration is underway.
- Do not mock ReactiveUI schedulers per test; `tests/BS2BG.Tests/TestModuleInitializer.cs` initializes deterministic schedulers for the entire assembly.
- Do not mock Avalonia controls for view smoke tests; create a real `MainWindow` through `AppBootstrapper.CreateServiceProvider()` and use `Avalonia.Headless.XUnit`, as in `tests/BS2BG.Tests/MainWindowHeadlessTests.cs`.

## Fixtures and Factories

**Test Data:**
```typescript
// C# fixture factory style from tests/BS2BG.Tests/TestProfiles.cs
internal static TemplateProfileCatalog CreateRequestScopedCatalog(params CustomProfileDefinition[] profiles) => new(
    CreateBundledOnlyCatalog().Entries.Concat(profiles.Select(profile => new ProfileCatalogEntry(
        profile.Name,
        new TemplateProfile(profile.Name, profile.SliderProfile),
        profile.SourceKind,
        profile.FilePath,
        false))));
```

**Location:**
- Shared in-code profile/project factories: `tests/BS2BG.Tests/TestProfiles.cs`.
- Golden-file input fixtures: `tests/fixtures/inputs/minimal/minimal.xml`, `tests/fixtures/inputs/skyrim-cbbe/CBBE.xml`, `tests/fixtures/inputs/fallout4-cbbe/CBBE.xml`, `tests/fixtures/inputs/skyrim-uunp/UUNP-synthetic.xml`, `tests/fixtures/inputs/npcs/sample-npcs.txt`.
- Golden-file expected outputs: `tests/fixtures/expected/{scenario}/templates.ini`, `tests/fixtures/expected/{scenario}/morphs.ini`, `tests/fixtures/expected/{scenario}/project.jbs2bg`, and `tests/fixtures/expected/{scenario}/bos-json/*.json`.
- Hand-traced math reference: `tests/fixtures/inputs/minimal/MATH-WALKTHROUGH.md`; use it for exact assertions around rounding, inversion, multiplier application, and float formatting.
- Fixture documentation and regeneration rules: `tests/fixtures/README.md`.

## Coverage

**Requirements:** None enforced by repository configuration.

**View Coverage:**
```bash
dotnet test --collect:"XPlat Code Coverage"
```
- No checked-in coverage threshold or coverage settings file was detected. Use coverage locally as a diagnostic, but do not treat it as the project acceptance gate.
- Acceptance relies on deterministic unit tests, Avalonia headless tests, CLI tests, release/script tests, and golden-file parity tests.

## Test Types

**Unit Tests:**
- Core parser/formatter/model/service tests use direct object construction and FluentAssertions: `tests/BS2BG.Tests/BodySlideXmlParserTests.cs`, `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/TemplateGenerationServiceTests.cs`, `tests/BS2BG.Tests/MorphCoreTests.cs`, `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs`.
- ViewModel unit tests instantiate ViewModels with fake services and assert command availability, status text, undo/redo, and project mutation: `tests/BS2BG.Tests/TemplatesViewModelTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`, `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`, `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs`.

**Integration Tests:**
- Export and file-system integration tests write to temporary directories and assert line endings, UTF-8 BOM absence, atomic rollback, and generated file paths: `tests/BS2BG.Tests/ExportWriterTests.cs`, `tests/BS2BG.Tests/AtomicFileWriterOutcomeTests.cs`, `tests/BS2BG.Tests/PortableBundleServiceTests.cs`.
- CLI integration tests exercise `src/BS2BG.Cli/Program.cs` behavior and automation contracts from `tests/BS2BG.Tests/CliGenerationTests.cs`.
- Project serialization tests cover `.jbs2bg` round trips and profile recovery: `tests/BS2BG.Tests/ProjectFileServiceTests.cs`, `tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs`, `tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs`.

**E2E Tests:**
- Avalonia headless UI smoke tests use real windows/controls through `Avalonia.Headless.XUnit`: `tests/BS2BG.Tests/MainWindowHeadlessTests.cs`, `tests/BS2BG.Tests/M6UxAppShellTests.cs`, `tests/BS2BG.Tests/AppShellTests.cs`.
- There is no browser-style E2E framework. UI verification is headless Avalonia plus direct AXAML assertions for compiled bindings, automation names, and required copy.

## Common Patterns

**Async Testing:**
```typescript
// C# ReactiveCommand async execution pattern from tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs
await viewModel.SaveStrategyConfigurationCommand
    .Execute()
    .ToTask(TestContext.Current.CancellationToken);

viewModel.StrategySummaryText.Should().Contain("Saved in project; reproducible in CLI and bundles.");
```
- Await `ReactiveCommand.Execute().ToTask(TestContext.Current.CancellationToken)` rather than sleeping or polling.
- Query `CanExecute` with Rx operators when needed: `viewModel.ApplyStrategyCommand.CanExecute.FirstAsync().Wait().Should().BeFalse()` in `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`.
- Rely on `tests/BS2BG.Tests/TestModuleInitializer.cs` for synchronous ReactiveUI command and `ToProperty` updates.

**Error Testing:**
```typescript
// C# exception + rollback pattern from tests/BS2BG.Tests/ExportWriterTests.cs
var act = () => AtomicFileWriter.WriteAtomicPair(
    firstPath,
    "NEW_FIRST",
    secondPath,
    "NEW_SECOND",
    Encoding.UTF8);

act.Should().Throw<DirectoryNotFoundException>();
File.ReadAllText(firstPath).Should().Be("ORIGINAL_FIRST");
File.Exists(secondPath).Should().BeFalse();
```
- For recoverable parser errors, assert diagnostics and valid-row preservation instead of exceptions; examples belong in `tests/BS2BG.Tests/BodySlideXmlParserTests.cs` and NPC import tests such as `tests/BS2BG.Tests/NpcImportPreviewServiceTests.cs`.
- For CLI and automation errors, assert stable exit-code mapping and user-facing failure output from `src/BS2BG.Cli/Program.cs` through `tests/BS2BG.Tests/CliGenerationTests.cs`.
- For golden-file mismatches, report the scenario and target file. `tests/fixtures/README.md` and `.claude/skills/parity-check/SKILL.md` define the expected workflow: run `dotnet test --nologo --verbosity quiet`, then rerun failures with detailed console output.

---

*Testing analysis: 2026-04-28*
