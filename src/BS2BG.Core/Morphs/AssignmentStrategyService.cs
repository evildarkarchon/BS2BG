using BS2BG.Core.Models;

namespace BS2BG.Core.Morphs;

/// <summary>
/// Executes persisted assignment strategies against an explicit, caller-provided NPC scope.
/// </summary>
public sealed class AssignmentStrategyService
{
    private static readonly StringComparer StableComparer = StringComparer.OrdinalIgnoreCase;
    private readonly IRandomAssignmentProvider randomAssignmentProvider;

    /// <summary>
    /// Creates a strategy service using the supplied provider for non-seeded or test-controlled draws.
    /// </summary>
    /// <param name="randomAssignmentProvider">Provider used by provider-compatible strategy execution.</param>
    public AssignmentStrategyService(IRandomAssignmentProvider randomAssignmentProvider)
    {
        this.randomAssignmentProvider = randomAssignmentProvider ?? throw new ArgumentNullException(nameof(randomAssignmentProvider));
    }

    /// <summary>
    /// Computes per-NPC strategy eligibility without mutating project assignments.
    /// </summary>
    /// <param name="project">Project containing the stable preset collection.</param>
    /// <param name="strategy">Persisted strategy configuration to evaluate.</param>
    /// <param name="eligibleRows">Explicit caller scope; GUI callers can pass visible/selected rows while CLI callers pass all rows.</param>
    /// <returns>Eligibility entries and blocked NPCs that have no eligible preset after strategy rules.</returns>
    public static AssignmentStrategyEligibility ComputeEligibility(
        ProjectModel project,
        AssignmentStrategyDefinition strategy,
        IReadOnlyList<Npc> eligibleRows)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));
        if (strategy is null) throw new ArgumentNullException(nameof(strategy));
        if (eligibleRows is null) throw new ArgumentNullException(nameof(eligibleRows));

        var orderedRows = OrderEligibleRows(eligibleRows).ToArray();
        var entries = new List<AssignmentStrategyEligibilityEntry>(orderedRows.Length);
        var blocked = new List<AssignmentStrategyBlockedNpc>();

        for (var index = 0; index < orderedRows.Length; index++)
        {
            var npc = orderedRows[index];
            var presets = ResolveEligiblePresets(project, strategy, npc);
            if (presets.Length == 0)
                blocked.Add(new AssignmentStrategyBlockedNpc(npc, "No eligible preset after strategy rules."));

            entries.Add(new AssignmentStrategyEligibilityEntry(npc, presets));
        }

        return new AssignmentStrategyEligibility(entries, blocked);
    }

    /// <summary>
    /// Applies a persisted strategy using a deterministic seed provider when the strategy carries a seed.
    /// </summary>
    /// <param name="project">Project whose NPC assignments should be mutated.</param>
    /// <param name="strategy">Strategy definition to execute.</param>
    /// <returns>The number of assignments made and the NPC rows blocked by strategy eligibility.</returns>
    public static AssignmentStrategyResult Apply(ProjectModel project, AssignmentStrategyDefinition strategy) =>
        new AssignmentStrategyService(CreateProvider(strategy)).Apply(project, strategy, null);

    /// <summary>
    /// Applies a persisted strategy to the provided NPC scope without bypassing the random-provider seam.
    /// </summary>
    /// <param name="project">Project whose NPC assignments should be mutated.</param>
    /// <param name="strategy">Strategy definition to execute.</param>
    /// <param name="eligibleRows">Optional explicit caller scope; defaults to all morphed NPCs in stable order.</param>
    /// <returns>The number of assignments made and the NPC rows blocked by strategy eligibility.</returns>
    public AssignmentStrategyResult Apply(
        ProjectModel project,
        AssignmentStrategyDefinition strategy,
        IReadOnlyList<Npc>? eligibleRows = null)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));
        if (strategy is null) throw new ArgumentNullException(nameof(strategy));

        var scopedRows = eligibleRows ?? project.MorphedNpcs;
        var eligibility = ComputeEligibility(project, strategy, scopedRows);
        var assigned = 0;
        var roundRobinIndex = 0;

        foreach (var entry in eligibility.Entries)
        {
            if (entry.EligiblePresets.Count == 0) continue;

            var preset = strategy.Kind == AssignmentStrategyKind.RoundRobin
                ? entry.EligiblePresets[roundRobinIndex++ % entry.EligiblePresets.Count]
                : entry.EligiblePresets[SafeNextIndex(randomAssignmentProvider, entry.EligiblePresets.Count)];

            entry.Npc.ClearSliderPresets();
            entry.Npc.AddSliderPreset(preset);
            assigned++;
        }

        return new AssignmentStrategyResult(assigned, eligibility.BlockedNpcs);
    }

    private static IRandomAssignmentProvider CreateProvider(AssignmentStrategyDefinition strategy) =>
        strategy.Seed.HasValue
            ? new DeterministicAssignmentRandomProvider(strategy.Seed.Value)
            : new RandomAssignmentProvider();

    private static SliderPreset[] ResolveEligiblePresets(
        ProjectModel project,
        AssignmentStrategyDefinition strategy,
        Npc npc)
    {
        return strategy.Kind switch
        {
            AssignmentStrategyKind.RaceFilters => ResolveRulePresetUnion(project, strategy.Rules.Where(rule =>
                rule.RaceFilters.Count > 0 && AssignmentStrategyRule.MatchesRace(rule, npc))),
            _ => project.SliderPresets.ToArray()
        };
    }

    private static SliderPreset[] ResolveRulePresetUnion(ProjectModel project, IEnumerable<AssignmentStrategyRule> rules)
    {
        var names = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var rule in rules)
            foreach (var name in rule.PresetNames)
                names.Add(name);

        return project.SliderPresets.Where(preset => names.Contains(preset.Name)).ToArray();
    }

    private static IEnumerable<Npc> OrderEligibleRows(IReadOnlyList<Npc> eligibleRows) => eligibleRows
        .Select((npc, index) => new { Npc = npc, Index = index })
        .OrderBy(row => row.Npc.Mod, StableComparer)
        .ThenBy(row => row.Npc.EditorId, StableComparer)
        .ThenBy(row => row.Npc.FormId, StableComparer)
        .ThenBy(row => row.Npc.Name, StableComparer)
        .ThenBy(row => row.Index)
        .Select(row => row.Npc);

    private static int SafeNextIndex(IRandomAssignmentProvider provider, int count)
    {
        var index = provider.NextIndex(count);
        return (uint)index < (uint)count ? index : 0;
    }
}

/// <summary>
/// Non-mutating strategy eligibility result shared by strategy application and diagnostics.
/// </summary>
/// <param name="Entries">Eligible preset list for each scoped NPC in execution order.</param>
/// <param name="BlockedNpcs">NPCs that cannot be assigned because strategy rules leave no preset.</param>
public sealed record AssignmentStrategyEligibility(
    IReadOnlyList<AssignmentStrategyEligibilityEntry> Entries,
    IReadOnlyList<AssignmentStrategyBlockedNpc> BlockedNpcs);

/// <summary>
/// Eligibility details for one NPC row.
/// </summary>
/// <param name="Npc">NPC evaluated by the strategy.</param>
/// <param name="EligiblePresets">Project presets allowed for this NPC after strategy filtering.</param>
public sealed record AssignmentStrategyEligibilityEntry(Npc Npc, IReadOnlyList<SliderPreset> EligiblePresets);

/// <summary>
/// Result returned when applying a strategy to project NPC assignments.
/// </summary>
/// <param name="AssignedCount">Number of NPC assignments mutated.</param>
/// <param name="BlockedNpcs">NPCs left unchanged because no eligible preset exists.</param>
public sealed record AssignmentStrategyResult(int AssignedCount, IReadOnlyList<AssignmentStrategyBlockedNpc> BlockedNpcs);

/// <summary>
/// NPC row that could not be assigned by a strategy.
/// </summary>
/// <param name="Npc">Blocked NPC row.</param>
/// <param name="Reason">Human-readable reason suitable for diagnostics.</param>
public sealed record AssignmentStrategyBlockedNpc(Npc Npc, string Reason);
