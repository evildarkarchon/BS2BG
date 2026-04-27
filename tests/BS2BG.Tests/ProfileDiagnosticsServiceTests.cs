using BS2BG.Core.Diagnostics;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using ModelSliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

public sealed class ProfileDiagnosticsServiceTests
{
    [Fact]
    public void AnalyzeReportsWholeProjectCoverageUnknownDefaultsMultipliersAndInversions()
    {
        var project = new ProjectModel();
        var alpha = new ModelSliderPreset("Alpha", "Measured");
        alpha.AddSetSlider(new ModelSetSlider("KnownDefault") { ValueSmall = 0, ValueBig = 50 });
        alpha.AddSetSlider(new ModelSetSlider("Amplified") { ValueSmall = 0, ValueBig = 50 });
        alpha.AddSetSlider(new ModelSetSlider("Flipped") { ValueSmall = 0, ValueBig = 50 });
        alpha.AddSetSlider(new ModelSetSlider("CustomOnly") { ValueSmall = 0, ValueBig = 50 });
        project.SliderPresets.Add(alpha);

        var report = new ProfileDiagnosticsService().Analyze(project, CreateCatalog());

        report.Summary.AffectedPresetCount.Should().Be(1);
        report.Summary.KnownSliderCount.Should().Be(3);
        report.Summary.UnknownSliderCount.Should().Be(1);
        report.Summary.InjectedDefaultCount.Should().Be(1);
        report.Summary.MultiplierCount.Should().Be(1);
        report.Summary.InversionCount.Should().Be(1);
        report.SliderDiagnostics.Should().Contain(slider => slider.Name == "CustomOnly" && !slider.IsKnown);
        report.SliderDiagnostics.Should().Contain(slider => slider.Name == "MissingDefault" && slider.IsInjectedDefault);
    }

    [Fact]
    public void AnalyzeReportsUnbundledSavedProfileAsInformationalFallbackWithoutMismatchScoring()
    {
        var project = new ProjectModel();
        var preset = new ModelSliderPreset("Community", "Saved profile");
        preset.AddSetSlider(new ModelSetSlider("KnownDefault") { ValueSmall = 0, ValueBig = 50 });
        project.SliderPresets.Add(preset);

        var report = new ProfileDiagnosticsService().Analyze(project, CreateCatalog());
        var findingText = string.Join(" ", report.Findings.Select(finding =>
            finding.Title + " " + finding.Detail + " " + finding.ActionHint));

        report.Summary.HasNeutralFallback.Should().BeTrue();
        report.Summary.SavedProfileNames.Should().Contain("Saved profile");
        report.Summary.CalculationFallbackProfileName.Should().Be("Measured");
        report.Findings.Should().Contain(finding =>
            finding.Severity == DiagnosticSeverity.Info
            && finding.Title.Contains("Saved profile")
            && finding.Detail.Contains("calculation fallback")
            && finding.Detail.Contains("Measured"));
        findingText.Should().Contain("Saved profile");
        findingText.Should().Contain("calculation fallback");
        findingText.Should().Contain("Info");
        findingText.Should().NotContain("mismatch");
        findingText.Should().NotContain("score");
        findingText.Should().NotContain("heuristic");
        findingText.Should().NotContain("experimental");
    }

    [Fact]
    public void AnalyzeCanFilterToSelectedPresetName()
    {
        var project = new ProjectModel();
        var alpha = new ModelSliderPreset("Alpha", "Measured");
        alpha.AddSetSlider(new ModelSetSlider("KnownDefault") { ValueSmall = 0, ValueBig = 50 });
        var beta = new ModelSliderPreset("Beta", "Measured");
        beta.AddSetSlider(new ModelSetSlider("CustomOnly") { ValueSmall = 0, ValueBig = 50 });
        project.SliderPresets.Add(alpha);
        project.SliderPresets.Add(beta);

        var report = new ProfileDiagnosticsService().Analyze(project, CreateCatalog(), "Beta");

        report.Summary.AffectedPresetCount.Should().Be(1);
        report.SliderDiagnostics.Select(slider => slider.PresetName).Should().OnlyContain(name => name == "Beta");
        report.Summary.UnknownSliderCount.Should().Be(1);
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        var profile = new SliderProfile(
            new[]
            {
                new SliderDefault("KnownDefault", 0f, 1f),
                new SliderDefault("MissingDefault", 0f, 1f)
            },
            new[] { new SliderMultiplier("Amplified", 2f) },
            new[] { "Flipped" });

        return new TemplateProfileCatalog(new[] { new TemplateProfile("Measured", profile) });
    }
}
