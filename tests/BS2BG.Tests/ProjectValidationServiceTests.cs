using BS2BG.Core.Diagnostics;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;
using ModelSliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

public sealed class ProjectValidationServiceTests
{
    [Fact]
    public void ValidateReportsEmptyProjectReadinessWithoutMutatingProject()
    {
        var project = new ProjectModel();
        var catalog = CreateCatalog();
        var initialVersion = project.ChangeVersion;
        var service = new ProjectValidationService();

        var report = service.Validate(project, catalog);

        project.IsDirty.Should().BeFalse("D-04 and DIAG-01 require validation to be read-only");
        project.ChangeVersion.Should().Be(initialVersion);
        report.BlockerCount.Should().Be(1);
        report.InfoCount.Should().BeGreaterThan(0);
        report.Findings.Select(finding => finding.Area)
            .Should().Contain(new[] { "Project", "Templates", "Morphs/NPCs", "Export" });
        report.Findings.Should().Contain(finding =>
            finding.Severity == DiagnosticSeverity.Blocker
            && finding.Area == "Templates"
            && finding.Title.Contains("No presets"));
    }

    [Fact]
    public void ValidateUsesRequiredSeverityValuesAndAreaNames()
    {
        var severities = Enum.GetNames<DiagnosticSeverity>();

        severities.Should().Equal("Blocker", "Caution", "Info");

        var report = new ProjectValidationService().Validate(new ProjectModel(), CreateCatalog());

        var allowedAreas = new[] { "Project", "Profiles", "Templates", "Morphs/NPCs", "Import", "Export" };
        report.Findings.Select(finding => finding.Area).Distinct()
            .Should().OnlyContain(area => allowedAreas.Contains(area));
    }

    [Fact]
    public void ValidateReportsMissingPresetReferencesAndUnbundledProfilesAsNeutralInformation()
    {
        var project = new ProjectModel();
        var alpha = new ModelSliderPreset("Alpha", "Unbundled Body");
        var missingReference = new ModelSliderPreset("Missing");
        project.SliderPresets.Add(alpha);
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(missingReference);
        project.CustomMorphTargets.Add(target);
        project.MarkClean();
        var service = new ProjectValidationService();

        var report = service.Validate(project, CreateCatalog());

        project.IsDirty.Should().BeFalse();
        report.Findings.Should().Contain(finding =>
            finding.Severity == DiagnosticSeverity.Blocker
            && finding.Area == "Morphs/NPCs"
            && finding.TargetKey == "All|Female");
        report.Findings.Should().NotContain(finding => finding.Title == "Unbundled saved profile");
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        var profile = new SliderProfile(
            new[] { new SliderDefault("Known", 0f, 1f) },
            Array.Empty<SliderMultiplier>(),
            Array.Empty<string>());

        return new TemplateProfileCatalog(new[] { new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, profile) });
    }
}
