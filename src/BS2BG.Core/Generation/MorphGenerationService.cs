using System.Diagnostics.CodeAnalysis;
using BS2BG.Core.Models;

namespace BS2BG.Core.Generation;

[SuppressMessage("Performance", "CA1822:Mark members as static", Justification = "Morph generation is exposed as an injectable service surface.")]
public sealed class MorphGenerationService
{
    private const string NewLine = "\r\n";

    public MorphGenerationResult GenerateMorphs(ProjectModel project)
    {
        if (project is null)
        {
            throw new ArgumentNullException(nameof(project));
        }

        return GenerateMorphs(project.CustomMorphTargets, project.MorphedNpcs);
    }

    public MorphGenerationResult GenerateMorphs(
        IEnumerable<CustomMorphTarget> customTargets,
        IEnumerable<Npc> npcs)
    {
        if (customTargets is null)
        {
            throw new ArgumentNullException(nameof(customTargets));
        }

        if (npcs is null)
        {
            throw new ArgumentNullException(nameof(npcs));
        }

        var lines = new List<string>();
        var targetsWithoutPresets = new List<MorphTargetBase>();

        foreach (var target in customTargets)
        {
            lines.Add(target.ToMorphLine());
            if (!target.HasPresets)
            {
                targetsWithoutPresets.Add(target);
            }
        }

        foreach (var npc in npcs.OrderBy(npc => npc.Mod, StringComparer.OrdinalIgnoreCase))
        {
            lines.Add(npc.ToMorphLine());
            if (!npc.HasPresets)
            {
                targetsWithoutPresets.Add(npc);
            }
        }

        return new MorphGenerationResult(string.Join(NewLine, lines), targetsWithoutPresets);
    }
}
