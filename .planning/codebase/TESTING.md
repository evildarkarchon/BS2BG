# Testing Patterns

**Analysis Date:** 2026-04-26

## Test Framework

**Runner:**
- xUnit v3 `3.2.2`, configured in `tests/BS2BG.Tests/BS2BG.Tests.csproj` via `xunit.v3`, `xunit.runner.visualstudio`, and `Microsoft.NET.Test.Sdk`.
- Avalonia headless UI tests use `Avalonia.Headless.XUnit` `12.0.1`, configured in `tests/BS2BG.Tests/BS2BG.Tests.csproj` and bootstrapped by `tests/BS2BG.Tests/AvaloniaTestApp.cs`.
- ReactiveUI test initialization is centralized in `tests/BS2BG.Tests/TestModuleInitializer.cs`.

**Assertion Library:**
- FluentAssertions `8.9.0` is the standard assertion library. Use `.Should()` style assertions in new tests.
- `tests/BS2BG.Tests/FluentAssertionsSetup.cs` accepts the FluentAssertions license and provides a global using.
- Bare `Assert.*` is not used in current tests; keep new tests on FluentAssertions.

**Run Commands:**
```bash
dotnet test                         # Run all tests
dotnet test --filter FullyQualifiedName~SliderMathFormatterTests  # Run one test class
dotnet test --collect:"XPlat Code Coverage"  # Coverage collection when a collector is available
```

## Test File Organization

**Location:**
- Tests live in one test project: `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- Test files are flat under `tests/BS2BG.Tests/`, not split into subfolders: `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`, `tests/BS2BG.Tests/AppShellTests.cs`.
- Fixtures live outside the test project source folder under `tests/fixtures/`, with expected golden outputs copied to test output by `tests/BS2BG.Tests/BS2BG.Tests.csproj`.

**Naming:**
- Test classes use `{UnitUnderTest}Tests`: `BodySlideXmlParserTests`, `TemplateGenerationServiceTests`, `MainWindowViewModelTests`.
- Test methods use behavior-descriptive PascalCase names: `ParseStringReadsPresetsWithOrWithoutXmlDeclaration` in `tests/BS2BG.Tests/BodySlideXmlParserTests.cs`, `BodyGenIniExportWriterWritesTemplatesAndMorphsWithCrLfUtf8NoBom` in `tests/BS2BG.Tests/ExportWriterTests.cs`.
- Use `[Fact]` for single scenarios, `[Theory]` + `[InlineData]` for parameterized parity/validation cases, and `[AvaloniaFact]` for headless UI control/window scenarios.

**Structure:**
```text
tests/
├── BS2BG.Tests/                 # xUnit v3 test project
│   ├── *Tests.cs                # Flat test classes
│   ├── AvaloniaTestApp.cs       # Avalonia headless assembly bootstrap
│   ├── TestModuleInitializer.cs # ReactiveUI scheduler bootstrap
│   └── FluentAssertionsSetup.cs # FluentAssertions license/global using
└── fixtures/
    ├── inputs/                  # XML/profile/NPC inputs
    ├── expected/                # Golden Java-reference outputs
    └── tools/                   # Fixture regeneration script
```

## Test Structure

**Suite Organization:**
```csharp
public sealed class BodySlideXmlParserTests
{
    [Theory]
    [InlineData("")]
    [InlineData("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")]
    public void ParseStringReadsPresetsWithOrWithoutXmlDeclaration(string declaration)
    {
        var xml = declaration + """
                                <SliderPresets>
                                  <Preset name="CBBE Curvy (Outfit)" set="Ignored">
                                    <SetSlider name="Breasts" size="big" value="75" ignored="true"/>
                                  </Preset>
                                </SliderPresets>
                                """;
        var parser = new BodySlideXmlParser();

        var result = parser.ParseString(xml, "sample.xml");

        result.Diagnostics.Should().BeEmpty();
        result.Presets.Should().ContainSingle();
    }
}
```

**Patterns:**
- Follow Arrange/Act/Assert separated by blank lines, as in `tests/BS2BG.Tests/BodySlideXmlParserTests.cs`, `tests/BS2BG.Tests/TemplatesViewModelTests.cs`, and `tests/BS2BG.Tests/ExportWriterTests.cs`.
- Prefer exact assertions for parity-sensitive strings, ordering, status messages, and file paths: `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`.
- Use helper factory methods at the bottom of each test class for repeated setup: `CreateViewModel` in `tests/BS2BG.Tests/TemplatesViewModelTests.cs`, `CreateProjectWithPresets` in `tests/BS2BG.Tests/MorphsViewModelTests.cs`.
- Use nested private fake/stub classes inside the test class that needs them: `BlockingFilePicker` and `CapturingClipboardService` in `tests/BS2BG.Tests/TemplatesViewModelTests.cs`; `QueueRandomAssignmentProvider` in `tests/BS2BG.Tests/MorphsViewModelTests.cs`.
- Use `using var` for disposable test resources: `TemporaryDirectory` in `tests/BS2BG.Tests/ExportWriterTests.cs`, `tests/BS2BG.Tests/TemplatesViewModelTests.cs`, and `tests/BS2BG.Tests/MorphsViewModelTests.cs`.

## Mocking

**Framework:** hand-written fakes/stubs; no Moq/NSubstitute/FakeItEasy dependency is detected.

**Patterns:**
```csharp
private sealed class QueueRandomAssignmentProvider(params int[] values) : IRandomAssignmentProvider
{
    private readonly Queue<int> values = new(values);

    public int NextIndex(int exclusiveMax)
    {
        if (values.Count == 0) return 0;

        return values.Dequeue();
    }
}

private sealed class CapturingClipboardService : IClipboardService
{
    public string? Text { get; private set; }

    public Task SetTextAsync(string text, CancellationToken cancellationToken)
    {
        Text = text;
        return Task.CompletedTask;
    }
}
```

**What to Mock:**
- Mock UI boundary interfaces and external interactions: `IClipboardService`, `IBodySlideXmlFilePicker`, `INpcTextFilePicker`, `IFileDialogService`, `IAppDialogService`, `IImageViewService`, and `INoPresetNotificationService` from `src/BS2BG.App/Services/`.
- Mock randomness through `IRandomAssignmentProvider` from `src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs`; use deterministic queues as in `tests/BS2BG.Tests/MorphsViewModelTests.cs`.
- Mock delayed async file picking with `TaskCompletionSource` when asserting busy state: `BlockingFilePicker` in `tests/BS2BG.Tests/TemplatesViewModelTests.cs`.
- Use real Core services where deterministic and cheap: `BodySlideXmlParser`, `TemplateGenerationService`, `MorphGenerationService`, and `ProjectFileService` are constructed directly in `tests/BS2BG.Tests/TemplatesViewModelTests.cs` and `tests/BS2BG.Tests/MainWindowViewModelTests.cs`.

**What NOT to Mock:**
- Do not mock `SliderMathFormatter`, `JavaFloatFormatting`, `BodyGenIniExportWriter`, or `BosJsonExportWriter`; these are parity-critical and should be exercised directly against fixtures in `tests/BS2BG.Tests/SliderMathFormatterTests.cs` and `tests/BS2BG.Tests/ExportWriterTests.cs`.
- Do not mock domain models such as `ProjectModel`, `SliderPreset`, `SetSlider`, `Npc`, or `CustomMorphTarget`; instantiate them directly as in `tests/BS2BG.Tests/MorphCoreTests.cs` and `tests/BS2BG.Tests/TemplatesViewModelTests.cs`.
- Do not regenerate or edit `tests/fixtures/expected/**` to make tests pass; failing golden comparisons indicate production logic drift unless the Java reference output has intentionally changed.

## Fixtures and Factories

**Test Data:**
```csharp
private static TemplateProfileCatalog CreateCatalog()
{
    var regular = new SliderProfile(
        Array.Empty<SliderDefault>(),
        Array.Empty<SliderMultiplier>(),
        Array.Empty<string>());
    var doubled = new SliderProfile(
        Array.Empty<SliderDefault>(),
        new[] { new SliderMultiplier("Scale", 2f) },
        Array.Empty<string>());

    return new TemplateProfileCatalog(new[]
    {
        new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, regular), new TemplateProfile("Double", doubled)
    });
}
```

**Location:**
- Golden fixture documentation is in `tests/fixtures/README.md`.
- Fixture inputs live under `tests/fixtures/inputs/`, including `tests/fixtures/inputs/minimal/minimal.xml`, `tests/fixtures/inputs/skyrim-cbbe/CBBE.xml`, `tests/fixtures/inputs/skyrim-uunp/UUNP-synthetic.xml`, and `tests/fixtures/inputs/npcs/sample-npcs.txt`.
- Golden expected outputs live under `tests/fixtures/expected/` and are copied by `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- The hand-traced math source of truth is `tests/fixtures/inputs/minimal/MATH-WALKTHROUGH.md`.
- Fixture regeneration tooling lives in `tests/tools/generate-expected.ps1`, but expected files are sacred unless explicitly regenerating from the Java reference.

## Coverage

**Requirements:** None enforced in project files. No `.runsettings`, coverlet package, or coverage threshold is configured.

**View Coverage:**
```bash
dotnet test --collect:"XPlat Code Coverage"
```

## Test Types

**Unit Tests:**
- Core formatter/parser/model unit tests assert deterministic behavior directly: `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/BodySlideXmlParserTests.cs`, `tests/BS2BG.Tests/SliderPresetTests.cs`, `tests/BS2BG.Tests/MorphCoreTests.cs`.
- App service tests instantiate concrete services with local temp files or headless windows: `tests/BS2BG.Tests/UserPreferencesServiceTests.cs`, `tests/BS2BG.Tests/WindowImageViewServiceTests.cs`, `tests/BS2BG.Tests/WindowNoPresetNotificationServiceTests.cs`.

**Integration Tests:**
- ViewModel integration-style tests wire real Core services plus fake boundaries to test workflows: `tests/BS2BG.Tests/TemplatesViewModelTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`, `tests/BS2BG.Tests/MainWindowViewModelTests.cs`.
- DI/shell integration is tested through `AppBootstrapper.CreateServiceProvider()` and `MainWindow` resolution in `tests/BS2BG.Tests/AppShellTests.cs`.
- Golden-file integration tests compare C# output to Java-reference output in `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/ExportWriterTests.cs`, and `tests/BS2BG.Tests/ProjectFileServiceTests.cs`.

**E2E Tests:**
- Browser-style E2E tests are not used.
- Headless UI smoke/structure tests use Avalonia Headless and `[AvaloniaFact]`: `tests/BS2BG.Tests/AppShellTests.cs`, `tests/BS2BG.Tests/M7ReleasePolishTests.cs`, `tests/BS2BG.Tests/WindowImageViewServiceTests.cs`.

## Common Patterns

**Async Testing:**
```csharp
[Fact]
public async Task CopyGeneratedTemplatesUsesClipboardAndReportsEmptyOutput()
{
    var clipboard = new CapturingClipboardService();
    var viewModel = CreateViewModel(clipboard: clipboard);

    await viewModel.CopyGeneratedTemplatesAsync(TestContext.Current.CancellationToken);

    clipboard.Text.Should().BeNull();
    viewModel.StatusMessage.Should().Be("Generate templates before copying.");
}
```
- Pass `TestContext.Current.CancellationToken` into async APIs in tests: `tests/BS2BG.Tests/TemplatesViewModelTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`.
- Convert `ReactiveCommand` execution to tasks with `.Execute().ToTask()` when asserting execution state: `tests/BS2BG.Tests/TemplatesViewModelTests.cs`.
- ReactiveUI schedulers are pinned to `ImmediateScheduler.Instance` in `tests/BS2BG.Tests/TestModuleInitializer.cs`; new ViewModel tests should not add per-test scheduler bootstrapping.

**Error Testing:**
```csharp
[Fact]
public void AtomicFileWriterWriteAtomicPairLeavesTargetsUntouchedOnPhase1Failure()
{
    using var directory = new TemporaryDirectory();
    var firstPath = Path.Combine(directory.Path, "first.txt");
    var secondPath = Path.Combine(directory.Path, "missing-subdir", "second.txt");
    File.WriteAllText(firstPath, "ORIGINAL_FIRST");

    var act = () => AtomicFileWriter.WriteAtomicPair(
        firstPath,
        "NEW_FIRST",
        secondPath,
        "NEW_SECOND",
        Encoding.UTF8);

    act.Should().Throw<DirectoryNotFoundException>();
    File.ReadAllText(firstPath).Should().Be("ORIGINAL_FIRST");
    File.Exists(secondPath).Should().BeFalse();
}
```
- Use `var act = () => ...; act.Should().Throw<TException>();` for expected exceptions: `tests/BS2BG.Tests/ExportWriterTests.cs`, `tests/BS2BG.Tests/SliderPresetTests.cs`.
- For UI command failures, assert no synchronization-context exception and assert status text: `RecordingSynchronizationContext` in `tests/BS2BG.Tests/TemplatesViewModelTests.cs`.
- For recoverable parser errors, assert diagnostics instead of exceptions: `tests/BS2BG.Tests/BodySlideXmlParserTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`.

---

*Testing analysis: 2026-04-26*
