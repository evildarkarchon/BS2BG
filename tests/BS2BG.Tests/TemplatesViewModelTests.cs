using System.Diagnostics.CodeAnalysis;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using ModelSliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected sequences keep ViewModel assertions readable.")]
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
    public void ClearPresetsRemovesAssignmentsFromTargetsAndNpcs()
    {
        var project = new ProjectModel();
        var preset = new ModelSliderPreset("Alpha");
        var target = new CustomMorphTarget("All|Female");
        var npc = new Npc("Lydia") { Mod = "Skyrim.esm", EditorId = "HousecarlWhiterun" };
        project.SliderPresets.Add(preset);
        project.CustomMorphTargets.Add(target);
        project.MorphedNpcs.Add(npc);
        target.AddSliderPreset(preset);
        npc.AddSliderPreset(preset);
        var viewModel = CreateViewModel(project: project);

        viewModel.ClearPresets();

        Assert.Empty(project.SliderPresets);
        Assert.Empty(target.SliderPresets);
        Assert.Empty(npc.SliderPresets);
    }

    [Fact]
    public async Task UndoImportRemapsTargetAndNpcAssignmentsToRestoredPresetInstances()
    {
        using var directory = new TemporaryDirectory();
        var importFile = directory.WriteXml(
            "alpha.xml",
            """
            <SliderPresets>
              <Preset name="Alpha"><SetSlider name="Scale" size="big" value="75"/></Preset>
            </SliderPresets>
            """);
        var undoRedo = new UndoRedoService();
        var project = new ProjectModel();
        var preset = new ModelSliderPreset("Alpha");
        preset.AddSetSlider(new ModelSetSlider("Scale") { ValueBig = 25 });
        var target = new CustomMorphTarget("All|Female");
        var npc = new Npc("Lydia") { Mod = "Skyrim.esm", FormId = "000A2C94" };
        project.SliderPresets.Add(preset);
        project.CustomMorphTargets.Add(target);
        project.MorphedNpcs.Add(npc);
        target.AddSliderPreset(preset);
        npc.AddSliderPreset(preset);
        var viewModel = CreateViewModel(project: project, undoRedo: undoRedo);

        await viewModel.ImportPresetFilesAsync(new[] { importFile }, TestContext.Current.CancellationToken);
        Assert.True(undoRedo.Undo());

        var restoredPreset = Assert.Single(project.SliderPresets);
        Assert.Same(restoredPreset, Assert.Single(target.SliderPresets));
        Assert.Same(restoredPreset, Assert.Single(npc.SliderPresets));

        viewModel.SelectedPreset = restoredPreset;
        Assert.True(viewModel.TryRenameSelectedPreset("Restored"));

        Assert.Equal("All|Female=Restored", target.ToMorphLine());
        Assert.Equal("Skyrim.esm|A2C94=Restored", npc.ToMorphLine());
    }

    [Fact]
    public void PreviewUpdatesForSelectionProfileSliderAndOmitState()
    {
        var viewModel = CreateViewModel();
        var alpha = AddPreset(viewModel, "Alpha", 50);
        AddPreset(viewModel, "Beta", 25);

        viewModel.SelectedPreset = alpha;
        Assert.Equal("Alpha = Scale@0.5", viewModel.PreviewTemplateText);

        viewModel.SelectedProfileName = "Double";
        Assert.Equal("Double", alpha.ProfileName);
        Assert.Equal("Alpha = Scale@1.0", viewModel.PreviewTemplateText);

        alpha.SetSliders.Single().ValueBig = 75;
        Assert.Equal("Alpha = Scale@1.5", viewModel.PreviewTemplateText);

        viewModel.GenerateTemplates();
        Assert.NotEqual(string.Empty, viewModel.GeneratedTemplateText);

        alpha.SetSliders.Single().ValueBig = 0;
        viewModel.OmitRedundantSliders = true;

        Assert.Equal(string.Empty, viewModel.GeneratedTemplateText);
        Assert.Equal("Alpha = ", viewModel.PreviewTemplateText);
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
        preset.AddSetSlider(new ModelSetSlider("Scale") { ValueSmall = 0, ValueBig = bigValue });
        viewModel.Presets.Add(preset);
        viewModel.SortPresets();
        return preset;
    }

    private static TemplatesViewModel CreateViewModel(
        IBodySlideXmlFilePicker? picker = null,
        IClipboardService? clipboard = null,
        TemplateProfileCatalog? profileCatalog = null,
        ProjectModel? project = null,
        UndoRedoService? undoRedo = null)
    {
        return new TemplatesViewModel(
            project ?? new ProjectModel(),
            new BodySlideXmlParser(),
            new TemplateGenerationService(),
            profileCatalog ?? CreateCatalog(),
            picker ?? new BlockingFilePicker(Array.Empty<string>()),
            clipboard ?? new CapturingClipboardService(),
            undoRedo);
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        var regular = new SliderProfile(
            Array.Empty<SliderDefault>(),
            Array.Empty<SliderMultiplier>(),
            Array.Empty<string>());
        var doubled = new SliderProfile(
            Array.Empty<SliderDefault>(),
            new[] { new SliderMultiplier("Scale", 2f) },
            Array.Empty<string>());

        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, regular), new TemplateProfile("Double", doubled)
        });
    }

    private static TemplateProfileCatalog CreateCatalogWithProfileDefaults()
    {
        var regular = new SliderProfile(
            new[] { new SliderDefault("RegularOnly", 0f, 1f) },
            Array.Empty<SliderMultiplier>(),
            Array.Empty<string>());
        var doubled = new SliderProfile(
            new[] { new SliderDefault("DoubleOnly", 0f, 1f) },
            Array.Empty<SliderMultiplier>(),
            Array.Empty<string>());

        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, regular), new TemplateProfile("Double", doubled)
        });
    }

    private sealed class BlockingFilePicker(IReadOnlyList<string> nextFiles) : IBodySlideXmlFilePicker
    {
        private TaskCompletionSource<IReadOnlyList<string>>? completion;

        public IReadOnlyList<string> NextFiles { get; set; } = nextFiles;

        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken)
        {
            completion = new TaskCompletionSource<IReadOnlyList<string>>(
                TaskCreationOptions.RunContinuationsAsynchronously);
            cancellationToken.Register(() => completion.TrySetCanceled(cancellationToken));
            return completion.Task;
        }

        public void Release() => completion?.TrySetResult(NextFiles);
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

    private sealed class ThrowingClipboardService(Exception exception) : IClipboardService
    {
        private readonly Exception exception = exception;

        public Task SetTextAsync(string text, CancellationToken cancellationToken) => Task.FromException(exception);
    }

    private sealed class RecordingSynchronizationContext : SynchronizationContext
    {
        public List<Exception> Exceptions { get; } = new();

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

        public TemporaryDirectory() => Directory.CreateDirectory(path);

        public void Dispose() => Directory.Delete(path, true);

        public string WriteXml(string fileName, string xml)
        {
            var filePath = Path.Combine(path, fileName);
            File.WriteAllText(filePath, xml);
            return filePath;
        }
    }
}
