using System.Text;
using BS2BG.Core.Generation;
using BS2BG.Core.IO;
using BS2BG.Core.Models;

namespace BS2BG.Core.Export;

public class BosJsonExportWriter(TemplateGenerationService templateGenerationService)
{
    private static readonly Encoding Utf8NoBom = new UTF8Encoding(false);
    private readonly BosJsonExportPlanner planner = new();

    private readonly TemplateGenerationService templateGenerationService = templateGenerationService
                                                                           ?? throw new ArgumentNullException(
                                                                               nameof(templateGenerationService));

    public virtual BosJsonExportResult Write(
        string directoryPath,
        IEnumerable<SliderPreset> presets,
        TemplateProfileCatalog profileCatalog)
    {
        if (directoryPath is null) throw new ArgumentNullException(nameof(directoryPath));

        if (presets is null) throw new ArgumentNullException(nameof(presets));

        if (profileCatalog is null) throw new ArgumentNullException(nameof(profileCatalog));

        Directory.CreateDirectory(directoryPath);

        var plannedPaths = planner.Plan(directoryPath, presets).ToArray();
        var entries = new List<(string Path, string Content)>();
        foreach (var pair in presets.OrderBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase).Zip(plannedPaths, (preset, path) => (preset, path)))
        {
            var json = templateGenerationService.PreviewBosJson(
                pair.preset,
                profileCatalog.GetProfile(pair.preset.ProfileName));
            entries.Add((pair.path, json));
        }

        if (entries.Count == 0) return new BosJsonExportResult(Array.Empty<string>());

        AtomicFileWriter.WriteAtomicBatch(entries, Utf8NoBom);

        return new BosJsonExportResult(entries.Select(entry => entry.Path).ToArray());
    }
}

public sealed class BosJsonExportResult(IEnumerable<string> filePaths)
{
    public IReadOnlyList<string> FilePaths { get; } =
        (filePaths ?? throw new ArgumentNullException(nameof(filePaths))).ToArray();
}
