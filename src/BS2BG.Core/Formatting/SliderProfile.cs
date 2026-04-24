namespace BS2BG.Core.Formatting;

public sealed class SliderProfile
{
    private readonly IReadOnlyList<SliderDefault> defaults;
    private readonly Dictionary<string, SliderDefault> defaultsByName;
    private readonly Dictionary<string, float> multipliersByName;
    private readonly HashSet<string> invertedNames;

    public SliderProfile(
        IEnumerable<SliderDefault> defaults,
        IEnumerable<SliderMultiplier> multipliers,
        IEnumerable<string> invertedNames)
    {
        this.defaults = defaults?.ToArray() ?? throw new ArgumentNullException(nameof(defaults));
        defaultsByName = this.defaults.ToDictionary(
            value => value.Name,
            StringComparer.OrdinalIgnoreCase);
        multipliersByName = (multipliers ?? throw new ArgumentNullException(nameof(multipliers)))
            .ToDictionary(value => value.Name, value => value.Value, StringComparer.OrdinalIgnoreCase);
        this.invertedNames = new HashSet<string>(
            invertedNames ?? throw new ArgumentNullException(nameof(invertedNames)),
            StringComparer.OrdinalIgnoreCase);
    }

    public IReadOnlyList<SliderDefault> Defaults => defaults;

    public int GetDefaultSmall(string sliderName)
    {
        return defaultsByName.TryGetValue(sliderName, out var value)
            ? (int)(value.ValueSmall * 100f)
            : 0;
    }

    public int GetDefaultBig(string sliderName)
    {
        return defaultsByName.TryGetValue(sliderName, out var value)
            ? (int)(value.ValueBig * 100f)
            : 0;
    }

    public float GetMultiplier(string sliderName)
    {
        return multipliersByName.TryGetValue(sliderName, out var value) ? value : 1f;
    }

    public bool IsInverted(string sliderName)
    {
        return invertedNames.Contains(sliderName);
    }
}
