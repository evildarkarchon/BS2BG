using System.Globalization;
using BS2BG.Core.Morphs;
using ReactiveUI;
using ReactiveUI.SourceGenerators;

namespace BS2BG.App.ViewModels;

/// <summary>
/// Editable presentation row for one persisted assignment-strategy rule.
/// Phase 5 deliberately uses comma-separated token text for preset and race input so the UI can ship without changing the Core schema.
/// </summary>
public sealed partial class AssignmentStrategyRuleRowViewModel : ReactiveObject
{
    [Reactive] private string _bucketName = string.Empty;
    [Reactive] private string _name = string.Empty;
    [Reactive] private string _presetNamesText = string.Empty;
    [Reactive] private string _raceFiltersText = string.Empty;
    [Reactive] private string _validationMessage = string.Empty;
    [Reactive] private double _weight = 1.0;

    /// <summary>
    /// Creates an editable row from a persisted strategy rule.
    /// </summary>
    /// <param name="rule">Persisted rule to copy into text-first controls.</param>
    /// <returns>A row whose comma-separated fields preserve normalized Core rule tokens.</returns>
    public static AssignmentStrategyRuleRowViewModel FromRule(AssignmentStrategyRule rule)
    {
        ArgumentNullException.ThrowIfNull(rule);

        return new AssignmentStrategyRuleRowViewModel
        {
            Name = rule.Name,
            PresetNamesText = string.Join(", ", rule.PresetNames),
            RaceFiltersText = string.Join(", ", rule.RaceFilters),
            Weight = rule.Weight,
            BucketName = rule.BucketName ?? string.Empty
        };
    }

    /// <summary>
    /// Parses comma-separated preset tokens using trim and ordinal-ignore-case duplicate removal.
    /// </summary>
    /// <returns>Normalized preset tokens in first-seen order.</returns>
    public IReadOnlyList<string> ParsePresetNames() => ParseCommaSeparatedTokens(PresetNamesText);

    /// <summary>
    /// Parses comma-separated imported race tokens using trim and ordinal-ignore-case duplicate removal.
    /// </summary>
    /// <returns>Normalized race tokens in first-seen order.</returns>
    public IReadOnlyList<string> ParseRaceFilters() => ParseCommaSeparatedTokens(RaceFiltersText);

    /// <summary>
    /// Converts the editable row to a Core strategy rule after row-level validation has succeeded.
    /// </summary>
    /// <returns>A new immutable strategy rule.</returns>
    public AssignmentStrategyRule ToRule() => new(
        Name.Trim(),
        ParsePresetNames(),
        ParseRaceFilters(),
        Weight,
        string.IsNullOrWhiteSpace(BucketName) ? null : BucketName.Trim());

    private static IReadOnlyList<string> ParseCommaSeparatedTokens(string text)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var values = new List<string>();
        foreach (var token in (text ?? string.Empty).Split(',', StringSplitOptions.RemoveEmptyEntries))
        {
            var normalized = token.Trim();
            if (normalized.Length == 0 || !seen.Add(normalized)) continue;

            values.Add(normalized);
        }

        return values;
    }

    /// <summary>
    /// Formats a weight for status text without introducing culture-specific decimal separators.
    /// </summary>
    /// <returns>The row weight as invariant text.</returns>
    public string FormatWeight() => Weight.ToString("0.##", CultureInfo.InvariantCulture);
}
