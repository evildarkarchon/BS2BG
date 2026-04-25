using System.Diagnostics.CodeAnalysis;
using System.Windows.Input;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected sequences keep ViewModel assertions readable.")]
public sealed class MorphsViewModelTests
{
    [Fact]
    public async Task ImportAndAssignmentCommandsPopulateTargetsNpcsAndGeneratedMorphs()
    {
        using var directory = new TemporaryDirectory();
        var npcFile = directory.WriteText(
            "npcs.txt",
            """
            Skyrim.esm|Lydia|HousecarlWhiterun|NordRace|000A2C94
            Dawnguard.esm|Valerica|DLC1Valerica|NordRaceVampire|02002B6C
            """);
        var project = CreateProjectWithPresets();
        var clipboard = new CapturingClipboardService();
        var viewModel = CreateViewModel(
            project,
            new QueueRandomAssignmentProvider(1, 0),
            new StaticNpcTextFilePicker(new[] { npcFile }),
            clipboard);

        await viewModel.ImportNpcsAsync(TestContext.Current.CancellationToken);
        viewModel.TargetNameInput = "All|Female";
        viewModel.AddCustomTarget().Should().BeTrue();
        viewModel.SelectedAvailablePreset = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        viewModel.AddSelectedPresetToTarget().Should().BeTrue();
        viewModel.AssignRandomOnAdd = true;
        viewModel.SelectedImportedNpc = viewModel.NpcDatabase.Single(npc => npc.Name == "Lydia");
        viewModel.AddSelectedNpc().Should().BeTrue();

        viewModel.GenerateMorphs();
        await viewModel.CopyGeneratedMorphsAsync(TestContext.Current.CancellationToken);

        viewModel.NpcDatabase.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        project.CustomMorphTargets.Single().SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha", "Beta");
        viewModel.SelectedTargetName.Should().Be("Lydia");
        viewModel.TargetPresetCountText.Should().Be("1");
        viewModel.NpcCountBadgeText.Should().Be("(1)");
        viewModel.GeneratedMorphsText.Should().Be("All|Female=Alpha|Beta\r\nSkyrim.esm|A2C94=Alpha");
        clipboard.Text.Should().Be(viewModel.GeneratedMorphsText);
    }

    [Fact]
    public async Task ImportNpcFilesSkipsUnreadableFilesAndContinuesImporting()
    {
        using var directory = new TemporaryDirectory();
        var missingFile = Path.Combine(
            Path.GetTempPath(),
            Guid.NewGuid().ToString("N"),
            "missing-npcs.txt");
        var validFile = directory.WriteText(
            "npcs.txt",
            "Skyrim.esm|Lydia|HousecarlWhiterun|NordRace|000A2C94");
        var viewModel = CreateViewModel(CreateProjectWithPresets(), new QueueRandomAssignmentProvider());

        await viewModel.ImportNpcFilesAsync(
            new[] { missingFile, validFile },
            TestContext.Current.CancellationToken);

        var npc = viewModel.NpcDatabase.Should().ContainSingle().Which;
        npc.Name.Should().Be("Lydia");
        viewModel.StatusMessage.Should().Be("Imported 1 NPC. 1 issue was skipped.");
    }

    [Fact]
    public void FilteredBulkOperationsOnlyAffectVisibleNpcs()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var beta = project.SliderPresets.Single(preset => preset.Name == "Beta");
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var serana = CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74");
        serana.AddSliderPreset(beta);
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        valerica.AddSliderPreset(beta);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(serana);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(0));

        viewModel.SearchText = "Skyrim";
        var filled = viewModel.FillEmptyVisibleNpcs(new[] { alpha });
        var cleared = viewModel.ClearVisibleNpcAssignments();

        filled.Should().Be(1);
        cleared.Should().Be(2);
        lydia.SliderPresets.Should().BeEmpty();
        serana.SliderPresets.Should().BeEmpty();
        valerica.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");
        viewModel.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Serana");
    }

    [Fact]
    public void ClearVisibleNpcsClearsSelectedNpcWhenSelectionIsRemoved()
    {
        var project = CreateProjectWithPresets();
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider());

        viewModel.SelectedNpc = lydia;
        viewModel.SearchText = "Skyrim";
        var removed = viewModel.ClearVisibleNpcs();

        removed.Should().Be(1);
        project.MorphedNpcs.Should().NotContain(lydia);
        project.MorphedNpcs.Should().Contain(valerica);
        viewModel.SelectedNpc.Should().BeNull();
        viewModel.SelectedTarget.Should().BeNull();
    }

    [Fact]
    public void ClearCustomTargetsCanUndoAndRedo()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets[0];
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(alpha);
        project.CustomMorphTargets.Add(target);
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo);

        viewModel.SelectedCustomTarget = target;
        viewModel.ClearCustomTargets();

        project.CustomMorphTargets.Should().BeEmpty();

        undoRedo.Undo().Should().BeTrue();
        project.CustomMorphTargets.Should().ContainSingle().Which.Should().BeSameAs(target);
        target.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        viewModel.SelectedCustomTarget.Should().BeSameAs(target);

        undoRedo.Redo().Should().BeTrue();
        project.CustomMorphTargets.Should().BeEmpty();
    }

    [Fact]
    public void ClearVisibleNpcsCanUndoAndRedo()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets[0];
        var beta = project.SliderPresets[1];
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        lydia.AddSliderPreset(alpha);
        valerica.AddSliderPreset(beta);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(valerica);
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo);

        viewModel.SelectedNpc = lydia;
        viewModel.SearchText = "Skyrim";
        viewModel.ClearVisibleNpcs().Should().Be(1);

        project.MorphedNpcs.Should().NotContain(lydia);
        project.MorphedNpcs.Should().Contain(valerica);

        undoRedo.Undo().Should().BeTrue();
        project.MorphedNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        lydia.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        viewModel.SelectedNpc.Should().BeSameAs(lydia);

        undoRedo.Redo().Should().BeTrue();
        project.MorphedNpcs.Should().NotContain(lydia);
        project.MorphedNpcs.Should().Contain(valerica);
    }

    [Fact]
    public void AddAllVisibleImportedNpcsCanUndoAndRedoWithRandomAssignments()
    {
        var project = CreateProjectWithPresets();
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(0, 1), undoRedo: undoRedo);
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        viewModel.NpcDatabase.Add(lydia);
        viewModel.NpcDatabase.Add(valerica);
        viewModel.AssignRandomOnAdd = true;

        viewModel.AddAllVisibleImportedNpcs().Should().Be(2);

        project.MorphedNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        lydia.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        valerica.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");

        undoRedo.Undo().Should().BeTrue();
        project.MorphedNpcs.Should().BeEmpty();

        undoRedo.Redo().Should().BeTrue();
        project.MorphedNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        lydia.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        valerica.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");
    }

    [Fact]
    public void ClearedNpcsDoNotRefreshViewModelWhenRemovedNpcChanges()
    {
        var project = CreateProjectWithPresets();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider());
        var npc = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var npcBadgeNotifications = 0;

        project.MorphedNpcs.Add(npc);
        viewModel.PropertyChanged += (_, args) =>
        {
            if (args.PropertyName == nameof(MorphsViewModel.NpcCountBadgeText)) npcBadgeNotifications++;
        };

        project.MorphedNpcs.Clear();
        npcBadgeNotifications = 0;

        npc.Name = "Detached Lydia";

        npcBadgeNotifications.Should().Be(0);
        viewModel.VisibleNpcs.Should().BeEmpty();
    }

    [Fact]
    public void GenerateMorphsReportsAndNotifiesTargetsWithoutPresets()
    {
        var project = CreateProjectWithPresets();
        var emptyTarget = new CustomMorphTarget("All|Female");
        var emptyNpc = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        project.CustomMorphTargets.Add(emptyTarget);
        project.MorphedNpcs.Add(emptyNpc);
        var notifier = new CapturingNoPresetNotificationService();
        var viewModel = CreateViewModel(
            project,
            new QueueRandomAssignmentProvider(),
            noPresetNotificationService: notifier);

        viewModel.GenerateMorphs();

        viewModel.NoPresetTargets.Should().Equal(emptyTarget, emptyNpc);
        notifier.Targets.Should().Equal(viewModel.NoPresetTargets);
        viewModel.StatusMessage.Should().Be("Generated morphs. 2 targets have no presets.");
    }

    [Fact]
    public void ViewSelectedNpcImageUsesLookupAndImageWindowService()
    {
        var project = CreateProjectWithPresets();
        var npc = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        project.MorphedNpcs.Add(npc);
        var imageLookup = new StubNpcImageLookupService("images/Lydia (HousecarlWhiterun).png");
        var imageView = new CapturingImageViewService();
        var viewModel = CreateViewModel(
            project,
            new QueueRandomAssignmentProvider(),
            imageLookupService: imageLookup,
            imageViewService: imageView);

        viewModel.SelectedNpc = npc;
        ((ICommand)viewModel.ViewSelectedNpcImageCommand).Execute(null);

        imageLookup.Npc.Should().Be(npc);
        imageView.Npc.Should().Be(npc);
        imageView.ImagePath.Should().Be("images/Lydia (HousecarlWhiterun).png");
        viewModel.StatusMessage.Should().Be("Opened image for Lydia.");
    }

    private static MorphsViewModel CreateViewModel(
        ProjectModel project,
        IRandomAssignmentProvider randomProvider,
        INpcTextFilePicker? filePicker = null,
        IClipboardService? clipboard = null,
        INpcImageLookupService? imageLookupService = null,
        IImageViewService? imageViewService = null,
        INoPresetNotificationService? noPresetNotificationService = null,
        UndoRedoService? undoRedo = null)
    {
        return new MorphsViewModel(
            project,
            new NpcTextParser(),
            new MorphAssignmentService(randomProvider),
            new MorphGenerationService(),
            filePicker ?? new StaticNpcTextFilePicker(Array.Empty<string>()),
            clipboard ?? new CapturingClipboardService(),
            imageLookupService ?? new StubNpcImageLookupService(null),
            imageViewService ?? new CapturingImageViewService(),
            noPresetNotificationService ?? new CapturingNoPresetNotificationService(),
            undoRedo);
    }

    private static ProjectModel CreateProjectWithPresets()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Alpha"));
        project.SliderPresets.Add(new SliderPreset("Beta"));
        return project;
    }

    private static Npc CreateNpc(string mod, string name, string editorId, string race, string formId) =>
        new(name) { Mod = mod, EditorId = editorId, Race = race, FormId = formId };

    private sealed class QueueRandomAssignmentProvider(params int[] values) : IRandomAssignmentProvider
    {
        private readonly Queue<int> values = new(values);

        public int NextIndex(int exclusiveMax)
        {
            if (values.Count == 0) return 0;

            return values.Dequeue();
        }
    }

    private sealed class StaticNpcTextFilePicker(IReadOnlyList<string> files) : INpcTextFilePicker
    {
        private readonly IReadOnlyList<string> files = files;

        public Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult(files);
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

    private sealed class StubNpcImageLookupService(string? imagePath) : INpcImageLookupService
    {
        private readonly string? imagePath = imagePath;

        public Npc? Npc { get; private set; }

        public string? FindImagePath(Npc npc)
        {
            Npc = npc;
            return imagePath;
        }
    }

    private sealed class CapturingImageViewService : IImageViewService
    {
        public Npc? Npc { get; private set; }

        public string? ImagePath { get; private set; }

        public void ShowImage(Npc npc, string? imagePath)
        {
            Npc = npc;
            ImagePath = imagePath;
        }
    }

    private sealed class CapturingNoPresetNotificationService : INoPresetNotificationService
    {
        public IReadOnlyList<MorphTargetBase> Targets { get; private set; } = Array.Empty<MorphTargetBase>();

        public void ShowTargetsWithoutPresets(IReadOnlyList<MorphTargetBase> targets) => Targets = targets.ToArray();
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        private readonly string path = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));

        public TemporaryDirectory() => Directory.CreateDirectory(path);

        public void Dispose() => Directory.Delete(path, true);

        public string WriteText(string fileName, string text)
        {
            var filePath = Path.Combine(path, fileName);
            File.WriteAllText(filePath, text);
            return filePath;
        }
    }
}
