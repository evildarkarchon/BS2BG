using System.Diagnostics.CodeAnalysis;
using BS2BG.Core.Models;

namespace BS2BG.Core.Morphs;

[SuppressMessage("Performance", "CA1822:Mark members as static", Justification = "Morph assignment is exposed as an injectable service surface.")]
public sealed class MorphAssignmentService
{
    private readonly IRandomAssignmentProvider randomAssignmentProvider;

    public MorphAssignmentService(IRandomAssignmentProvider randomAssignmentProvider)
    {
        this.randomAssignmentProvider = randomAssignmentProvider
            ?? throw new ArgumentNullException(nameof(randomAssignmentProvider));
    }

    public bool TryAddCustomTarget(
        ProjectModel project,
        string targetName,
        out CustomMorphTarget target,
        out string error)
    {
        if (project is null)
        {
            throw new ArgumentNullException(nameof(project));
        }

        target = null!;
        var normalizedName = NormalizeCustomTargetName(targetName);
        if (!IsValidCustomTargetName(normalizedName))
        {
            error = "Custom target must use Context|Gender or Context|Gender|Race[Variant].";
            return false;
        }

        if (project.CustomMorphTargets.Any(existing => string.Equals(
                existing.Name,
                normalizedName,
                StringComparison.OrdinalIgnoreCase)))
        {
            error = "A custom target named '" + normalizedName + "' already exists.";
            return false;
        }

        target = new CustomMorphTarget(normalizedName);
        AssignRandomPreset(target, project.SliderPresets);
        project.CustomMorphTargets.Add(target);
        project.SortCustomMorphTargets();
        error = string.Empty;
        return true;
    }

    public bool RemoveCustomTarget(ProjectModel project, CustomMorphTarget? target)
    {
        if (project is null)
        {
            throw new ArgumentNullException(nameof(project));
        }

        if (target is null)
        {
            return false;
        }

        target.ClearSliderPresets();
        return project.CustomMorphTargets.Remove(target);
    }

    public bool AddPresetToTarget(MorphTargetBase? target, SliderPreset? preset)
    {
        if (target is null || preset is null)
        {
            return false;
        }

        var previousCount = target.SliderPresets.Count;
        target.AddSliderPreset(preset);
        return target.SliderPresets.Count != previousCount;
    }

    public int AddAllPresetsToTarget(MorphTargetBase? target, IEnumerable<SliderPreset> presets)
    {
        if (target is null)
        {
            return 0;
        }

        if (presets is null)
        {
            throw new ArgumentNullException(nameof(presets));
        }

        var previousCount = target.SliderPresets.Count;
        foreach (var preset in presets)
        {
            target.AddSliderPreset(preset);
        }

        return target.SliderPresets.Count - previousCount;
    }

    public bool RemovePresetFromTarget(MorphTargetBase? target, SliderPreset? preset)
    {
        return target is not null
            && preset is not null
            && target.RemoveSliderPreset(preset.Name);
    }

    public int ClearTargetPresets(MorphTargetBase? target)
    {
        if (target is null)
        {
            return 0;
        }

        var count = target.SliderPresets.Count;
        target.ClearSliderPresets();
        return count;
    }

    public bool AddNpcToMorphs(ProjectModel project, Npc? npc, bool assignRandom)
    {
        if (project is null)
        {
            throw new ArgumentNullException(nameof(project));
        }

        if (npc is null || ContainsNpc(project.MorphedNpcs, npc))
        {
            return false;
        }

        npc.ClearSliderPresets();
        if (assignRandom)
        {
            AssignRandomPreset(npc, project.SliderPresets);
        }

        project.MorphedNpcs.Add(npc);
        return true;
    }

    public int AddNpcsToMorphs(ProjectModel project, IEnumerable<Npc> npcs, bool assignRandom)
    {
        if (project is null)
        {
            throw new ArgumentNullException(nameof(project));
        }

        if (npcs is null)
        {
            throw new ArgumentNullException(nameof(npcs));
        }

        var added = 0;
        foreach (var npc in npcs.ToArray())
        {
            if (AddNpcToMorphs(project, npc, assignRandom))
            {
                added++;
            }
        }

        return added;
    }

    public int FillEmptyNpcs(IEnumerable<Npc> visibleNpcs, IReadOnlyList<SliderPreset> candidatePresets)
    {
        if (visibleNpcs is null)
        {
            throw new ArgumentNullException(nameof(visibleNpcs));
        }

        if (candidatePresets is null)
        {
            throw new ArgumentNullException(nameof(candidatePresets));
        }

        if (candidatePresets.Count == 0)
        {
            return 0;
        }

        var filled = 0;
        foreach (var npc in visibleNpcs)
        {
            if (npc.SliderPresets.Count != 0)
            {
                continue;
            }

            npc.ClearSliderPresets();
            AssignRandomPreset(npc, candidatePresets);
            filled++;
        }

        return filled;
    }

    public int ClearAssignments(IEnumerable<Npc> visibleNpcs)
    {
        if (visibleNpcs is null)
        {
            throw new ArgumentNullException(nameof(visibleNpcs));
        }

        var cleared = 0;
        foreach (var npc in visibleNpcs)
        {
            if (npc.SliderPresets.Count == 0)
            {
                continue;
            }

            npc.ClearSliderPresets();
            cleared++;
        }

        return cleared;
    }

    public bool RemoveNpc(ProjectModel project, Npc? npc)
    {
        if (project is null)
        {
            throw new ArgumentNullException(nameof(project));
        }

        if (npc is null)
        {
            return false;
        }

        npc.ClearSliderPresets();
        return project.MorphedNpcs.Remove(npc);
    }

    private void AssignRandomPreset(MorphTargetBase target, IReadOnlyList<SliderPreset> presets)
    {
        if (presets.Count == 0)
        {
            return;
        }

        var index = randomAssignmentProvider.NextIndex(presets.Count);
        if ((uint)index >= (uint)presets.Count)
        {
            index = 0;
        }

        target.AddSliderPreset(presets[index]);
    }

    private static bool ContainsNpc(IEnumerable<Npc> npcs, Npc candidate)
    {
        return npcs.Any(npc => IsSameNpc(npc, candidate));
    }

    private static bool IsSameNpc(Npc left, Npc right)
    {
        return string.Equals(left.Mod, right.Mod, StringComparison.OrdinalIgnoreCase)
            && string.Equals(left.EditorId, right.EditorId, StringComparison.OrdinalIgnoreCase);
    }

    private static string NormalizeCustomTargetName(string value)
    {
        return (value ?? string.Empty).Trim();
    }

    private static bool IsValidCustomTargetName(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return false;
        }

        var parts = value.Split('|');
        return (parts.Length == 2 || parts.Length == 3)
            && parts.All(part => !string.IsNullOrWhiteSpace(part));
    }
}
