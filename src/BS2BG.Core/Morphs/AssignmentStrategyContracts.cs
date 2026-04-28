using BS2BG.Core.Models;

namespace BS2BG.Core.Morphs;

/// <summary>
/// Identifies the primary algorithm used when applying persisted assignment strategy rules.
/// </summary>
public enum AssignmentStrategyKind
{
    SeededRandom,
    RoundRobin,
    Weighted,
    RaceFilters,
    GroupsBuckets
}

/// <summary>
/// Persisted assignment strategy configuration stored in project files for deterministic replay.
/// </summary>
public sealed record AssignmentStrategyDefinition
{
    public const int CurrentSchemaVersion = 1;

    /// <summary>
    /// Creates a strategy definition with a versioned schema and composable rules.
    /// </summary>
    /// <param name="schemaVersion">Persisted strategy schema version; new strategies use <see cref="CurrentSchemaVersion" />.</param>
    /// <param name="kind">Primary selection algorithm for the rule set.</param>
    /// <param name="seed">Optional deterministic seed used by seeded random strategies.</param>
    /// <param name="rules">Rules carrying preset eligibility, race filters, weights, and bucket metadata.</param>
    public AssignmentStrategyDefinition(
        int schemaVersion,
        AssignmentStrategyKind kind,
        int? seed,
        IReadOnlyList<AssignmentStrategyRule>? rules)
    {
        SchemaVersion = schemaVersion;
        Kind = kind;
        Seed = seed;
        Rules = rules?.ToArray() ?? Array.Empty<AssignmentStrategyRule>();
    }

    public int SchemaVersion { get; init; } = CurrentSchemaVersion;

    public AssignmentStrategyKind Kind { get; init; }

    public int? Seed { get; init; }

    public IReadOnlyList<AssignmentStrategyRule> Rules { get; init; } = Array.Empty<AssignmentStrategyRule>();
}

/// <summary>
/// Composable assignment strategy rule describing eligible presets, imported-race filters, weight, and optional bucket membership.
/// </summary>
public sealed record AssignmentStrategyRule
{
    public static readonly StringComparer RaceComparer = StringComparer.OrdinalIgnoreCase;

    /// <summary>
    /// Creates a strategy rule. Empty race filters intentionally match any imported NPC race for group/bucket rules.
    /// </summary>
    /// <param name="name">Optional human-readable rule name; validation rejects duplicate non-empty names.</param>
    /// <param name="presetNames">Preset names eligible for this rule.</param>
    /// <param name="raceFilters">Imported <see cref="Npc.Race" /> values matched with <see cref="StringComparer.OrdinalIgnoreCase" />.</param>
    /// <param name="weight">Weighted-strategy relative weight.</param>
    /// <param name="bucketName">Optional group/bucket label used by group assignment strategies.</param>
    public AssignmentStrategyRule(
        string? name,
        IReadOnlyList<string>? presetNames,
        IReadOnlyList<string>? raceFilters,
        double weight,
        string? bucketName)
    {
        Name = name ?? string.Empty;
        PresetNames = Normalize(presetNames);
        RaceFilters = Normalize(raceFilters);
        Weight = weight;
        BucketName = string.IsNullOrWhiteSpace(bucketName) ? null : bucketName;
    }

    public string Name { get; init; }

    public IReadOnlyList<string> PresetNames { get; init; }

    public IReadOnlyList<string> RaceFilters { get; init; }

    public double Weight { get; init; }

    public string? BucketName { get; init; }

    /// <summary>
    /// Returns whether a rule's imported-race filters allow the supplied NPC.
    /// </summary>
    /// <param name="rule">Rule containing zero or more imported race strings.</param>
    /// <param name="npc">NPC whose <see cref="Npc.Race" /> text should be matched without game-data lookup.</param>
    /// <returns><see langword="true" /> when the rule has no race filters or one filter matches <see cref="Npc.Race" /> ordinal-ignore-case.</returns>
    public static bool MatchesRace(AssignmentStrategyRule rule, Npc npc)
    {
        if (rule is null) throw new ArgumentNullException(nameof(rule));
        if (npc is null) throw new ArgumentNullException(nameof(npc));

        return rule.RaceFilters.Count == 0 || rule.RaceFilters.Contains(npc.Race, RaceComparer);
    }

    private static string[] Normalize(IReadOnlyList<string>? values) =>
        values?.Where(value => value is not null).Select(value => value ?? string.Empty).ToArray()
        ?? Array.Empty<string>();
}
