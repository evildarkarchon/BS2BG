using System.Diagnostics.CodeAnalysis;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments", Justification = "Small expected sequences keep ViewModel assertions readable.")]
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
        Assert.True(viewModel.AddCustomTarget());
        viewModel.SelectedAvailablePreset = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        Assert.True(viewModel.AddSelectedPresetToTarget());
        viewModel.AssignRandomOnAdd = true;
        viewModel.SelectedImportedNpc = viewModel.NpcDatabase.Single(npc => npc.Name == "Lydia");
        Assert.True(viewModel.AddSelectedNpc());

        viewModel.GenerateMorphs();
        await viewModel.CopyGeneratedMorphsAsync(TestContext.Current.CancellationToken);

        Assert.Equal(new[] { "Lydia", "Valerica" }, viewModel.NpcDatabase.Select(npc => npc.Name));
        Assert.Equal(new[] { "Alpha", "Beta" }, project.CustomMorphTargets.Single().SliderPresets.Select(preset => preset.Name));
        Assert.Equal("Lydia", viewModel.SelectedTargetName);
        Assert.Equal("1", viewModel.TargetPresetCountText);
        Assert.Equal("(1)", viewModel.NpcCountBadgeText);
        Assert.Equal(
            "All|Female=Alpha|Beta\r\nSkyrim.esm|A2C94=Alpha",
            viewModel.GeneratedMorphsText);
        Assert.Equal(viewModel.GeneratedMorphsText, clipboard.Text);
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

        Assert.Equal(1, filled);
        Assert.Equal(2, cleared);
        Assert.Empty(lydia.SliderPresets);
        Assert.Empty(serana.SliderPresets);
        Assert.Equal(new[] { "Beta" }, valerica.SliderPresets.Select(preset => preset.Name));
        Assert.Equal(new[] { "Lydia", "Serana" }, viewModel.VisibleNpcs.Select(npc => npc.Name));
    }

    private static MorphsViewModel CreateViewModel(
        ProjectModel project,
        IRandomAssignmentProvider randomProvider,
        INpcTextFilePicker? filePicker = null,
        IClipboardService? clipboard = null)
    {
        return new MorphsViewModel(
            project,
            new NpcTextParser(),
            new MorphAssignmentService(randomProvider),
            new MorphGenerationService(),
            filePicker ?? new StaticNpcTextFilePicker(Array.Empty<string>()),
            clipboard ?? new CapturingClipboardService());
    }

    private static ProjectModel CreateProjectWithPresets()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Alpha"));
        project.SliderPresets.Add(new SliderPreset("Beta"));
        return project;
    }

    private static Npc CreateNpc(string mod, string name, string editorId, string race, string formId)
    {
        return new Npc(name)
        {
            Mod = mod,
            EditorId = editorId,
            Race = race,
            FormId = formId,
        };
    }

    private sealed class QueueRandomAssignmentProvider : IRandomAssignmentProvider
    {
        private readonly Queue<int> values;

        public QueueRandomAssignmentProvider(params int[] values)
        {
            this.values = new Queue<int>(values);
        }

        public int NextIndex(int exclusiveMax)
        {
            if (values.Count == 0)
            {
                return 0;
            }

            return values.Dequeue();
        }
    }

    private sealed class StaticNpcTextFilePicker : INpcTextFilePicker
    {
        private readonly IReadOnlyList<string> files;

        public StaticNpcTextFilePicker(IReadOnlyList<string> files)
        {
            this.files = files;
        }

        public Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult(files);
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

    private sealed class TemporaryDirectory : IDisposable
    {
        private readonly string path = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));

        public TemporaryDirectory()
        {
            Directory.CreateDirectory(path);
        }

        public string WriteText(string fileName, string text)
        {
            var filePath = Path.Combine(path, fileName);
            File.WriteAllText(filePath, text);
            return filePath;
        }

        public void Dispose()
        {
            Directory.Delete(path, recursive: true);
        }
    }
}
