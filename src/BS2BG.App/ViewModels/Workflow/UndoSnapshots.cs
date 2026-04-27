using BS2BG.Core.Models;

namespace BS2BG.App.ViewModels.Workflow;

/// <summary>
/// Captures a set-slider as scalar values so undo records never depend on a mutable live slider instance.
/// The snapshot can recreate a model slider or restore an existing one during replay.
/// </summary>
public sealed record SetSliderValueSnapshot(
    string Name,
    bool Enabled,
    int? ValueSmall,
    int? ValueBig,
    int PercentMin,
    int PercentMax)
{
    /// <summary>
    /// Creates a value snapshot from the current slider values.
    /// </summary>
    public static SetSliderValueSnapshot Create(SetSlider slider)
    {
        ArgumentNullException.ThrowIfNull(slider);

        return new SetSliderValueSnapshot(
            slider.Name,
            slider.Enabled,
            slider.ValueSmall,
            slider.ValueBig,
            slider.PercentMin,
            slider.PercentMax);
    }

    /// <summary>
    /// Recreates a new slider instance with the captured values.
    /// </summary>
    public SetSlider ToSetSlider() =>
        new(Name)
        {
            Enabled = Enabled,
            ValueSmall = ValueSmall,
            ValueBig = ValueBig,
            PercentMin = PercentMin,
            PercentMax = PercentMax
        };

    /// <summary>
    /// Restores captured scalar values onto an existing slider instance.
    /// </summary>
    public void Apply(SetSlider slider)
    {
        ArgumentNullException.ThrowIfNull(slider);

        slider.Enabled = Enabled;
        slider.ValueSmall = ValueSmall;
        slider.ValueBig = ValueBig;
        slider.PercentMin = PercentMin;
        slider.PercentMax = PercentMax;
    }
}

/// <summary>
/// Captures a slider preset as values at operation time, including profile and slider collections.
/// Undo/redo replay uses fresh model instances from this snapshot to avoid live-reference corruption.
/// </summary>
public sealed record PresetValueSnapshot(
    string Name,
    string ProfileName,
    IReadOnlyList<SetSliderValueSnapshot> SetSliders,
    IReadOnlyList<SetSliderValueSnapshot> MissingDefaultSetSliders)
{
    /// <summary>
    /// Creates a value snapshot from the current preset values.
    /// </summary>
    public static PresetValueSnapshot Create(SliderPreset preset)
    {
        ArgumentNullException.ThrowIfNull(preset);

        return new PresetValueSnapshot(
            preset.Name,
            preset.ProfileName,
            preset.SetSliders.Select(SetSliderValueSnapshot.Create).ToArray(),
            preset.MissingDefaultSetSliders.Select(SetSliderValueSnapshot.Create).ToArray());
    }

    /// <summary>
    /// Recreates a new preset instance with the captured profile and slider values.
    /// </summary>
    public SliderPreset ToPreset()
    {
        var preset = new SliderPreset(Name, ProfileName);
        foreach (var slider in SetSliders) preset.AddSetSlider(slider.ToSetSlider());

        foreach (var slider in MissingDefaultSetSliders) preset.MissingDefaultSetSliders.Add(slider.ToSetSlider());

        return preset;
    }

    /// <summary>
    /// Restores captured values onto an existing preset instance while preserving that object's identity.
    /// </summary>
    public void ApplyTo(SliderPreset preset)
    {
        ArgumentNullException.ThrowIfNull(preset);

        preset.Name = Name;
        preset.ProfileName = ProfileName;
        preset.SetSliders.Clear();
        preset.MissingDefaultSetSliders.Clear();
        foreach (var slider in SetSliders) preset.AddSetSlider(slider.ToSetSlider());

        foreach (var slider in MissingDefaultSetSliders) preset.MissingDefaultSetSliders.Add(slider.ToSetSlider());
    }
}

/// <summary>
/// Captures a custom morph target by value, including collection position and assigned preset names.
/// Replay creates a fresh target so detached target mutations cannot corrupt undo state.
/// </summary>
public sealed record CustomTargetValueSnapshot(
    int Index,
    string Name,
    IReadOnlyList<string> AssignedPresetNames)
{
    /// <summary>
    /// Creates a value snapshot from the current custom target values and collection index.
    /// </summary>
    public static CustomTargetValueSnapshot Create(CustomMorphTarget target, int index)
    {
        ArgumentNullException.ThrowIfNull(target);

        return new CustomTargetValueSnapshot(
            index,
            target.Name,
            target.SliderPresets.Select(preset => preset.Name).ToArray());
    }

    /// <summary>
    /// Recreates the target and resolves captured assignment names against the current preset collection.
    /// Missing presets are skipped because replay must not resurrect deleted preset rows.
    /// </summary>
    public CustomMorphTarget ToTarget(IEnumerable<SliderPreset> availablePresets)
    {
        ArgumentNullException.ThrowIfNull(availablePresets);

        var target = new CustomMorphTarget(Name);
        AddResolvedAssignments(target, AssignedPresetNames, availablePresets);
        return target;
    }

    internal static void AddResolvedAssignments(
        MorphTargetBase target,
        IEnumerable<string> presetNames,
        IEnumerable<SliderPreset> availablePresets)
    {
        var presetsByName = availablePresets
            .GroupBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase)
            .ToDictionary(group => group.Key, group => group.First(), StringComparer.OrdinalIgnoreCase);
        foreach (var presetName in presetNames)
            if (presetsByName.TryGetValue(presetName, out var preset))
                target.AddSliderPreset(preset);
    }
}

/// <summary>
/// Captures an NPC row by stable UI row ID plus scalar values and assigned preset names.
/// Undo/redo can resolve the current row by ID or recreate it when the row has been removed.
/// </summary>
public sealed record NpcAssignmentSnapshot(
    Guid RowId,
    int Index,
    string Mod,
    string Name,
    string EditorId,
    string Race,
    string FormId,
    IReadOnlyList<string> AssignedPresetNames)
{
    /// <summary>
    /// Creates a value snapshot from the current NPC values, stable row ID, and collection index.
    /// </summary>
    public static NpcAssignmentSnapshot Create(Npc npc, Guid rowId, int index)
    {
        ArgumentNullException.ThrowIfNull(npc);

        return new NpcAssignmentSnapshot(
            rowId,
            index,
            npc.Mod,
            npc.Name,
            npc.EditorId,
            npc.Race,
            npc.FormId,
            npc.SliderPresets.Select(preset => preset.Name).ToArray());
    }

    /// <summary>
    /// Recreates an NPC with captured field values and assignments resolved by current preset names.
    /// </summary>
    public Npc ToNpc(IEnumerable<SliderPreset> availablePresets)
    {
        ArgumentNullException.ThrowIfNull(availablePresets);

        var npc = new Npc(Name)
        {
            Mod = Mod,
            EditorId = EditorId,
            Race = Race,
            FormId = FormId
        };
        CustomTargetValueSnapshot.AddResolvedAssignments(npc, AssignedPresetNames, availablePresets);
        return npc;
    }

    /// <summary>
    /// Restores captured scalar values and assignment names onto an existing row when replay resolves it by ID.
    /// </summary>
    public void ApplyTo(Npc npc, IEnumerable<SliderPreset> availablePresets)
    {
        ArgumentNullException.ThrowIfNull(npc);
        ArgumentNullException.ThrowIfNull(availablePresets);

        npc.Mod = Mod;
        npc.Name = Name;
        npc.EditorId = EditorId;
        npc.Race = Race;
        npc.FormId = FormId;
        npc.ClearSliderPresets();
        CustomTargetValueSnapshot.AddResolvedAssignments(npc, AssignedPresetNames, availablePresets);
    }
}

/// <summary>
/// Captures one target's assigned preset names and the identity needed to resolve it during replay.
/// NPC targets use stable row IDs; custom targets use their unique target name.
/// </summary>
public sealed record MorphTargetAssignmentSnapshot(
    Guid? NpcRowId,
    string TargetName,
    IReadOnlyList<string> AssignedPresetNames)
{
    /// <summary>
    /// Creates an assignment snapshot for a target at operation time.
    /// </summary>
    public static MorphTargetAssignmentSnapshot Create(MorphTargetBase target, Guid? npcRowId = null)
    {
        ArgumentNullException.ThrowIfNull(target);

        return new MorphTargetAssignmentSnapshot(
            npcRowId,
            target.Name,
            target.SliderPresets.Select(preset => preset.Name).ToArray());
    }

    /// <summary>
    /// Applies captured assignment names to the resolved current target.
    /// </summary>
    public void ApplyTo(MorphTargetBase target, IEnumerable<SliderPreset> availablePresets)
    {
        ArgumentNullException.ThrowIfNull(target);
        ArgumentNullException.ThrowIfNull(availablePresets);

        target.ClearSliderPresets();
        CustomTargetValueSnapshot.AddResolvedAssignments(target, AssignedPresetNames, availablePresets);
    }
}
