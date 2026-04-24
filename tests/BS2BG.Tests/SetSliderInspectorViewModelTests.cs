using System.Diagnostics.CodeAnalysis;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using Xunit;
using SetSlider = BS2BG.Core.Models.SetSlider;
using SliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected sequences keep ViewModel assertions readable.")]
public sealed class SetSliderInspectorViewModelTests
{
    [Fact]
    public void SelectedPresetBuildsInspectorRowsFromSetSlidersAndMissingDefaults()
    {
        var viewModel = CreateViewModel(CreateCatalogWithDefault());
        var preset = new SliderPreset("Alpha");
        preset.AddSetSlider(new SetSlider("Scale") { ValueSmall = 0, ValueBig = 100 });
        viewModel.Presets.Add(preset);

        viewModel.SelectedPreset = preset;

        viewModel.SetSliderRows.Select(row => row.Name).Should().Equal(new[] { "DefaultOnly", "Scale" });
        viewModel.PreviewTemplateText.Should().Be("Alpha = DefaultOnly@1.0, Scale@1.0");
    }

    [Fact]
    public void InspectorEditingClampsMinMaxTogglesEnabledAndRefreshesPreview()
    {
        var viewModel = CreateViewModel();
        var preset = AddPreset(viewModel);
        viewModel.SelectedPreset = preset;
        var row = viewModel.SetSliderRows.Should().ContainSingle().Which;

        row.PercentMin = 75;
        row.PercentMax = 50;

        row.PercentMin.Should().Be(75);
        row.PercentMax.Should().Be(75);
        row.PercentMinText.Should().Be("75%");
        row.PercentMaxText.Should().Be("75%");
        viewModel.PreviewTemplateText.Should().Be("Alpha = Scale@0.75");

        row.Enabled = false;

        preset.SetSliders.Single().Enabled.Should().BeFalse();
        viewModel.PreviewTemplateText.Should().Be("Alpha = ");
    }

    [Fact]
    public void BatchCommandsUpdateAllRowsAndRefreshPreview()
    {
        var viewModel = CreateViewModel();
        var preset = AddPreset(viewModel);
        preset.AddSetSlider(
            new SetSlider("Height") { ValueSmall = 0, ValueBig = 100, PercentMin = 25, PercentMax = 75 });
        viewModel.SelectedPreset = preset;

        viewModel.SetAllSliderPercentsTo50Command.Execute(null);
        viewModel.SetSliderRows.Should().AllSatisfy(row =>
        {
            row.PercentMin.Should().Be(50);
            row.PercentMax.Should().Be(50);
        });
        viewModel.PreviewTemplateText.Should().Be("Alpha = Height@0.5, Scale@0.5");

        viewModel.SetAllMinPercentsTo0Command.Execute(null);
        viewModel.SetAllMaxPercentsTo100Command.Execute(null);

        viewModel.SetSliderRows.Should().AllSatisfy(row =>
        {
            row.PercentMin.Should().Be(0);
            row.PercentMax.Should().Be(100);
        });
        viewModel.PreviewTemplateText.Should().Be("Alpha = Height@0.0:1.0, Scale@0.0:1.0");
    }

    [Fact]
    public async Task SelectedPresetBosJsonUsesCoreOutputAndCopiesToClipboard()
    {
        var clipboard = new CapturingClipboardService();
        var viewModel = CreateViewModel(clipboard: clipboard);
        var preset = AddPreset(viewModel);

        viewModel.SelectedPreset = preset;
        await viewModel.CopySelectedBosJsonAsync(TestContext.Current.CancellationToken);

        viewModel.SelectedBosJsonText.Should().Be(new TemplateGenerationService().PreviewBosJson(
                preset,
                CreateCatalog().GetProfile(ProjectProfileMapping.SkyrimCbbe)));
        clipboard.Text.Should().Be(viewModel.SelectedBosJsonText);
        viewModel.StatusMessage.Should().Be("BoS JSON copied.");
    }

    private static SliderPreset AddPreset(TemplatesViewModel viewModel)
    {
        var preset = new SliderPreset("Alpha");
        preset.AddSetSlider(new SetSlider("Scale") { ValueSmall = 0, ValueBig = 100 });
        viewModel.Presets.Add(preset);
        return preset;
    }

    private static TemplatesViewModel CreateViewModel(
        TemplateProfileCatalog? profileCatalog = null,
        IClipboardService? clipboard = null)
    {
        return new TemplatesViewModel(
            new ProjectModel(),
            new BodySlideXmlParser(),
            new TemplateGenerationService(),
            profileCatalog ?? CreateCatalog(),
            new EmptyBodySlideXmlFilePicker(),
            clipboard ?? new CapturingClipboardService());
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new SliderProfile(
                    Array.Empty<SliderDefault>(),
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
        });
    }

    private static TemplateProfileCatalog CreateCatalogWithDefault()
    {
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new SliderProfile(
                    new[] { new SliderDefault("DefaultOnly", 0f, 1f) },
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
        });
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
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
}
