using System.Text.Json;
using System.IO.Compression;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Cli;
using BS2BG.Core.Automation;
using BS2BG.Core.Bundling;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using System.Reactive.Threading.Tasks;
using System.Reactive.Linq;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using SliderDefault = BS2BG.Core.Formatting.SliderDefault;
using SliderMultiplier = BS2BG.Core.Formatting.SliderMultiplier;
using SliderProfile = BS2BG.Core.Formatting.SliderProfile;

namespace BS2BG.Tests;

[Collection(ConsoleCaptureCollection.ConsoleCaptureCollectionName)]
public sealed class PortableBundleServiceTests
{
    private static readonly DateTimeOffset FixedCreatedUtc = new(2026, 4, 28, 3, 0, 0, TimeSpan.Zero);
    private static readonly object ConsoleLock = new();
    private static readonly string[] PresetAName = ["PresetA"];
    private static readonly string[] PresetBName = ["PresetB"];
    private static readonly string[] PresetCName = ["PresetC"];
    private static readonly string[] NordRaceName = ["NordRace"];
    private static readonly string[] MrHandyRaceName = ["MrHandyRace"];
    private static readonly string[] BretonRaceName = ["BretonRace"];

    [Fact]
    public void BundleCommandRequiresProjectBundleAndIntentOptions()
    {
        var result = InvokeProgramMain("bundle");
        var combinedOutput = result.StandardOutput + result.StandardError;

        result.ExitCode.Should().Be((int)AutomationExitCode.UsageError);
        combinedOutput.Should().Contain("Usage:");
        combinedOutput.Should().Contain("--project");
        combinedOutput.Should().Contain("--bundle");
        combinedOutput.Should().Contain("--intent");
        combinedOutput.Should().Contain("bodygen");
        combinedOutput.Should().Contain("bos");
        combinedOutput.Should().Contain("all");
    }

    [Fact]
    public void ProgramMainBundleRefusesExistingZipWithoutOverwrite()
    {
        using var directory = new TemporaryDirectory();
        CopyProfileAssetsToTestAssemblyDirectory();
        var projectPath = SaveProject(directory.Path, CreateProjectWithAssignedPreset());
        var bundlePath = Path.Combine(directory.Path, "share.zip");
        File.WriteAllText(bundlePath, "original");

        var result = InvokeProgramMain(
            "bundle",
            "--project", projectPath,
            "--bundle", bundlePath,
            "--intent", "bodygen");

        result.ExitCode.Should().Be((int)AutomationExitCode.OverwriteRefused);
        result.StandardError.Should().Contain("Target files already exist. Enable overwrite to replace them.");
        File.ReadAllText(bundlePath).Should().Be("original");
    }

    [Fact]
    public void ProgramMainBundleCreatesZipAndPrintsPathAndEntryCount()
    {
        using var directory = new TemporaryDirectory();
        CopyProfileAssetsToTestAssemblyDirectory();
        var projectPath = SaveProject(directory.Path, CreateProjectWithAssignedPreset());
        var bundlePath = Path.Combine(directory.Path, "share.zip");

        var result = InvokeProgramMain(
            "bundle",
            "--project", projectPath,
            "--bundle", bundlePath,
            "--intent", "all");

        result.ExitCode.Should().Be((int)AutomationExitCode.Success);
        result.StandardOutput.Should().Contain(bundlePath);
        result.StandardOutput.Should().MatchRegex(@"entries:\s*\d+");
        result.StandardError.Should().BeEmpty();
        using var archive = ZipFile.OpenRead(bundlePath);
        archive.Entries.Should().Contain(entry => entry.FullName == "manifest.json");
    }

    [Fact]
    public void BundleOutcomesMapToSharedAutomationExitCodes()
    {
        Program.MapBundleOutcome(PortableProjectBundleOutcome.Success).Should().Be(AutomationExitCode.Success);
        Program.MapBundleOutcome(PortableProjectBundleOutcome.ValidationBlocked).Should().Be(AutomationExitCode.ValidationBlocked);
        Program.MapBundleOutcome(PortableProjectBundleOutcome.MissingProfile).Should().Be(AutomationExitCode.ValidationBlocked);
        Program.MapBundleOutcome(PortableProjectBundleOutcome.OverwriteRefused).Should().Be(AutomationExitCode.OverwriteRefused);
        Program.MapBundleOutcome(PortableProjectBundleOutcome.IoFailure).Should().Be(AutomationExitCode.IoFailure);
    }

    [Fact]
    public async Task PreviewPortableBundleCommandPopulatesLayoutWithoutWritingZip()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithAssignedPreset();
        var bundlePath = Path.Combine(directory.Path, "preview.zip");
        var viewModel = CreateBundleViewModel(project);
        viewModel.BundleTargetPath = bundlePath;
        viewModel.BundleOutputIntent = OutputIntent.All;

        await viewModel.PreviewPortableBundleCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        viewModel.BundlePreviewSummary.Should().Contain("Portable bundle preview");
        viewModel.BundlePreviewSummary.Should().Contain("Referenced custom profiles only");
        viewModel.BundlePreviewSummary.Should().Contain("No absolute paths in manifest/report");
        viewModel.BundlePreviewEntries.Should().Contain([
            "project/",
            "outputs/bodygen/",
            "outputs/bos/",
            "profiles/",
            "reports/",
            "manifest.json",
            "SHA256SUMS.txt",
        ]);
        File.Exists(bundlePath).Should().BeFalse("preview must not create/delete a temporary zip");
    }

    [Fact]
    public async Task CreatePortableBundleCommandRefusesExistingTargetWithoutExplicitOverwrite()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithAssignedPreset();
        var bundlePath = Path.Combine(directory.Path, "existing.zip");
        File.WriteAllText(bundlePath, "original");
        var viewModel = CreateBundleViewModel(project);
        viewModel.BundleTargetPath = bundlePath;
        viewModel.BundleOutputIntent = OutputIntent.BodyGen;
        viewModel.BundleOverwriteAllowed = false;

        await viewModel.CreatePortableBundleCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        viewModel.StatusMessage.Should().Contain("Target files already exist. Enable overwrite to replace them.");
        File.ReadAllText(bundlePath).Should().Be("original");
    }

    [Fact]
    public void MainWindowExposesPortableBundlePreviewCopyAndAccessibleCreateAction()
    {
        var axaml = File.ReadAllText(Path.Combine(FindRepoRoot(), "src", "BS2BG.App", "Views", "MainWindow.axaml"));

        axaml.Should().Contain("Portable bundle preview");
        axaml.Should().Contain("Create Portable Bundle");
        axaml.Should().Contain("Referenced custom profiles only");
        axaml.Should().Contain("Bundle reports use relative paths and source filenames only.");
        axaml.Should().Contain("Target files already exist. Enable overwrite to replace them.");
        axaml.Should().Contain("AutomationProperties.Name=\"Create Portable Bundle\"");
        axaml.Should().Contain("FontFamily=\"Consolas\"");
        axaml.Should().Contain("FontSize=\"12\"");
    }

    [Fact]
    public async Task UnsavedAndDirtyProjectPreviewUsesFilenameOnlyFallbackAndInMemoryState()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithAssignedPreset();
        project.MarkDirty();
        var viewModel = CreateBundleViewModel(project);
        viewModel.BundleTargetPath = Path.Combine(directory.Path, "dirty.zip");
        viewModel.BundleOutputIntent = OutputIntent.BodyGen;

        await viewModel.PreviewPortableBundleCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        viewModel.BundlePreviewSummary.Should().Contain("unsaved-project.jbs2bg");
        viewModel.BundlePreviewSummary.Should().Contain("current open project state");
        viewModel.BundlePreviewSummary.Should().NotContain(directory.Path);
        viewModel.BundlePreviewEntries.Should().Contain("outputs/bodygen/templates.ini");
    }

    [Fact]
    public async Task GuiBundleCreateUsesBuildProjectSaveContextProfilesForOutputGeneration()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectUsingProfile("Community Body");
        var bundlePath = Path.Combine(directory.Path, "gui-community.zip");
        var communityProfile = CreateProfile("Community Body", ProfileSourceKind.LocalCustom, @"C:\Users\Example\Profiles\Community Body.json", CreateCommunitySliderProfile());
        var catalogService = new FixedCatalogService(CreateBundledOnlyCatalog(), new[] { communityProfile }, Array.Empty<CustomProfileDefinition>());
        var viewModel = CreateBundleViewModel(project, catalogService, CreateBundledOnlyService());
        viewModel.BundleTargetPath = bundlePath;
        viewModel.BundleOutputIntent = OutputIntent.All;
        var expected = WriteExpectedOutputs(directory.Path, "gui-community-expected", project, CreateRequestScopedCatalog(communityProfile));

        await viewModel.CreatePortableBundleCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        viewModel.StatusMessage.Should().Contain("Portable bundle created");
        using var archive = ZipFile.OpenRead(bundlePath);
        ReadEntryBytes(archive, "outputs/bodygen/templates.ini").Should().Equal(File.ReadAllBytes(expected.TemplatesPath));
        ReadEntryBytes(archive, "outputs/bos/Alpha.json").Should().Equal(File.ReadAllBytes(expected.BosJsonPath));
    }

    [Theory]
    [InlineData("project\\project.jbs2bg", "project/project.jbs2bg")]
    [InlineData("outputs/bodygen/templates.ini", "outputs/bodygen/templates.ini")]
    public void BundleEntryPathsNormalizeToForwardSlashRelativeNames(string input, string expected)
    {
        BundlePathScrubber.NormalizeEntryPath(input).Should().Be(expected);
    }

    [Theory]
    [InlineData("C:/Users/Example/project.jbs2bg")]
    [InlineData("/Users/example/project.jbs2bg")]
    [InlineData("project/../secret.txt")]
    [InlineData("project//project.jbs2bg")]
    [InlineData("")]
    public void BundleEntryPathsRejectRootedTraversalAndEmptySegments(string input)
    {
        var act = () => BundlePathScrubber.NormalizeEntryPath(input);

        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void ScrubberRemovesPrivateRootsAndDetectsPathLeaks()
    {
        var privateRoots = new[]
        {
            @"C:\Users\Example",
            @"D:\Modding\Imports",
            @"E:\Exports",
        };
        var text = @"Source C:\Users\Example\project.jbs2bg imported D:\Modding\Imports\body.xml exported E:\Exports\templates.ini";

        var scrubbed = BundlePathScrubber.Scrub(text, privateRoots);

        scrubbed.Should().NotContain(@"C:\Users\Example");
        scrubbed.Should().NotContain(@"D:\Modding\Imports");
        scrubbed.Should().NotContain(@"E:\Exports");
        scrubbed.Should().Contain("[redacted-path]");
        BundlePathScrubber.IsPrivatePathLeak(text).Should().BeTrue();
        BundlePathScrubber.IsPrivatePathLeak("project/project.jbs2bg\noutputs/bodygen/templates.ini").Should().BeFalse();
    }

    [Fact]
    public void RequestModelCarriesSelectedOutputIntentAndOverwriteFlag()
    {
        var project = new ProjectModel();
        var saveContext = new ProjectSaveContext(new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase));
        var request = new PortableProjectBundleRequest(
            project,
            "bundle.zip",
            @"C:\Users\Example\Source\shared-project.jbs2bg",
            OutputIntent.BosJson,
            Overwrite: true,
            FixedCreatedUtc,
            saveContext,
            new[] { @"C:\Users\Example" });

        request.Project.Should().BeSameAs(project);
        request.BundlePath.Should().Be("bundle.zip");
        request.SourceProjectFileName.Should().Be(@"C:\Users\Example\Source\shared-project.jbs2bg");
        request.Intent.Should().Be(OutputIntent.BosJson);
        request.Overwrite.Should().BeTrue();
        request.CreatedUtc.Should().Be(FixedCreatedUtc);
        request.SaveContext.Should().BeSameAs(saveContext);
        request.PrivateRoots.Should().Contain(@"C:\Users\Example");
    }

    [Fact]
    public void ManifestSchemaUsesFilenameOnlySourceAndLowercaseSha256Entries()
    {
        var manifest = new BundleManifest(
            1,
            FixedCreatedUtc,
            Path.GetFileName(@"C:\Users\Example\Source\shared-project.jbs2bg"),
            new[]
            {
                new BundleManifestEntry("project/project.jbs2bg", "project", new string('a', 64)),
                new BundleManifestEntry("manifest.json", "manifest", new string('0', 64)),
            });

        var json = PortableProjectBundleManifestSerializer.Serialize(manifest);
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;

        root.GetProperty("schemaVersion").GetInt32().Should().Be(1);
        root.GetProperty("createdUtc").GetString().Should().Be("2026-04-28T03:00:00.0000000+00:00");
        root.GetProperty("bundleSourceProjectName").GetString().Should().Be("shared-project.jbs2bg");
        root.GetProperty("entries")[0].GetProperty("path").GetString().Should().Be("project/project.jbs2bg");
        root.GetProperty("entries")[0].GetProperty("kind").GetString().Should().Be("project");
        root.GetProperty("entries")[0].GetProperty("sha256").GetString().Should().MatchRegex("^[0-9a-f]{64}$");
        json.Should().NotContain(@"C:\Users\Example");
    }

    [Fact]
    public void PreviewContractsExposeEntriesManifestValidationReportPrivacyFindingsAndOutcome()
    {
        var report = new ProjectValidationReport(new[]
        {
            new DiagnosticFinding(DiagnosticSeverity.Info, "Project", "OK", "No blockers"),
        });
        var preview = new PortableProjectBundlePreview(
            PortableProjectBundleOutcome.Success,
            new[] { new BundleManifestEntry("reports/validation.txt", "report", new string('b', 64)) },
            "{\"schemaVersion\":1}",
            report,
            new[] { "No private path leaks detected." });
        var result = new PortableProjectBundleResult(
            PortableProjectBundleOutcome.Success,
            "bundle.zip",
            new[] { "reports/validation.txt" },
            preview.ManifestJson,
            report,
            Array.Empty<string>());

        preview.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        preview.Entries.Should().ContainSingle(entry => entry.Path == "reports/validation.txt");
        preview.ManifestJson.Should().Contain("schemaVersion");
        preview.ValidationReport.Should().BeSameAs(report);
        preview.PrivacyFindings.Should().ContainSingle();
        result.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        result.BundlePath.Should().Be("bundle.zip");
        File.Exists(result.BundlePath).Should().BeFalse("preview/result contracts do not imply zip creation");
    }

    [Fact]
    public void BundleContractsExposeStableOutcomeValuesForCliAndGuiMapping()
    {
        Enum.GetNames<PortableProjectBundleOutcome>().Should().Equal(
            "Success",
            "ValidationBlocked",
            "OverwriteRefused",
            "MissingProfile",
            "IoFailure");
    }

    [Fact]
    public void CreateWritesStructuredZipWithProjectOutputsProfilesReportsManifestAndChecksums()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithEmbeddedAndLocalProfiles();
        var bundlePath = Path.Combine(directory.Path, "share.zip");
        var request = CreateRequest(project, bundlePath, OutputIntent.All, overwrite: false);

        var result = CreateService().Create(request);

        result.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        using var archive = ZipFile.OpenRead(bundlePath);
        var names = archive.Entries.Select(entry => entry.FullName).OrderBy(value => value, StringComparer.Ordinal).ToArray();
        names.Should().Contain([
            "SHA256SUMS.txt",
            "manifest.json",
            "outputs/bodygen/morphs.ini",
            "outputs/bodygen/templates.ini",
            "outputs/bos/Alpha.json",
            "profiles/Community Body.json",
            "profiles/Embedded Body.json",
            "project/project.jbs2bg",
            "reports/validation.txt",
        ]);
        names.Should().OnlyHaveUniqueItems();
        names.Should().NotContain("profiles/Unrelated Body.json");
        archive.Entries.Should().OnlyContain(entry => entry.LastWriteTime.UtcDateTime == FixedCreatedUtc.UtcDateTime);
        ReadEntryText(archive, "manifest.json").Should().Contain("\"bundleSourceProjectName\": \"source-project.jbs2bg\"");
        ReadEntryText(archive, "manifest.json").Should().NotContain(directory.Path);
        ReadEntryText(archive, "reports/validation.txt").Should().NotContain(directory.Path);
    }

    [Fact]
    public void ExistingBundleTargetIsRefusedUnlessOverwriteIsTrue()
    {
        using var directory = new TemporaryDirectory();
        var bundlePath = Path.Combine(directory.Path, "share.zip");
        File.WriteAllText(bundlePath, "original");

        var refused = CreateService().Create(CreateRequest(CreateProjectWithAssignedPreset(), bundlePath, OutputIntent.BodyGen, overwrite: false));
        var overwritten = CreateService().Create(CreateRequest(CreateProjectWithAssignedPreset(), bundlePath, OutputIntent.BodyGen, overwrite: true));

        refused.Outcome.Should().Be(PortableProjectBundleOutcome.OverwriteRefused);
        overwritten.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        using var archive = ZipFile.OpenRead(bundlePath);
        archive.Entries.Should().Contain(entry => entry.FullName == "project/project.jbs2bg");
    }

    [Fact]
    public void ExistingBundleBytesSurviveInjectedFinalCommitFailure()
    {
        using var directory = new TemporaryDirectory();
        var bundlePath = Path.Combine(directory.Path, "share.zip");
        File.WriteAllText(bundlePath, "original bundle");
        var service = CreateServiceWithCommitter(new ThrowingBundleCommitter());

        var result = service.Create(CreateRequest(CreateProjectWithAssignedPreset(), bundlePath, OutputIntent.BodyGen, overwrite: true));

        result.Outcome.Should().Be(PortableProjectBundleOutcome.IoFailure);
        File.ReadAllText(bundlePath).Should().Be("original bundle");
        Directory.EnumerateFiles(directory.Path, ".share.zip.*.tmp").Should().BeEmpty();
    }

    [Fact]
    public void ValidationBlockerPreventsZipCreationAndReturnsReport()
    {
        using var directory = new TemporaryDirectory();
        var bundlePath = Path.Combine(directory.Path, "blocked.zip");

        var result = CreateService().Create(CreateRequest(new ProjectModel(), bundlePath, OutputIntent.All, overwrite: false));

        result.Outcome.Should().Be(PortableProjectBundleOutcome.ValidationBlocked);
        result.ValidationReport.BlockerCount.Should().BeGreaterThan(0);
        File.Exists(bundlePath).Should().BeFalse();
    }

    [Fact]
    public void MissingReferencedCustomProfileBlocksBundleUnlessEmbeddedProfileExists()
    {
        using var directory = new TemporaryDirectory();
        var missingProject = new ProjectModel();
        missingProject.SliderPresets.Add(new SliderPreset("Alpha", "Missing Body"));
        var embeddedProject = new ProjectModel();
        embeddedProject.SliderPresets.Add(new SliderPreset("Alpha", "Embedded Body"));
        embeddedProject.CustomProfiles.Add(CreateProfile("Embedded Body", ProfileSourceKind.EmbeddedProject, null));

        var missing = CreateService().Create(CreateRequest(missingProject, Path.Combine(directory.Path, "missing.zip"), OutputIntent.BodyGen, overwrite: false));
        var embedded = CreateService().Create(CreateRequest(embeddedProject, Path.Combine(directory.Path, "embedded.zip"), OutputIntent.BodyGen, overwrite: false));

        missing.Outcome.Should().Be(PortableProjectBundleOutcome.MissingProfile);
        File.Exists(Path.Combine(directory.Path, "missing.zip")).Should().BeFalse();
        embedded.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        using var archive = ZipFile.OpenRead(Path.Combine(directory.Path, "embedded.zip"));
        archive.Entries.Should().Contain(entry => entry.FullName == "profiles/Embedded Body.json");
    }

    [Fact]
    public void BundleOutputBytesExactlyMatchExistingWriters()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithAssignedPreset();
        var bundlePath = Path.Combine(directory.Path, "bytes.zip");
        var expectedDirectory = Path.Combine(directory.Path, "expected");
        var expectedBosDirectory = Path.Combine(expectedDirectory, "bos");
        var templatesText = new TemplateGenerationService().GenerateTemplates(project.SliderPresets, CreateCatalog(), false);
        var morphsText = new MorphGenerationService().GenerateMorphs(project).Text;
        new BodyGenIniExportWriter().Write(expectedDirectory, templatesText, morphsText);
        new BosJsonExportWriter(new TemplateGenerationService()).Write(expectedBosDirectory, project.SliderPresets, CreateCatalog());

        CreateService().Create(CreateRequest(project, bundlePath, OutputIntent.All, overwrite: false)).Outcome.Should().Be(PortableProjectBundleOutcome.Success);

        using var archive = ZipFile.OpenRead(bundlePath);
        ReadEntryBytes(archive, "outputs/bodygen/templates.ini").Should().Equal(File.ReadAllBytes(Path.Combine(expectedDirectory, "templates.ini")));
        ReadEntryBytes(archive, "outputs/bodygen/morphs.ini").Should().Equal(File.ReadAllBytes(Path.Combine(expectedDirectory, "morphs.ini")));
        ReadEntryBytes(archive, "outputs/bos/Alpha.json").Should().Equal(File.ReadAllBytes(Path.Combine(expectedBosDirectory, "Alpha.json")));
    }

    [Fact]
    public void BundleOutputBytesUseLocalCustomProfileFromRequestSaveContext()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectUsingProfile("Community Body");
        var bundlePath = Path.Combine(directory.Path, "community.zip");
        var requestScopedCatalog = CreateRequestScopedCatalog(CreateProfile("Community Body", ProfileSourceKind.LocalCustom, @"C:\Users\Example\Profiles\Community Body.json", CreateCommunitySliderProfile()));
        var bundledOnlyCatalog = CreateBundledOnlyCatalog();

        var expected = WriteExpectedOutputs(directory.Path, "community-expected", project, requestScopedCatalog);
        var bundledOnly = new TemplateGenerationService().GenerateTemplates(project.SliderPresets, bundledOnlyCatalog, omitRedundantSliders: false);

        CreateBundledOnlyService().Create(CreateRequest(project, bundlePath, OutputIntent.All, overwrite: false)).Outcome.Should().Be(PortableProjectBundleOutcome.Success);

        using var archive = ZipFile.OpenRead(bundlePath);
        ReadEntryBytes(archive, "outputs/bodygen/templates.ini").Should().Equal(File.ReadAllBytes(expected.TemplatesPath));
        ReadEntryBytes(archive, "outputs/bos/Alpha.json").Should().Equal(File.ReadAllBytes(expected.BosJsonPath));
        ReadEntryText(archive, "outputs/bodygen/templates.ini").Should().NotBe(bundledOnly);
    }

    [Fact]
    public void BundleOutputBytesUseEmbeddedCustomProfileFromProjectModel()
    {
        using var directory = new TemporaryDirectory();
        var embedded = CreateProfile("Embedded Body", ProfileSourceKind.EmbeddedProject, null, CreateEmbeddedSliderProfile());
        var project = CreateProjectUsingProfile("Embedded Body");
        project.CustomProfiles.Add(embedded);
        var bundlePath = Path.Combine(directory.Path, "embedded.zip");
        var requestScopedCatalog = CreateRequestScopedCatalog(embedded);

        var expected = WriteExpectedOutputs(directory.Path, "embedded-expected", project, requestScopedCatalog);

        CreateBundledOnlyService().Create(CreateRequest(project, bundlePath, OutputIntent.All, overwrite: false)).Outcome.Should().Be(PortableProjectBundleOutcome.Success);

        using var archive = ZipFile.OpenRead(bundlePath);
        ReadEntryBytes(archive, "outputs/bodygen/templates.ini").Should().Equal(File.ReadAllBytes(expected.TemplatesPath));
        ReadEntryBytes(archive, "outputs/bos/Alpha.json").Should().Equal(File.ReadAllBytes(expected.BosJsonPath));
    }

    [Fact]
    public void BundleBodyGenReplaysSavedAssignmentStrategyPreservesProjectEntryAndCallerState()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateStaleAssignmentStrategyProject(CreateRaceFilterStrategy());
        var originalAssignments = SnapshotNpcAssignments(project);
        var originalStrategy = project.AssignmentStrategy;
        var originalDirty = project.IsDirty;
        var originalChangeVersion = project.ChangeVersion;
        var bundlePath = Path.Combine(directory.Path, "replayed.zip");

        var result = CreateService().Create(CreateRequest(project, bundlePath, OutputIntent.BodyGen, overwrite: false));

        result.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        GetReplayReportText(result).Should().Contain("Assignment strategy replayed: RaceFilters; assigned NPCs: 3; blocked NPCs: 0.");
        using var archive = ZipFile.OpenRead(bundlePath);
        var morphs = ReadEntryText(archive, "outputs/bodygen/morphs.ini");
        morphs.Should().Contain("Skyrim.esm|1=PresetA");
        morphs.Should().Contain("Fallout4.esm|2=PresetB");
        morphs.Should().Contain("Skyrim.esm|3=PresetC");
        morphs.Should().NotContain("StalePreset");

        var bundledProject = new ProjectFileService().LoadFromString(ReadEntryText(archive, "project/project.jbs2bg"));
        bundledProject.AssignmentStrategy.Should().NotBeNull();
        bundledProject.AssignmentStrategy!.Kind.Should().Be(AssignmentStrategyKind.RaceFilters);
        SnapshotNpcAssignments(bundledProject).Should().Equal(originalAssignments);
        SnapshotNpcAssignments(project).Should().Equal(originalAssignments);
        project.AssignmentStrategy.Should().BeSameAs(originalStrategy);
        project.IsDirty.Should().Be(originalDirty);
        project.ChangeVersion.Should().Be(originalChangeVersion);
    }

    [Fact]
    public void BundleSeededReplayIsRepeatableMatchesCliMorphsAndPreservesNonAssignmentOutputs()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateStaleAssignmentStrategyProject(
            new AssignmentStrategyDefinition(1, AssignmentStrategyKind.SeededRandom, 123, Array.Empty<AssignmentStrategyRule>()),
            includeStalePreset: false);
        var sourceStateProject = CreateStaleAssignmentStrategyProject(null, includeStalePreset: false);
        var firstBundlePath = Path.Combine(directory.Path, "first.zip");
        var secondBundlePath = Path.Combine(directory.Path, "second.zip");
        var cliProjectPath = SaveProject(directory.Path, project);
        var cliOutputDirectory = Path.Combine(directory.Path, "cli");
        var sourceExpected = WriteExpectedOutputs(directory.Path, "source-expected", sourceStateProject, CreateCatalog());

        var first = CreateService().Create(CreateRequest(project, firstBundlePath, OutputIntent.All, overwrite: false));
        var second = CreateService().Create(CreateRequest(project, secondBundlePath, OutputIntent.All, overwrite: false));
        var cli = CreateHeadlessService().Run(new HeadlessGenerationRequest(
            cliProjectPath,
            cliOutputDirectory,
            OutputIntent.BodyGen,
            Overwrite: false,
            OmitRedundantSliders: false));

        first.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        second.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        cli.ExitCode.Should().Be(AutomationExitCode.Success);
        using var firstArchive = ZipFile.OpenRead(firstBundlePath);
        using var secondArchive = ZipFile.OpenRead(secondBundlePath);
        ReadEntryBytes(secondArchive, "outputs/bodygen/morphs.ini")
            .Should().Equal(ReadEntryBytes(firstArchive, "outputs/bodygen/morphs.ini"));
        ReadEntryBytes(firstArchive, "outputs/bodygen/morphs.ini")
            .Should().Equal(File.ReadAllBytes(Path.Combine(cliOutputDirectory, "morphs.ini")));
        ReadEntryBytes(firstArchive, "outputs/bodygen/templates.ini").Should().Equal(File.ReadAllBytes(sourceExpected.TemplatesPath));
        ReadEntryBytes(firstArchive, "outputs/bos/PresetA.json").Should().Equal(File.ReadAllBytes(sourceExpected.BosJsonPath));
    }

    [Fact]
    public void BundleBosJsonIntentAndNoSavedAssignmentStrategyDoNotReplayOrWriteReplayReport()
    {
        using var directory = new TemporaryDirectory();
        var bosProject = CreateStaleAssignmentStrategyProject(CreateRaceFilterStrategy());
        var noStrategyProject = CreateStaleAssignmentStrategyProject(null);
        var bosBundlePath = Path.Combine(directory.Path, "bos.zip");
        var noStrategyBundlePath = Path.Combine(directory.Path, "no-strategy.zip");

        var bos = CreateService().Create(CreateRequest(bosProject, bosBundlePath, OutputIntent.BosJson, overwrite: false));
        var noStrategy = CreateService().Create(CreateRequest(noStrategyProject, noStrategyBundlePath, OutputIntent.BodyGen, overwrite: false));

        bos.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        noStrategy.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        GetReplayReportText(bos).Should().Be("No saved assignment strategy; generated from existing project assignments.");
        GetReplayReportText(noStrategy).Should().Be("No saved assignment strategy; generated from existing project assignments.");
        SnapshotNpcAssignments(bosProject).Should().Contain(row => row.Contains("StalePreset", StringComparison.Ordinal));
        using var bosArchive = ZipFile.OpenRead(bosBundlePath);
        bosArchive.GetEntry("outputs/bodygen/morphs.ini").Should().BeNull();
        bosArchive.GetEntry("reports/replay.txt").Should().BeNull();
        using var noStrategyArchive = ZipFile.OpenRead(noStrategyBundlePath);
        noStrategyArchive.GetEntry("reports/replay.txt").Should().BeNull();
        ReadEntryText(noStrategyArchive, "outputs/bodygen/morphs.ini").Should().Contain("StalePreset");
    }

    [Fact]
    public void BundleBlockedReplayReturnsValidationBlockedReportWithoutZipOrPathLeaks()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateStaleAssignmentStrategyProject(CreateNordOnlyBlockingStrategy());
        var bundlePath = Path.Combine(directory.Path, "blocked.zip");

        var result = CreateService().Create(CreateRequest(project, bundlePath, OutputIntent.All, overwrite: false));

        result.Outcome.Should().Be(PortableProjectBundleOutcome.ValidationBlocked);
        File.Exists(bundlePath).Should().BeFalse();
        result.Entries.Should().BeEmpty();
        result.ManifestJson.Should().BeEmpty();
        var report = GetReplayReportText(result);
        report.Should().Contain("Assignment strategy replay blocked.");
        report.Should().Contain("Codsworth");
        report.Should().Contain("Fallout4.esm");
        report.Should().Contain("CodsworthEditor");
        report.Should().Contain("FormId=2");
        report.Should().Contain("MrHandyRace");
        report.Should().Contain("No eligible preset after strategy rules");
        report.Should().NotContain(directory.Path);
        report.Should().NotContain(bundlePath);
        result.PrivacyFindings.Should().ContainSingle("No private path leaks detected.");
    }

    [Fact]
    public void PreviewReportsReplayOutcomeWithoutWritingZip()
    {
        using var directory = new TemporaryDirectory();
        var successPath = Path.Combine(directory.Path, "preview-success.zip");
        var blockedPath = Path.Combine(directory.Path, "preview-blocked.zip");
        var noStrategyPath = Path.Combine(directory.Path, "preview-no-strategy.zip");

        var success = CreateService().Preview(CreateRequest(CreateStaleAssignmentStrategyProject(CreateRaceFilterStrategy()), successPath, OutputIntent.BodyGen, overwrite: false));
        var blocked = CreateService().Preview(CreateRequest(CreateStaleAssignmentStrategyProject(CreateNordOnlyBlockingStrategy()), blockedPath, OutputIntent.BodyGen, overwrite: false));
        var noStrategy = CreateService().Preview(CreateRequest(CreateStaleAssignmentStrategyProject(null), noStrategyPath, OutputIntent.BodyGen, overwrite: false));

        success.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        GetReplayReportText(success).Should().Contain("Assignment strategy replayed: RaceFilters; assigned NPCs: 3; blocked NPCs: 0.");
        success.Entries.Should().Contain(entry => entry.Path == "reports/replay.txt");
        blocked.Outcome.Should().Be(PortableProjectBundleOutcome.ValidationBlocked);
        GetReplayReportText(blocked).Should().Contain("Codsworth").And.NotContain(blockedPath);
        noStrategy.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        GetReplayReportText(noStrategy).Should().Be("No saved assignment strategy; generated from existing project assignments.");
        noStrategy.Entries.Should().NotContain(entry => entry.Path == "reports/replay.txt");
        File.Exists(successPath).Should().BeFalse();
        File.Exists(blockedPath).Should().BeFalse();
        File.Exists(noStrategyPath).Should().BeFalse();
    }

    [Fact]
    public void SuccessfulReplayAddsReportEntryAndManifestChecksum()
    {
        using var directory = new TemporaryDirectory();
        var bundlePath = Path.Combine(directory.Path, "manifest.zip");
        var result = CreateService().Create(CreateRequest(CreateStaleAssignmentStrategyProject(CreateRaceFilterStrategy()), bundlePath, OutputIntent.BodyGen, overwrite: false));

        result.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        result.Entries.Should().Contain("reports/replay.txt");
        using var archive = ZipFile.OpenRead(bundlePath);
        ReadEntryText(archive, "reports/replay.txt").Should().Be("Assignment strategy replayed: RaceFilters; assigned NPCs: 3; blocked NPCs: 0.");
        var manifest = ReadEntryText(archive, "manifest.json");
        manifest.Should().Contain("\"path\": \"reports/replay.txt\"");
        manifest.Should().Contain("\"kind\": \"report\"");
        using var document = JsonDocument.Parse(manifest);
        var replayEntry = document.RootElement.GetProperty("entries")
            .EnumerateArray()
            .Single(entry => entry.GetProperty("path").GetString() == "reports/replay.txt");
        replayEntry.GetProperty("sha256").GetString().Should().MatchRegex("^[0-9a-f]{64}$");
    }

    [Fact]
    public void BundleProfileEntriesUseReferencedDeduplicatedProfilesAndSkipBundledNameCollisions()
    {
        using var directory = new TemporaryDirectory();
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Beta", "Embedded Body"));
        project.SliderPresets.Add(new SliderPreset("Alpha", "Community Body"));
        project.SliderPresets.Add(new SliderPreset("Gamma", ProjectProfileMapping.SkyrimCbbe));
        project.CustomProfiles.Add(TestProfiles.CreateProfile(ProjectProfileMapping.SkyrimCbbe, ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Embedded Body", ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Unrelated Body", ProfileSourceKind.EmbeddedProject));
        var bundlePath = Path.Combine(directory.Path, "profiles.zip");

        CreateBundledOnlyService().Create(CreateRequest(project, bundlePath, OutputIntent.BodyGen, overwrite: false))
            .Outcome.Should().Be(PortableProjectBundleOutcome.Success);

        using var archive = ZipFile.OpenRead(bundlePath);
        var profileEntries = archive.Entries
            .Select(entry => entry.FullName)
            .Where(name => name.StartsWith("profiles/", StringComparison.Ordinal))
            .ToArray();
        profileEntries.Should().Equal("profiles/Community Body.json", "profiles/Embedded Body.json");
    }

    [Fact]
    public void BundleProfileEntriesDeduplicateSanitizedCustomProfileFileNames()
    {
        using var directory = new TemporaryDirectory();
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Alpha", "Body:A"));
        project.SliderPresets.Add(new SliderPreset("Beta", "Body?A"));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Body:A", ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Body?A", ProfileSourceKind.EmbeddedProject));
        var bundlePath = Path.Combine(directory.Path, "profiles.zip");

        CreateBundledOnlyService().Create(CreateRequest(project, bundlePath, OutputIntent.BodyGen, overwrite: false))
            .Outcome.Should().Be(PortableProjectBundleOutcome.Success);

        using var archive = ZipFile.OpenRead(bundlePath);
        var profileEntries = archive.Entries
            .Select(entry => entry.FullName)
            .Where(name => name.StartsWith("profiles/", StringComparison.Ordinal))
            .ToArray();
        profileEntries.Should().Equal("profiles/Body_A (2).json", "profiles/Body_A.json");
    }

    [Fact]
    public void TempStagingDirectoryIsRemovedOnSuccessAndFailure()
    {
        using var directory = new TemporaryDirectory();
        var tempRoot = Path.Combine(directory.Path, "temp-root");
        Directory.CreateDirectory(tempRoot);
        var successPath = Path.Combine(directory.Path, "success.zip");
        var invalidBundlePath = Path.Combine(directory.Path, "not-a-file");
        Directory.CreateDirectory(invalidBundlePath);
        var service = CreateService(tempRoot);

        service.Create(CreateRequest(CreateProjectWithAssignedPreset(), successPath, OutputIntent.BodyGen, overwrite: false)).Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        service.Create(CreateRequest(CreateProjectWithAssignedPreset(), invalidBundlePath, OutputIntent.BodyGen, overwrite: true)).Outcome.Should().Be(PortableProjectBundleOutcome.IoFailure);

        Directory.EnumerateDirectories(tempRoot).Should().BeEmpty();
    }

    [Fact]
    public void PreviewReturnsManifestReportAndPrivacyFindingsWithoutWritingZip()
    {
        using var directory = new TemporaryDirectory();
        var bundlePath = Path.Combine(directory.Path, "preview.zip");

        var preview = CreateService().Preview(CreateRequest(CreateProjectWithAssignedPreset(), bundlePath, OutputIntent.BodyGen, overwrite: false));

        preview.Outcome.Should().Be(PortableProjectBundleOutcome.Success);
        preview.Entries.Should().Contain(entry => entry.Path == "project/project.jbs2bg");
        preview.ManifestJson.Should().Contain("2026-04-28T03:00:00.0000000+00:00");
        preview.ValidationReport.BlockerCount.Should().Be(0);
        preview.PrivacyFindings.Should().ContainSingle("No private path leaks detected.");
        File.Exists(bundlePath).Should().BeFalse();
    }

    private static PortableProjectBundleService CreateService(string? tempRoot = null) => new(
        new ProjectFileService(),
        new TemplateGenerationService(),
        new MorphGenerationService(),
        new BodyGenIniExportWriter(),
        new BosJsonExportWriter(new TemplateGenerationService()),
        CreateCatalog(),
        new DiagnosticReportTextFormatter(),
        tempRoot);

    private static PortableProjectBundleService CreateBundledOnlyService() => new(
        new ProjectFileService(),
        new TemplateGenerationService(),
        new MorphGenerationService(),
        new BodyGenIniExportWriter(),
        new BosJsonExportWriter(new TemplateGenerationService()),
        CreateBundledOnlyCatalog(),
        new DiagnosticReportTextFormatter());

    private static PortableProjectBundleService CreateServiceWithCommitter(ThrowingBundleCommitter committer) => new(
        new ProjectFileService(),
        new TemplateGenerationService(),
        new MorphGenerationService(),
        new BodyGenIniExportWriter(),
        new BosJsonExportWriter(new TemplateGenerationService()),
        CreateCatalog(),
        new DiagnosticReportTextFormatter(),
        tempRoot: null,
        bundleCommitter: committer.Commit);

    private static HeadlessGenerationService CreateHeadlessService() => new(
        new ProjectFileService(),
        new TemplateGenerationService(),
        new MorphGenerationService(),
        new BodyGenIniExportWriter(),
        new BosJsonExportWriter(new TemplateGenerationService()),
        new BosJsonExportPlanner(),
        new AssignmentStrategyReplayService(new MorphAssignmentService(new RandomAssignmentProvider())),
        CreateCatalog());

    private static MainWindowViewModel CreateBundleViewModel(ProjectModel project)
    {
        var templateGenerationService = new TemplateGenerationService();
        var catalog = CreateCatalog();
        return new MainWindowViewModel(
            project,
            new ProjectFileService(),
            templateGenerationService,
            new MorphGenerationService(),
            catalog,
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(templateGenerationService),
            new FakeBundleFileDialogService(),
            new NullAppDialogService(),
            CreateTemplatesViewModel(project, catalog),
            CreateMorphsViewModel(project),
            portableProjectBundleService: CreateService());
    }

    private static MainWindowViewModel CreateBundleViewModel(
        ProjectModel project,
        ITemplateProfileCatalogService catalogService,
        PortableProjectBundleService portableProjectBundleService)
    {
        var templateGenerationService = new TemplateGenerationService();
        return new MainWindowViewModel(
            project,
            new ProjectFileService(),
            templateGenerationService,
            new MorphGenerationService(),
            catalogService,
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(templateGenerationService),
            new FakeBundleFileDialogService(),
            new NullAppDialogService(),
            CreateTemplatesViewModel(project, catalogService.Current),
            CreateMorphsViewModel(project),
            portableProjectBundleService: portableProjectBundleService);
    }

    private static TemplatesViewModel CreateTemplatesViewModel(ProjectModel project, TemplateProfileCatalog catalog) => new(
        project,
        new BS2BG.Core.Import.BodySlideXmlParser(),
        new TemplateGenerationService(),
        catalog,
        new EmptyBodySlideXmlFilePicker(),
        new EmptyClipboardService());

    private static MorphsViewModel CreateMorphsViewModel(ProjectModel project) => new(
        project,
        new BS2BG.Core.Import.NpcTextParser(),
        new BS2BG.Core.Morphs.MorphAssignmentService(new BS2BG.Core.Morphs.RandomAssignmentProvider()),
        new MorphGenerationService(),
        new EmptyNpcTextFilePicker(),
        new EmptyClipboardService());

    private static PortableProjectBundleRequest CreateRequest(ProjectModel project, string bundlePath, OutputIntent intent, bool overwrite) => new(
        project,
        bundlePath,
        @"C:\Users\Example\Source\source-project.jbs2bg",
        intent,
        overwrite,
        FixedCreatedUtc,
        new ProjectSaveContext(new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase)
        {
            ["Community Body"] = CreateProfile("Community Body", ProfileSourceKind.LocalCustom, @"C:\Users\Example\Profiles\Community Body.json", CreateCommunitySliderProfile()),
            ["Unrelated Body"] = CreateProfile("Unrelated Body", ProfileSourceKind.LocalCustom, @"C:\Users\Example\Profiles\Unrelated Body.json"),
        }),
        new[] { @"C:\Users\Example", Path.GetDirectoryName(bundlePath)! });

    private static string SaveProject(string directory, ProjectModel project)
    {
        var path = Path.Combine(directory, "project-" + Guid.NewGuid().ToString("N") + ".jbs2bg");
        new ProjectFileService().Save(project, path);
        return path;
    }

    private static ProcessResult InvokeProgramMain(params string[] args)
    {
        lock (ConsoleLock)
        {
            var originalOut = Console.Out;
            var originalError = Console.Error;
            using var standardOutput = new StringWriter();
            using var standardError = new StringWriter();
            try
            {
                Console.SetOut(standardOutput);
                Console.SetError(standardError);
                var exitCode = Program.Main(args);
                return new ProcessResult(exitCode, standardOutput.ToString(), standardError.ToString());
            }
            finally
            {
                Console.SetOut(originalOut);
                Console.SetError(originalError);
            }
        }
    }

    private static void CopyProfileAssetsToTestAssemblyDirectory()
    {
        var repoRoot = FindRepoRoot();
        foreach (var fileName in new[] { "settings.json", "settings_UUNP.json", "settings_FO4_CBBE.json" })
        {
            File.Copy(
                Path.Combine(repoRoot, fileName),
                Path.Combine(AppContext.BaseDirectory, fileName),
                overwrite: true);
        }
    }

    private static string FindRepoRoot()
    {
        var directory = AppContext.BaseDirectory;
        while (directory is not null)
        {
            if (File.Exists(Path.Combine(directory, "BS2BG.sln"))) return directory;

            directory = Directory.GetParent(directory)?.FullName;
        }

        throw new DirectoryNotFoundException("Could not find repository root.");
    }

    private static ProjectModel CreateProjectWithAssignedPreset()
    {
        var project = new ProjectModel();
        var preset = new SliderPreset("Alpha");
        preset.AddSetSlider(new ModelSetSlider("Breasts"));
        project.SliderPresets.Add(preset);
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(preset);
        project.CustomMorphTargets.Add(target);
        return project;
    }

    private static ProjectModel CreateProjectUsingProfile(string profileName)
    {
        var project = new ProjectModel();
        var preset = new SliderPreset("Alpha", profileName);
        preset.AddSetSlider(new ModelSetSlider("Breasts") { ValueSmall = 0, ValueBig = 100 });
        project.SliderPresets.Add(preset);
        return project;
    }

    private static ProjectModel CreateStaleAssignmentStrategyProject(
        AssignmentStrategyDefinition? strategy,
        bool includeStalePreset = true)
    {
        var project = new ProjectModel { AssignmentStrategy = strategy };
        var presetA = CreatePreset("PresetA", 10);
        var presetB = CreatePreset("PresetB", 20);
        var presetC = CreatePreset("PresetC", 30);
        var stalePreset = includeStalePreset ? CreatePreset("StalePreset", 40) : presetC;
        project.SliderPresets.Add(presetA);
        project.SliderPresets.Add(presetB);
        project.SliderPresets.Add(presetC);
        if (includeStalePreset) project.SliderPresets.Add(stalePreset);

        AddStaleNpc(project, "Aela", "Skyrim.esm", "AelaEditor", "000001", "NordRace", stalePreset);
        AddStaleNpc(project, "Codsworth", "Fallout4.esm", "CodsworthEditor", "000002", "MrHandyRace", stalePreset);
        AddStaleNpc(project, "Danica", "Skyrim.esm", "DanicaEditor", "000003", "BretonRace", stalePreset);
        return project;
    }

    private static SliderPreset CreatePreset(string name, int breastsValue)
    {
        var preset = new SliderPreset(name);
        preset.AddSetSlider(new ModelSetSlider("Breasts") { ValueSmall = breastsValue, ValueBig = breastsValue + 1 });
        return preset;
    }

    private static void AddStaleNpc(
        ProjectModel project,
        string name,
        string mod,
        string editorId,
        string formId,
        string race,
        SliderPreset stalePreset)
    {
        var npc = new Npc(name)
        {
            Mod = mod,
            EditorId = editorId,
            FormId = formId,
            Race = race,
        };
        npc.AddSliderPreset(stalePreset);
        project.MorphedNpcs.Add(npc);
    }

    private static AssignmentStrategyDefinition CreateRaceFilterStrategy() => new(
        1,
        AssignmentStrategyKind.RaceFilters,
        null,
        new[]
        {
            new AssignmentStrategyRule("Nords", PresetAName, NordRaceName, 1.0, null),
            new AssignmentStrategyRule("Robots", PresetBName, MrHandyRaceName, 1.0, null),
            new AssignmentStrategyRule("Bretons", PresetCName, BretonRaceName, 1.0, null),
        });

    private static AssignmentStrategyDefinition CreateNordOnlyBlockingStrategy() => new(
        1,
        AssignmentStrategyKind.RaceFilters,
        null,
        new[] { new AssignmentStrategyRule("Nords", PresetAName, NordRaceName, 1.0, null) });

    private static string[] SnapshotNpcAssignments(ProjectModel project) => project.MorphedNpcs
        .OrderBy(npc => npc.Name, StringComparer.Ordinal)
        .Select(npc => npc.Name + "=" + string.Join("|", npc.SliderPresets.Select(preset => preset.Name)))
        .ToArray();

    private static ProjectModel CreateProjectWithEmbeddedAndLocalProfiles()
    {
        var project = CreateProjectWithAssignedPreset();
        project.SliderPresets[0].ProfileName = "Community Body";
        project.SliderPresets.Add(new SliderPreset("Beta", "Embedded Body"));
        project.CustomProfiles.Add(CreateProfile("Embedded Body", ProfileSourceKind.EmbeddedProject, null));
        project.CustomProfiles.Add(CreateProfile("Unrelated Body", ProfileSourceKind.EmbeddedProject, null));
        return project;
    }

    private static TemplateProfileCatalog CreateCatalog() => new(new[]
    {
        new ProfileCatalogEntry(ProjectProfileMapping.SkyrimCbbe, new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
        new ProfileCatalogEntry("Community Body", new TemplateProfile("Community Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, @"C:\Users\Example\Profiles\Community Body.json", true),
        new ProfileCatalogEntry("Embedded Body", new TemplateProfile("Embedded Body", CreateSliderProfile()), ProfileSourceKind.EmbeddedProject, null, false),
    });

    private static TemplateProfileCatalog CreateBundledOnlyCatalog() => new(new[]
    {
        new ProfileCatalogEntry(ProjectProfileMapping.SkyrimCbbe, new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
    });

    private static TemplateProfileCatalog CreateRequestScopedCatalog(params CustomProfileDefinition[] profiles) => new(
        CreateBundledOnlyCatalog().Entries.Concat(profiles.Select(profile => new ProfileCatalogEntry(
            profile.Name,
            new TemplateProfile(profile.Name, profile.SliderProfile),
            profile.SourceKind,
            profile.FilePath,
            false))));

    private static CustomProfileDefinition CreateProfile(string name, ProfileSourceKind sourceKind, string? filePath) =>
        new(name, "Skyrim", CreateSliderProfile(), sourceKind, filePath);

    private static CustomProfileDefinition CreateProfile(string name, ProfileSourceKind sourceKind, string? filePath, SliderProfile sliderProfile) =>
        new(name, "Skyrim", sliderProfile, sourceKind, filePath);

    private static SliderProfile CreateSliderProfile() => new(
        new[] { new SliderDefault("Breasts", 0.2f, 1f) },
        Array.Empty<SliderMultiplier>(),
        Array.Empty<string>());

    private static SliderProfile CreateCommunitySliderProfile() => new(
        new[] { new SliderDefault("Breasts", 0.8f, 0.1f) },
        new[] { new SliderMultiplier("Breasts", 1.5f) },
        new[] { "Breasts" });

    private static SliderProfile CreateEmbeddedSliderProfile() => new(
        new[] { new SliderDefault("Breasts", 0.05f, 0.9f) },
        new[] { new SliderMultiplier("Breasts", 0.5f) },
        Array.Empty<string>());

    private static ExpectedOutputPaths WriteExpectedOutputs(string directory, string childName, ProjectModel project, TemplateProfileCatalog catalog)
    {
        var expectedDirectory = Path.Combine(directory, childName);
        var bodyGenDirectory = Path.Combine(expectedDirectory, "bodygen");
        var bosDirectory = Path.Combine(expectedDirectory, "bos");
        var templatesText = new TemplateGenerationService().GenerateTemplates(project.SliderPresets, catalog, false);
        var morphsText = new MorphGenerationService().GenerateMorphs(project).Text;
        new BodyGenIniExportWriter().Write(bodyGenDirectory, templatesText, morphsText);
        new BosJsonExportWriter(new TemplateGenerationService()).Write(bosDirectory, project.SliderPresets, catalog);
        return new ExpectedOutputPaths(
            Path.Combine(bodyGenDirectory, "templates.ini"),
            Directory.GetFiles(bosDirectory, "*.json", SearchOption.TopDirectoryOnly)
                .OrderBy(Path.GetFileName, StringComparer.OrdinalIgnoreCase)
                .First());
    }

    private static string GetReplayReportText(object contract)
    {
        var property = contract.GetType().GetProperty("ReplayReportText");
        property.Should().NotBeNull("portable bundle preview/result contracts must expose replay status explicitly");
        return (string?)property!.GetValue(contract) ?? string.Empty;
    }

    private static string ReadEntryText(ZipArchive archive, string name) =>
        System.Text.Encoding.UTF8.GetString(ReadEntryBytes(archive, name));

    private static byte[] ReadEntryBytes(ZipArchive archive, string name)
    {
        var entry = archive.GetEntry(name) ?? throw new InvalidOperationException("Missing zip entry " + name);
        using var stream = entry.Open();
        using var memory = new MemoryStream();
        stream.CopyTo(memory);
        return memory.ToArray();
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose() => Directory.Delete(Path, true);
    }

    private sealed record ProcessResult(int ExitCode, string StandardOutput, string StandardError);

    private sealed record ExpectedOutputPaths(string TemplatesPath, string BosJsonPath);

    private sealed class ThrowingBundleCommitter
    {
        public void Commit(string tempPath, string finalPath)
        {
            File.Exists(tempPath).Should().BeTrue();
            finalPath.Should().EndWith("share.zip");
            throw new IOException("forced commit failure");
        }
    }

    private sealed class FakeBundleFileDialogService : IFileDialogService
    {
        public Task<string?> PickOpenProjectFileAsync(CancellationToken cancellationToken) => Task.FromResult<string?>(null);

        public Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken) => Task.FromResult<string?>(null);

        public Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken) => Task.FromResult<string?>(null);

        public Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken) => Task.FromResult<string?>(null);

        public Task<string?> PickSaveBundleFileAsync(CancellationToken cancellationToken) => Task.FromResult<string?>(null);
    }

    private sealed class FixedCatalogService : ITemplateProfileCatalogService
    {
        public FixedCatalogService(
            TemplateProfileCatalog current,
            IReadOnlyList<CustomProfileDefinition> localCustomProfiles,
            IReadOnlyList<CustomProfileDefinition> projectProfiles)
        {
            Current = current;
            LocalCustomProfiles = localCustomProfiles;
            ProjectProfiles = projectProfiles;
        }

        public TemplateProfileCatalog Current { get; }

        public IReadOnlyList<ProfileValidationDiagnostic> LastDiscoveryDiagnostics => Array.Empty<ProfileValidationDiagnostic>();

        public IReadOnlyList<CustomProfileDefinition> LocalCustomProfiles { get; }

        public IReadOnlyList<CustomProfileDefinition> ProjectProfiles { get; }

        public IObservable<TemplateProfileCatalog> CatalogChanged => Observable.Empty<TemplateProfileCatalog>();

        public TemplateProfileCatalog Refresh() => Current;

        public TemplateProfileCatalog ClearProjectProfiles() => Current;

        public TemplateProfileCatalog WithProjectProfiles(IEnumerable<CustomProfileDefinition> projectProfiles) => Current;

        public UserProfileSaveResult SaveLocalProfile(CustomProfileDefinition profile) =>
            new(true, null, Array.Empty<ProfileValidationDiagnostic>());
    }

    private sealed class NullAppDialogService : IAppDialogService
    {
        public Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken) => Task.FromResult(true);

        public Task<bool> ConfirmBulkOperationAsync(string title, string message, CancellationToken cancellationToken) => Task.FromResult(true);

        public Task<bool> ConfirmExportOverwriteAsync(ExportPreviewResult preview, CancellationToken cancellationToken) => Task.FromResult(true);

        public Task<ProfileConflictDecision?> PromptProfileConflictAsync(ProfileConflictRequest request, CancellationToken cancellationToken) => Task.FromResult<ProfileConflictDecision?>(null);

        public void ShowAbout()
        {
        }
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
    }

    private sealed class EmptyNpcTextFilePicker : INpcTextFilePicker
    {
        public Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
    }

    private sealed class EmptyClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken) => Task.CompletedTask;
    }
}
