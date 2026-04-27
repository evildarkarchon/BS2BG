using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.Core.Diagnostics;

/// <summary>
/// Analyzes how project presets interact with bundled profile tables and neutral fallback rules.
/// </summary>
public sealed class ProfileDiagnosticsService
{
    /// <summary>
    /// Builds whole-project or selected-preset profile diagnostics without mutating project state.
    /// </summary>
    /// <param name="project">Project whose presets should be inspected.</param>
    /// <param name="catalog">Profile catalog that supplies bundled profiles and fallback behavior.</param>
    /// <param name="selectedPresetName">Optional preset name filter for drilldown diagnostics.</param>
    /// <returns>Summary, slider-level details, and neutral informational findings.</returns>
    public static ProfileDiagnosticsReport Analyze(ProjectModel project, TemplateProfileCatalog catalog, string? selectedPresetName = null)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));
        if (catalog is null) throw new ArgumentNullException(nameof(catalog));

        var presets = SelectPresets(project, selectedPresetName).ToArray();
        var sliderDiagnostics = new List<ProfileSliderDiagnostic>();
        var findings = new List<DiagnosticFinding>();
        var savedProfileNames = new SortedSet<string>(StringComparer.OrdinalIgnoreCase);
        var calculationFallbackProfileName = catalog.DefaultProfile.Name;
        var knownSliderNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var unknownSliderNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var injectedDefaultKeys = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var multiplierNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var inversionNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        foreach (var preset in presets)
        {
            var profile = catalog.GetProfile(preset.ProfileName);
            if (!catalog.ContainsProfile(preset.ProfileName))
            {
                savedProfileNames.Add(preset.ProfileName);
                calculationFallbackProfileName = profile.Name;
            }

            var defaultNames = new HashSet<string>(
                profile.SliderProfile.Defaults.Select(value => value.Name),
                StringComparer.OrdinalIgnoreCase);
            var multiplierTableNames = new HashSet<string>(
                profile.SliderProfile.Multipliers.Select(value => value.Name),
                StringComparer.OrdinalIgnoreCase);
            var invertedTableNames = new HashSet<string>(
                profile.SliderProfile.InvertedNames,
                StringComparer.OrdinalIgnoreCase);
            var knownNamesForProfile = new HashSet<string>(defaultNames, StringComparer.OrdinalIgnoreCase);
            knownNamesForProfile.UnionWith(multiplierTableNames);
            knownNamesForProfile.UnionWith(invertedTableNames);
            var explicitSliderNames = new HashSet<string>(
                preset.SetSliders.Select(slider => slider.Name),
                StringComparer.OrdinalIgnoreCase);

            foreach (var slider in preset.SetSliders)
            {
                var isKnown = knownNamesForProfile.Contains(slider.Name);
                AddName(isKnown ? knownSliderNames : unknownSliderNames, slider.Name);
                if (multiplierTableNames.Contains(slider.Name)) AddName(multiplierNames, slider.Name);
                if (invertedTableNames.Contains(slider.Name)) AddName(inversionNames, slider.Name);

                sliderDiagnostics.Add(new ProfileSliderDiagnostic(
                    preset.Name,
                    slider.Name,
                    isKnown,
                    isInjectedDefault: false,
                    hasMultiplier: multiplierTableNames.Contains(slider.Name),
                    isInverted: invertedTableNames.Contains(slider.Name)));
            }

            foreach (var defaultName in defaultNames.Where(name => !explicitSliderNames.Contains(name)))
            {
                AddName(injectedDefaultKeys, preset.Name + "\u001f" + defaultName);

                sliderDiagnostics.Add(new ProfileSliderDiagnostic(
                    preset.Name,
                    defaultName,
                    isKnown: true,
                    isInjectedDefault: true,
                    hasMultiplier: multiplierTableNames.Contains(defaultName),
                    isInverted: invertedTableNames.Contains(defaultName)));
            }
        }

        foreach (var recoveryDiagnostic in ProfileRecoveryDiagnosticsService.Analyze(project, catalog))
            findings.Add(ToFinding(recoveryDiagnostic));

        var summary = new ProfileDiagnosticsSummary(
            presets.Length,
            knownSliderNames.Count,
            unknownSliderNames.Count,
            injectedDefaultKeys.Count,
            multiplierNames.Count,
            inversionNames.Count,
            savedProfileNames.Count > 0,
            savedProfileNames.ToArray(),
            calculationFallbackProfileName);

        return new ProfileDiagnosticsReport(summary, sliderDiagnostics, findings);
    }

    private static IEnumerable<SliderPreset> SelectPresets(ProjectModel project, string? selectedPresetName)
    {
        if (string.IsNullOrWhiteSpace(selectedPresetName)) return project.SliderPresets;

        return project.SliderPresets.Where(preset => string.Equals(
            preset.Name,
            selectedPresetName,
            StringComparison.OrdinalIgnoreCase));
    }

    private static void AddName(HashSet<string> names, string name)
    {
        if (!string.IsNullOrWhiteSpace(name)) names.Add(name);
    }

    private static DiagnosticFinding ToFinding(ProfileRecoveryDiagnostic diagnostic)
    {
        return new DiagnosticFinding(
            diagnostic.Severity,
            "Profiles",
            "Missing custom profile recovery",
            diagnostic.Detail,
            diagnostic.MissingProfileName,
            "Available recovery actions: " + string.Join(", ", diagnostic.Actions),
            diagnostic.Code,
            diagnostic.Category);
    }
}

/// <summary>
/// Complete profile diagnostic result containing summary, slider drilldown rows, and findings.
/// </summary>
public sealed class ProfileDiagnosticsReport
{
    /// <summary>
    /// Creates an immutable profile diagnostics report.
    /// </summary>
    public ProfileDiagnosticsReport(
        ProfileDiagnosticsSummary summary,
        IEnumerable<ProfileSliderDiagnostic> sliderDiagnostics,
        IEnumerable<DiagnosticFinding> findings)
    {
        Summary = summary ?? throw new ArgumentNullException(nameof(summary));
        SliderDiagnostics = (sliderDiagnostics ?? throw new ArgumentNullException(nameof(sliderDiagnostics))).ToArray();
        Findings = (findings ?? throw new ArgumentNullException(nameof(findings))).ToArray();
    }

    public ProfileDiagnosticsSummary Summary { get; }

    public IReadOnlyList<ProfileSliderDiagnostic> SliderDiagnostics { get; }

    public IReadOnlyList<DiagnosticFinding> Findings { get; }
}

/// <summary>
/// Aggregated counts describing profile table coverage and neutral fallback state.
/// </summary>
public sealed class ProfileDiagnosticsSummary
{
    /// <summary>
    /// Creates immutable aggregate counts for profile diagnostics.
    /// </summary>
    public ProfileDiagnosticsSummary(
        int affectedPresetCount,
        int knownSliderCount,
        int unknownSliderCount,
        int injectedDefaultCount,
        int multiplierCount,
        int inversionCount,
        bool hasNeutralFallback,
        IEnumerable<string> savedProfileNames,
        string calculationFallbackProfileName)
    {
        AffectedPresetCount = affectedPresetCount;
        KnownSliderCount = knownSliderCount;
        UnknownSliderCount = unknownSliderCount;
        InjectedDefaultCount = injectedDefaultCount;
        MultiplierCount = multiplierCount;
        InversionCount = inversionCount;
        HasNeutralFallback = hasNeutralFallback;
        SavedProfileNames = (savedProfileNames ?? throw new ArgumentNullException(nameof(savedProfileNames))).ToArray();
        CalculationFallbackProfileName = calculationFallbackProfileName
                                         ?? throw new ArgumentNullException(nameof(calculationFallbackProfileName));
    }

    public int AffectedPresetCount { get; }

    public int KnownSliderCount { get; }

    public int UnknownSliderCount { get; }

    public int InjectedDefaultCount { get; }

    public int MultiplierCount { get; }

    public int InversionCount { get; }

    public bool HasNeutralFallback { get; }

    public IReadOnlyList<string> SavedProfileNames { get; }

    public string CalculationFallbackProfileName { get; }
}

/// <summary>
/// Slider-level diagnostic row for coverage, injected default, multiplier, and inversion drilldown.
/// </summary>
public sealed class ProfileSliderDiagnostic
{
    /// <summary>
    /// Creates an immutable slider diagnostic row for a specific preset and slider name.
    /// </summary>
    public ProfileSliderDiagnostic(
        string presetName,
        string name,
        bool isKnown,
        bool isInjectedDefault,
        bool hasMultiplier,
        bool isInverted)
    {
        PresetName = presetName ?? throw new ArgumentNullException(nameof(presetName));
        Name = name ?? throw new ArgumentNullException(nameof(name));
        IsKnown = isKnown;
        IsInjectedDefault = isInjectedDefault;
        HasMultiplier = hasMultiplier;
        IsInverted = isInverted;
    }

    public string PresetName { get; }

    public string Name { get; }

    public bool IsKnown { get; }

    public bool IsInjectedDefault { get; }

    public bool HasMultiplier { get; }

    public bool IsInverted { get; }
}
