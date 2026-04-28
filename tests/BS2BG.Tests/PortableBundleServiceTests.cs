using System.Text.Json;
using BS2BG.Core.Automation;
using BS2BG.Core.Bundling;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;
using Xunit;

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
}
