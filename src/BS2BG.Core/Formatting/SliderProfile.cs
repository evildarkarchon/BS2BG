namespace BS2BG.Core.Formatting;

public sealed class SliderProfile
{
    private readonly Dictionary<string, SliderDefault> defaultsByName;
    private readonly HashSet<string> invertedNames;
    private readonly Dictionary<string, float> multipliersByName;

    public SliderProfile(
        IEnumerable<SliderDefault> defaults,
        IEnumerable<SliderMultiplier> multipliers,
        IEnumerable<string> invertedNames)
    {
        Defaults = defaults?.ToArray() ?? throw new ArgumentNullException(nameof(defaults));
        Multipliers = (multipliers ?? throw new ArgumentNullException(nameof(multipliers))).ToArray();
        InvertedNames = (invertedNames ?? throw new ArgumentNullException(nameof(invertedNames))).ToArray();
        defaultsByName = Defaults.ToDictionary(
            value => value.Name,
            StringComparer.OrdinalIgnoreCase);
        multipliersByName = Multipliers
            .ToDictionary(value => value.Name, value => value.Value, StringComparer.OrdinalIgnoreCase);
        this.invertedNames = new HashSet<string>(
            InvertedNames,
            StringComparer.OrdinalIgnoreCase);
    }

    public IReadOnlyList<SliderDefault> Defaults { get; }

    /// <summary>
    /// Gets profile multiplier table entries so diagnostics can report profile behavior without re-parsing JSON.
    /// </summary>
    public IReadOnlyList<SliderMultiplier> Multipliers { get; }

    /// <summary>
    /// Gets profile inverted slider names so diagnostics can report profile behavior without changing formatter semantics.
    /// </summary>
    public IReadOnlyList<string> InvertedNames { get; }

    public int GetDefaultSmall(string sliderName)
    {
        return defaultsByName.TryGetValue(sliderName, out var value)
            ? (int)MathF.Round(value.ValueSmall * 100f, MidpointRounding.AwayFromZero)
            : 0;
    }

    public int GetDefaultBig(string sliderName)
    {
        return defaultsByName.TryGetValue(sliderName, out var value)
            ? (int)MathF.Round(value.ValueBig * 100f, MidpointRounding.AwayFromZero)
            : 0;
    }

    public float GetMultiplier(string sliderName) =>
        multipliersByName.TryGetValue(sliderName, out var value) ? value : 1f;

    public bool IsInverted(string sliderName) => invertedNames.Contains(sliderName);
}
