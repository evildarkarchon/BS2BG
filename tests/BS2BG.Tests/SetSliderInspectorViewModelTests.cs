using System.Diagnostics.CodeAnalysis;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments", Justification = "Small expected sequences keep ViewModel assertions readable.")]
public sealed class SetSliderInspectorViewModelTests
{
    [Fact]
    public void SelectedPresetBuildsInspectorRowsFromSetSlidersAndMissingDefaults()
    {
        var viewModel = CreateViewModel(CreateCatalogWithDefault());
        var preset = new SliderPreset("Alpha");
        preset.AddSetSlider(new SetSlider("Scale")
        {
            ValueSmall = 0,
            ValueBig = 100,
        });
        viewModel.Presets.Add(preset);

        viewModel.SelectedPreset = preset;

        Assert.Equal(new[] { "DefaultOnly", "Scale" }, viewModel.SetSliderRows.Select(row => row.Name));
        Assert.Equal("Alpha=DefaultOnly@1.0, Scale@1.0", viewModel.PreviewTemplateText);
    }

    [Fact]
    public void InspectorEditingClampsMinMaxTogglesEnabledAndRefreshesPreview()
    {
        var viewModel = CreateViewModel();
        var preset = AddPreset(viewModel);
        viewModel.SelectedPreset = preset;
        var row = Assert.Single(viewModel.SetSliderRows);

        row.PercentMin = 75;
        row.PercentMax = 50;

        Assert.Equal(75, row.PercentMin);
        Assert.Equal(75, row.PercentMax);
        Assert.Equal("75%", row.PercentMinText);
        Assert.Equal("75%", row.PercentMaxText);
        Assert.Equal("Alpha=Scale@0.75", viewModel.PreviewTemplateText);

        row.Enabled = false;

        Assert.False(preset.SetSliders.Single().Enabled);
        Assert.Equal("Alpha=", viewModel.PreviewTemplateText);
    }

    [Fact]
    public void BatchCommandsUpdateAllRowsAndRefreshPreview()
    {
        var viewModel = CreateViewModel();
        var preset = AddPreset(viewModel);
        preset.AddSetSlider(new SetSlider("Height")
        {
            ValueSmall = 0,
            ValueBig = 100,
            PercentMin = 25,
            PercentMax = 75,
        });
        viewModel.SelectedPreset = preset;

        viewModel.SetAllSliderPercentsTo50Command.Execute(null);
        Assert.All(viewModel.SetSliderRows, row =>
        {
            Assert.Equal(50, row.PercentMin);
            Assert.Equal(50, row.PercentMax);
        });
        Assert.Equal("Alpha=Height@0.5, Scale@0.5", viewModel.PreviewTemplateText);

        viewModel.SetAllMinPercentsTo0Command.Execute(null);
        viewModel.SetAllMaxPercentsTo100Command.Execute(null);

        Assert.All(viewModel.SetSliderRows, row =>
        {
            Assert.Equal(0, row.PercentMin);
            Assert.Equal(100, row.PercentMax);
        });
        Assert.Equal("Alpha=Height@0.0:1.0, Scale@0.0:1.0", viewModel.PreviewTemplateText);
    }

    [Fact]
    public async Task SelectedPresetBosJsonUsesCoreOutputAndCopiesToClipboard()
    {
        var clipboard = new CapturingClipboardService();
        var viewModel = CreateViewModel(clipboard: clipboard);
        var preset = AddPreset(viewModel);

        viewModel.SelectedPreset = preset;
        await viewModel.CopySelectedBosJsonAsync(TestContext.Current.CancellationToken);

        Assert.Equal(
            new TemplateGenerationService().PreviewBosJson(
                preset,
                CreateCatalog().GetProfile(ProjectProfileMapping.SkyrimCbbe)),
            viewModel.SelectedBosJsonText);
        Assert.Equal(viewModel.SelectedBosJsonText, clipboard.Text);
        Assert.Equal("BoS JSON copied.", viewModel.StatusMessage);
    }

    private static SliderPreset AddPreset(TemplatesViewModel viewModel)
    {
        var preset = new SliderPreset("Alpha");
        preset.AddSetSlider(new SetSlider("Scale")
        {
            ValueSmall = 0,
            ValueBig = 100,
        });
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
                new BS2BG.Core.Formatting.SliderProfile(
                    Array.Empty<BS2BG.Core.Formatting.SliderDefault>(),
                    Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
                    Array.Empty<string>())),
        });
    }

    private static TemplateProfileCatalog CreateCatalogWithDefault()
    {
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new BS2BG.Core.Formatting.SliderProfile(
                    new[] { new BS2BG.Core.Formatting.SliderDefault("DefaultOnly", 0f, 1f) },
                    Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
                    Array.Empty<string>())),
        });
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
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
}
