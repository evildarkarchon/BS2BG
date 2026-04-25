using System.Diagnostics.CodeAnalysis;
using System.Reactive.Threading.Tasks;
using System.Windows.Input;
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

        var importTask = viewModel.ImportPresetsCommand.Execute().ToTask();
        viewModel.IsBusy.Should().BeTrue();

        picker.Release();
        await importTask;

        viewModel.IsBusy.Should().BeFalse();
        viewModel.Presets.Select(preset => preset.Name).Should().Equal("Alpha", "beta");
        var alpha = viewModel.Presets.Single(preset => preset.Name == "Alpha");
        viewModel.SelectedPreset.Should().BeSameAs(alpha);
        alpha.SetSliders.Single().ValueBig.Should().Be(50);

        picker.NextFiles = new[] { secondImport };
        var secondImportTask = viewModel.ImportPresetsCommand.Execute().ToTask();
        picker.Release();
        await secondImportTask;

        var updatedAlpha = viewModel.Presets.Should().ContainSingle(preset =>
            string.Equals(preset.Name, "Alpha", StringComparison.OrdinalIgnoreCase)).Which;
        updatedAlpha.Should().BeSameAs(alpha);
        updatedAlpha.SetSliders.Single().ValueBig.Should().Be(75);
        viewModel.Presets.Select(preset => preset.Name).Should().Equal("Alpha", "beta");
    }

    [Fact]
    public void PresetManagementRejectsDuplicateNamesAndSupportsDuplicateRemoveAndClear()
    {
        var viewModel = CreateViewModel();
        var alpha = AddPreset(viewModel, "Alpha", 25);
        AddPreset(viewModel, "Beta", 50);
        viewModel.SelectedPreset = alpha;

        viewModel.TryRenameSelectedPreset("Beta").Should().BeFalse();
        viewModel.ValidationMessage.Should().Be("A preset named 'Beta' already exists.");
        viewModel.Presets.Select(preset => preset.Name).Should().Equal("Alpha", "Beta");

        viewModel.TryRenameSelectedPreset("Gamma").Should().BeTrue();
        alpha.Name.Should().Be("Gamma");

        viewModel.TryDuplicateSelectedPreset("Beta").Should().BeFalse();
        viewModel.ValidationMessage.Should().Be("A preset named 'Beta' already exists.");

        viewModel.TryDuplicateSelectedPreset("Delta").Should().BeTrue();
        viewModel.Presets.Select(preset => preset.Name).Should().Equal("Beta", "Delta", "Gamma");
        viewModel.SelectedPreset?.Name.Should().Be("Delta");
        viewModel.SelectedPreset?.SetSliders.Single().ValueBig.Should().Be(25);

        viewModel.RemoveSelectedPreset().Should().BeTrue();
        viewModel.Presets.Select(preset => preset.Name).Should().Equal("Beta", "Gamma");

        viewModel.ClearPresets();

        viewModel.Presets.Should().BeEmpty();
        viewModel.SelectedPreset.Should().BeNull();
        viewModel.PreviewTemplateText.Should().Be(string.Empty);
    }

    [Fact]
    public void TryRenameSelectedPresetRejectsForbiddenCharacterWithFriendlyMessage()
    {
        var viewModel = CreateViewModel();
        var alpha = AddPreset(viewModel, "Alpha", 25);
        viewModel.SelectedPreset = alpha;

        viewModel.TryRenameSelectedPreset("foo|bar").Should().BeFalse();

        viewModel.ValidationMessage.Should().Contain("'|'");
        alpha.Name.Should().Be("Alpha");
    }

    [Fact]
    public void TryDuplicateSelectedPresetRejectsForbiddenCharacterWithFriendlyMessage()
    {
        var viewModel = CreateViewModel();
        var alpha = AddPreset(viewModel, "Alpha", 25);
        viewModel.SelectedPreset = alpha;

        viewModel.TryDuplicateSelectedPreset("foo=bar").Should().BeFalse();

        viewModel.ValidationMessage.Should().Contain("'='");
        viewModel.Presets.Select(preset => preset.Name).Should().Equal("Alpha");
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

        project.SliderPresets.Should().BeEmpty();
        target.SliderPresets.Should().BeEmpty();
        npc.SliderPresets.Should().BeEmpty();
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
        undoRedo.Undo().Should().BeTrue();

        var restoredPreset = project.SliderPresets.Should().ContainSingle().Which;
        target.SliderPresets.Should().ContainSingle().Which.Should().BeSameAs(restoredPreset);
        npc.SliderPresets.Should().ContainSingle().Which.Should().BeSameAs(restoredPreset);

        viewModel.SelectedPreset = restoredPreset;
        viewModel.TryRenameSelectedPreset("Restored").Should().BeTrue();

        target.ToMorphLine().Should().Be("All|Female=Restored");
        npc.ToMorphLine().Should().Be("Skyrim.esm|A2C94=Restored");
    }

    [Fact]
    public void PreviewUpdatesForSelectionProfileSliderAndOmitState()
    {
        var viewModel = CreateViewModel();
        var alpha = AddPreset(viewModel, "Alpha", 50);
        AddPreset(viewModel, "Beta", 25);

        viewModel.SelectedPreset = alpha;
        viewModel.PreviewTemplateText.Should().Be("Alpha = Scale@0.5");

        viewModel.SelectedProfileName = "Double";
        alpha.ProfileName.Should().Be("Double");
        viewModel.PreviewTemplateText.Should().Be("Alpha = Scale@1.0");

        alpha.SetSliders.Single().ValueBig = 75;
        viewModel.PreviewTemplateText.Should().Be("Alpha = Scale@1.5");

        viewModel.GenerateTemplates();
        viewModel.GeneratedTemplateText.Should().NotBe(string.Empty);

        alpha.SetSliders.Single().ValueBig = 0;
        viewModel.OmitRedundantSliders = true;

        viewModel.GeneratedTemplateText.Should().Be(string.Empty);
        viewModel.PreviewTemplateText.Should().Be("Alpha = ");
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

        preset.ProfileName.Should().Be("Double");
        preset.MissingDefaultSetSliders.Select(slider => slider.Name).Should().Equal("DoubleOnly");

        viewModel.GenerateTemplates();

        viewModel.GeneratedTemplateText.Should().Contain("DoubleOnly@1.0");
        viewModel.GeneratedTemplateText.Should().NotContain("RegularOnly@");
    }

    [Fact]
    public async Task CopyGeneratedTemplatesUsesClipboardAndReportsEmptyOutput()
    {
        var clipboard = new CapturingClipboardService();
        var viewModel = CreateViewModel(clipboard: clipboard);

        await viewModel.CopyGeneratedTemplatesAsync(TestContext.Current.CancellationToken);

        clipboard.Text.Should().BeNull();
        viewModel.StatusMessage.Should().Be("Generate templates before copying.");

        AddPreset(viewModel, "Alpha", 50);
        viewModel.GenerateTemplates();
        await viewModel.CopyGeneratedTemplatesAsync(TestContext.Current.CancellationToken);

        clipboard.Text.Should().Be(viewModel.GeneratedTemplateText);
        viewModel.StatusMessage.Should().Be("Generated templates copied.");
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

            ((ICommand)viewModel.CopyGeneratedTemplatesCommand).Execute(null);
        }
        finally
        {
            SynchronizationContext.SetSynchronizationContext(previousContext);
        }

        synchronizationContext.Exceptions.Should().BeEmpty();
        viewModel.StatusMessage.Should().Be("Copy generated templates failed: Clipboard is unavailable.");
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
