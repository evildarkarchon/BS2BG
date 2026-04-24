using System.Diagnostics.CodeAnalysis;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using ModelSliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments", Justification = "Small expected sequences keep ViewModel assertions readable.")]
public sealed class TemplatesViewModelTests
{
    [Fact]
    public async Task ImportPresetsShowsBusyStateAddsSortedPresetsAndUpdatesDuplicatesInPlace()
    {
        using var directory = new TemporaryDirectory();
        var firstImport = directory.WriteXml(
            "first.xml",
            """
            <SliderPresets>
              <Preset name="beta"><SetSlider name="Scale" size="big" value="25"/></Preset>
              <Preset name="Alpha"><SetSlider name="Scale" size="big" value="50"/></Preset>
            </SliderPresets>
            """);
        var secondImport = directory.WriteXml(
            "second.xml",
            """
            <SliderPresets>
              <Preset name="alpha"><SetSlider name="Scale" size="big" value="75"/></Preset>
            </SliderPresets>
            """);
        var picker = new BlockingFilePicker(new[] { firstImport });
        var viewModel = CreateViewModel(picker);

        var importTask = viewModel.ImportPresetsAsync(TestContext.Current.CancellationToken);
        Assert.True(viewModel.IsBusy);

        picker.Release();
        await importTask;

        Assert.False(viewModel.IsBusy);
        Assert.Equal(new[] { "Alpha", "beta" }, viewModel.Presets.Select(preset => preset.Name));
        var alpha = viewModel.Presets.Single(preset => preset.Name == "Alpha");
        Assert.Same(alpha, viewModel.SelectedPreset);
        Assert.Equal(50, alpha.SetSliders.Single().ValueBig);

        picker.NextFiles = new[] { secondImport };
        var secondImportTask = viewModel.ImportPresetsAsync(TestContext.Current.CancellationToken);
        picker.Release();
        await secondImportTask;

        var updatedAlpha = Assert.Single(
            viewModel.Presets,
            preset => string.Equals(preset.Name, "Alpha", StringComparison.OrdinalIgnoreCase));
        Assert.Same(alpha, updatedAlpha);
        Assert.Equal(75, updatedAlpha.SetSliders.Single().ValueBig);
        Assert.Equal(new[] { "Alpha", "beta" }, viewModel.Presets.Select(preset => preset.Name));
    }

    [Fact]
    public void PresetManagementRejectsDuplicateNamesAndSupportsDuplicateRemoveAndClear()
    {
        var viewModel = CreateViewModel();
        var alpha = AddPreset(viewModel, "Alpha", 25);
        AddPreset(viewModel, "Beta", 50);
        viewModel.SelectedPreset = alpha;

        Assert.False(viewModel.TryRenameSelectedPreset("Beta"));
        Assert.Equal("A preset named 'Beta' already exists.", viewModel.ValidationMessage);
        Assert.Equal(new[] { "Alpha", "Beta" }, viewModel.Presets.Select(preset => preset.Name));

        Assert.True(viewModel.TryRenameSelectedPreset("Gamma"));
        Assert.Equal("Gamma", alpha.Name);

        Assert.False(viewModel.TryDuplicateSelectedPreset("Beta"));
        Assert.Equal("A preset named 'Beta' already exists.", viewModel.ValidationMessage);

        Assert.True(viewModel.TryDuplicateSelectedPreset("Delta"));
        Assert.Equal(new[] { "Beta", "Delta", "Gamma" }, viewModel.Presets.Select(preset => preset.Name));
        Assert.Equal("Delta", viewModel.SelectedPreset?.Name);
        Assert.Equal(25, viewModel.SelectedPreset?.SetSliders.Single().ValueBig);

        Assert.True(viewModel.RemoveSelectedPreset());
        Assert.Equal(new[] { "Beta", "Gamma" }, viewModel.Presets.Select(preset => preset.Name));

        viewModel.ClearPresets();

        Assert.Empty(viewModel.Presets);
        Assert.Null(viewModel.SelectedPreset);
        Assert.Equal(string.Empty, viewModel.PreviewTemplateText);
    }

    [Fact]
    public void PreviewUpdatesForSelectionProfileSliderAndOmitState()
    {
        var viewModel = CreateViewModel();
        var alpha = AddPreset(viewModel, "Alpha", 50);
        AddPreset(viewModel, "Beta", 25);

        viewModel.SelectedPreset = alpha;
        Assert.Equal("Alpha=Scale@0.5", viewModel.PreviewTemplateText);

        viewModel.SelectedProfileName = "Double";
        Assert.Equal("Double", alpha.ProfileName);
        Assert.Equal("Alpha=Scale@1.0", viewModel.PreviewTemplateText);

        alpha.SetSliders.Single().ValueBig = 75;
        Assert.Equal("Alpha=Scale@1.5", viewModel.PreviewTemplateText);

        viewModel.GenerateTemplates();
        Assert.NotEqual(string.Empty, viewModel.GeneratedTemplateText);

        alpha.SetSliders.Single().ValueBig = 0;
        viewModel.OmitRedundantSliders = true;

        Assert.Equal(string.Empty, viewModel.GeneratedTemplateText);
        Assert.Equal("Alpha=", viewModel.PreviewTemplateText);
    }

    [Fact]
    public void ProfileSelectionRebuildsMissingDefaultSlidersForSelectedPreset()
    {
        var viewModel = CreateViewModel(profileCatalog: CreateCatalogWithProfileDefaults());
        var preset = new ModelSliderPreset("Alpha", ProjectProfileMapping.SkyrimCbbe);
        preset.MissingDefaultSetSliders.Add(new ModelSetSlider("RegularOnly"));
        viewModel.Presets.Add(preset);
        viewModel.SelectedPreset = preset;

        viewModel.SelectedProfileName = "Double";

        Assert.Equal("Double", preset.ProfileName);
        Assert.Equal(new[] { "DoubleOnly" }, preset.MissingDefaultSetSliders.Select(slider => slider.Name));

        viewModel.GenerateTemplates();

        Assert.Contains("DoubleOnly@1.0", viewModel.GeneratedTemplateText);
        Assert.DoesNotContain("RegularOnly@", viewModel.GeneratedTemplateText);
    }

    [Fact]
    public async Task CopyGeneratedTemplatesUsesClipboardAndReportsEmptyOutput()
    {
        var clipboard = new CapturingClipboardService();
        var viewModel = CreateViewModel(clipboard: clipboard);

        await viewModel.CopyGeneratedTemplatesAsync(TestContext.Current.CancellationToken);

        Assert.Null(clipboard.Text);
        Assert.Equal("Generate templates before copying.", viewModel.StatusMessage);

        AddPreset(viewModel, "Alpha", 50);
        viewModel.GenerateTemplates();
        await viewModel.CopyGeneratedTemplatesAsync(TestContext.Current.CancellationToken);

        Assert.Equal(viewModel.GeneratedTemplateText, clipboard.Text);
        Assert.Equal("Generated templates copied.", viewModel.StatusMessage);
    }

    [Fact]
    public void CopyGeneratedTemplatesCommandReportsClipboardFailure()
    {
        var clipboard = new ThrowingClipboardService(new InvalidOperationException("Clipboard is unavailable."));
        var viewModel = CreateViewModel(clipboard: clipboard);
        AddPreset(viewModel, "Alpha", 50);
        viewModel.GenerateTemplates();
        var synchronizationContext = new RecordingSynchronizationContext();
        var previousContext = SynchronizationContext.Current;

        try
        {
            SynchronizationContext.SetSynchronizationContext(synchronizationContext);

            viewModel.CopyGeneratedTemplatesCommand.Execute(null);
        }
        finally
        {
            SynchronizationContext.SetSynchronizationContext(previousContext);
        }

        Assert.Empty(synchronizationContext.Exceptions);
        Assert.Equal("Copy generated templates failed: Clipboard is unavailable.", viewModel.StatusMessage);
    }

    private static ModelSliderPreset AddPreset(TemplatesViewModel viewModel, string name, int bigValue)
    {
        var preset = new ModelSliderPreset(name);
        preset.AddSetSlider(new ModelSetSlider("Scale")
        {
            ValueSmall = 0,
            ValueBig = bigValue,
        });
        viewModel.Presets.Add(preset);
        viewModel.SortPresets();
        return preset;
    }

    private static TemplatesViewModel CreateViewModel(
        IBodySlideXmlFilePicker? picker = null,
        IClipboardService? clipboard = null,
        TemplateProfileCatalog? profileCatalog = null)
    {
        return new TemplatesViewModel(
            new ProjectModel(),
            new BodySlideXmlParser(),
            new TemplateGenerationService(),
            profileCatalog ?? CreateCatalog(),
            picker ?? new BlockingFilePicker(Array.Empty<string>()),
            clipboard ?? new CapturingClipboardService());
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        var regular = new BS2BG.Core.Formatting.SliderProfile(
            defaults: Array.Empty<BS2BG.Core.Formatting.SliderDefault>(),
            multipliers: Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
            invertedNames: Array.Empty<string>());
        var doubled = new BS2BG.Core.Formatting.SliderProfile(
            defaults: Array.Empty<BS2BG.Core.Formatting.SliderDefault>(),
            multipliers: new[] { new BS2BG.Core.Formatting.SliderMultiplier("Scale", 2f) },
            invertedNames: Array.Empty<string>());

        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, regular),
            new TemplateProfile("Double", doubled),
        });
    }

    private static TemplateProfileCatalog CreateCatalogWithProfileDefaults()
    {
        var regular = new BS2BG.Core.Formatting.SliderProfile(
            defaults: new[] { new BS2BG.Core.Formatting.SliderDefault("RegularOnly", 0f, 1f) },
            multipliers: Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
            invertedNames: Array.Empty<string>());
        var doubled = new BS2BG.Core.Formatting.SliderProfile(
            defaults: new[] { new BS2BG.Core.Formatting.SliderDefault("DoubleOnly", 0f, 1f) },
            multipliers: Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
            invertedNames: Array.Empty<string>());

        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, regular),
            new TemplateProfile("Double", doubled),
        });
    }

    private sealed class BlockingFilePicker : IBodySlideXmlFilePicker
    {
        private TaskCompletionSource<IReadOnlyList<string>>? completion;

        public BlockingFilePicker(IReadOnlyList<string> nextFiles)
        {
            NextFiles = nextFiles;
        }

        public IReadOnlyList<string> NextFiles { get; set; }

        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken)
        {
            completion = new TaskCompletionSource<IReadOnlyList<string>>(
                TaskCreationOptions.RunContinuationsAsynchronously);
            cancellationToken.Register(() => completion.TrySetCanceled(cancellationToken));
            return completion.Task;
        }

        public void Release()
        {
            completion?.TrySetResult(NextFiles);
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

    private sealed class ThrowingClipboardService : IClipboardService
    {
        private readonly Exception exception;

        public ThrowingClipboardService(Exception exception)
        {
            this.exception = exception;
        }

        public Task SetTextAsync(string text, CancellationToken cancellationToken)
        {
            return Task.FromException(exception);
        }
    }

    private sealed class RecordingSynchronizationContext : SynchronizationContext
    {
        public List<Exception> Exceptions { get; } = new List<Exception>();

        public override void Post(SendOrPostCallback d, object? state)
        {
            try
            {
                d(state);
            }
            catch (Exception ex)
            {
                Exceptions.Add(ex);
            }
        }
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        private readonly string path = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));

        public TemporaryDirectory()
        {
            Directory.CreateDirectory(path);
        }

        public string WriteXml(string fileName, string xml)
        {
            var filePath = Path.Combine(path, fileName);
            File.WriteAllText(filePath, xml);
            return filePath;
        }

        public void Dispose()
        {
            Directory.Delete(path, recursive: true);
        }
    }
}
