using System.Text.Json;
using System.IO.Compression;
using BS2BG.Core.Automation;
using BS2BG.Core.Bundling;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using SliderDefault = BS2BG.Core.Formatting.SliderDefault;
using SliderMultiplier = BS2BG.Core.Formatting.SliderMultiplier;
using SliderProfile = BS2BG.Core.Formatting.SliderProfile;

namespace BS2BG.Tests;

public sealed class PortableBundleServiceTests
{
    private static readonly DateTimeOffset FixedCreatedUtc = new(2026, 4, 28, 3, 0, 0, TimeSpan.Zero);

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

    private static PortableProjectBundleRequest CreateRequest(ProjectModel project, string bundlePath, OutputIntent intent, bool overwrite) => new(
        project,
        bundlePath,
        @"C:\Users\Example\Source\source-project.jbs2bg",
        intent,
        overwrite,
        FixedCreatedUtc,
        new ProjectSaveContext(new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase)
        {
            ["Community Body"] = CreateProfile("Community Body", ProfileSourceKind.LocalCustom, @"C:\Users\Example\Profiles\Community Body.json"),
            ["Unrelated Body"] = CreateProfile("Unrelated Body", ProfileSourceKind.LocalCustom, @"C:\Users\Example\Profiles\Unrelated Body.json"),
        }),
        new[] { @"C:\Users\Example", Path.GetDirectoryName(bundlePath)! });

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

    private static CustomProfileDefinition CreateProfile(string name, ProfileSourceKind sourceKind, string? filePath) =>
        new(name, "Skyrim", CreateSliderProfile(), sourceKind, filePath);

    private static SliderProfile CreateSliderProfile() => new(
        new[] { new SliderDefault("Breasts", 0.2f, 1f) },
        Array.Empty<SliderMultiplier>(),
        Array.Empty<string>());

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
}
