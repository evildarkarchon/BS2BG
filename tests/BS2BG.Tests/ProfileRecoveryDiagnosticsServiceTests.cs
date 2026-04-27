using BS2BG.Core.Diagnostics;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;
using ModelSliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

public sealed class ProfileRecoveryDiagnosticsServiceTests
{
    [Fact]
    public void AnalyzeReportsMissingCustomProfileAsNeutralRecoveryDiagnostic()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new ModelSliderPreset("CommunityPreset", "Community CBBE"));
        project.MarkClean();

        var diagnostics = new ProfileRecoveryDiagnosticsService().Analyze(project, CreateCatalog()).ToArray();

        project.IsDirty.Should().BeFalse("recovery diagnostics are read-only and must not block generation");
        diagnostics.Should().ContainSingle();
        var diagnostic = diagnostics.Single();
        diagnostic.Code.Should().Be("MissingCustomProfile");
        diagnostic.Category.Should().Be("ProfileRecovery");
        diagnostic.Severity.Should().Be(DiagnosticSeverity.Info);
        diagnostic.MissingProfileName.Should().Be("Community CBBE");
        diagnostic.AffectedPresetNames.Should().Equal("CommunityPreset");
        diagnostic.FallbackCalculationProfileName.Should().Be(ProjectProfileMapping.SkyrimCbbe);
        diagnostic.Detail.Should().Contain("Project references custom profile");
        diagnostic.Detail.Should().Contain("visible fallback calculation");
        diagnostic.Detail.Should().Contain("BS2BG can continue with visible fallback calculation until you resolve it");
        diagnostic.Actions.Should().Equal(
            ProfileRecoveryActionKind.ImportMatchingProfile,
            ProfileRecoveryActionKind.RemapToInstalledProfile,
            ProfileRecoveryActionKind.KeepUnresolvedForNow);
    }

    [Fact]
    public void AnalyzeAddsEmbeddedCopyActionWhenProjectContainsExactMissingProfileDefinition()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new ModelSliderPreset("CommunityPreset", "Community CBBE"));
        project.CustomProfiles.Add(new CustomProfileDefinition(
            "Community CBBE",
            "Skyrim",
            EmptyProfile(),
            ProfileSourceKind.EmbeddedProject,
            filePath: null));
        project.MarkClean();

        var diagnostic = new ProfileRecoveryDiagnosticsService().Analyze(project, CreateCatalog()).Single();

        project.IsDirty.Should().BeFalse();
        diagnostic.Detail.Should().Contain("project-embedded copy is available");
        diagnostic.Actions.Should().Equal(
            ProfileRecoveryActionKind.ImportMatchingProfile,
            ProfileRecoveryActionKind.UseProjectEmbeddedCopy,
            ProfileRecoveryActionKind.RemapToInstalledProfile,
            ProfileRecoveryActionKind.KeepUnresolvedForNow);
    }

    [Fact]
    public void AnalyzeSkipsResolvedBundledLocalCustomAndLegacyProfileNames()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new ModelSliderPreset("Bundled", ProjectProfileMapping.SkyrimCbbe));
        project.SliderPresets.Add(new ModelSliderPreset("BundledDifferentCase", ProjectProfileMapping.SkyrimUunp.ToUpperInvariant()));
        project.SliderPresets.Add(new ModelSliderPreset("Local", "Local Community"));

        var localCatalog = new TemplateProfileCatalog(CreateCatalog().Entries.Concat(new[]
        {
            new ProfileCatalogEntry(
                "Local Community",
                new TemplateProfile("Local Community", EmptyProfile()),
                ProfileSourceKind.LocalCustom,
                "local-community.json",
                true)
        }));

        var diagnostics = new ProfileRecoveryDiagnosticsService().Analyze(project, localCatalog);

        diagnostics.Should().BeEmpty();
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, EmptyProfile()),
            new TemplateProfile(ProjectProfileMapping.SkyrimUunp, EmptyProfile())
        });
    }

    private static SliderProfile EmptyProfile() =>
        new(Array.Empty<SliderDefault>(), Array.Empty<SliderMultiplier>(), Array.Empty<string>());
}
