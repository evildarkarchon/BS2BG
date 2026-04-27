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
