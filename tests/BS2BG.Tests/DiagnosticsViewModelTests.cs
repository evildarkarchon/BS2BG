using System.Reactive.Threading.Tasks;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Microsoft.Extensions.DependencyInjection;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using ModelSliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

public sealed class DiagnosticsViewModelTests
{
    [Fact]
    public async Task RefreshDiagnosticsGroupsFindingsCountsSeveritiesAndDoesNotMutateProject()
    {
        var project = new ProjectModel();
        var preset = new ModelSliderPreset("Community", "Saved profile");
        preset.AddSetSlider(new ModelSetSlider("UnknownSlider") { ValueSmall = 0, ValueBig = 50 });
        project.SliderPresets.Add(preset);
        project.MarkClean();
        var changeVersion = project.ChangeVersion;
        var viewModel = CreateViewModel(project);

        await viewModel.RefreshDiagnosticsCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        project.IsDirty.Should().BeFalse();
        project.ChangeVersion.Should().Be(changeVersion);
        viewModel.Areas.Should().ContainInOrder("Project", "Profiles", "Templates", "Morphs/NPCs", "Export");
        viewModel.Findings.Select(finding => finding.SeverityLabel).Should().Contain(new[] { "Blocker", "Info" });
        viewModel.Findings.Should().OnlyContain(finding =>
            finding.SeverityLabel is "Blocker" or "Caution" or "Info");
        viewModel.BlockerCount.Should().BeGreaterThan(0);
        viewModel.InfoCount.Should().BeGreaterThan(0);
        viewModel.CautionCount.Should().Be(0);
        viewModel.SummaryText.Should().Be(viewModel.BlockerCount + " blocker(s) need attention before output is ready.");
    }

    [Fact]
    public async Task RefreshDiagnosticsExposesSelectedFindingDetailsAndNavigationIntentWithoutAutoFix()
    {
        var project = new ProjectModel();
        var preset = new ModelSliderPreset("Community", "Saved profile");
        project.SliderPresets.Add(preset);
        var viewModel = CreateViewModel(project);

        await viewModel.RefreshDiagnosticsCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        var selected = viewModel.Findings.Should().Contain(finding => finding.Area == "Profiles").Subject.First();
        viewModel.SelectedFinding = selected;

        viewModel.SelectedDetailText.Should().Contain(selected.Detail);
        viewModel.SelectedNavigationTarget.Should().Be(selected.TargetKey);
        viewModel.HasNavigationIntent.Should().BeTrue();
        viewModel.GetType().GetProperty("AutoFixCommand").Should().BeNull();
    }

    [Fact]
    public void AppBootstrapperRegistersDiagnosticsServicesAndViewModel()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        provider.GetRequiredService<ProjectValidationService>().Should().NotBeNull();
        provider.GetRequiredService<ProfileDiagnosticsService>().Should().NotBeNull();
        provider.GetRequiredService<DiagnosticsViewModel>().Should().NotBeNull();
    }

    private static DiagnosticsViewModel CreateViewModel(ProjectModel project)
    {
        return new DiagnosticsViewModel(
            project,
            CreateCatalog(),
            new ProjectValidationService(),
            new ProfileDiagnosticsService());
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        var profile = new SliderProfile(
            new[] { new SliderDefault("KnownDefault", 0f, 1f) },
            Array.Empty<SliderMultiplier>(),
            Array.Empty<string>());

        return new TemplateProfileCatalog(new[] { new TemplateProfile("Measured", profile) });
    }
}
