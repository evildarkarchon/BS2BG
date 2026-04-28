using BS2BG.Core.Diagnostics;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;

namespace BS2BG.Core.Automation;

/// <summary>
/// Describes the request-scoped project state prepared for BodyGen automation after optional saved-strategy replay.
/// </summary>
/// <param name="Project">
/// Project instance callers may use for generation only when <see cref="IsBlocked" /> is false. When blocked, this
/// project may contain partial replay mutations and callers MUST NOT generate output from it.
/// </param>
/// <param name="Replayed">Whether a saved assignment strategy was applied for this request.</param>
/// <param name="StrategyKind">Kind of saved strategy that was replayed, or null when no replay occurred.</param>
/// <param name="AssignedCount">Number of NPC assignments mutated during replay.</param>
/// <param name="BlockedNpcs">NPC rows that could not be assigned because strategy rules left no eligible preset.</param>
public sealed record AssignmentStrategyReplayResult(
    ProjectModel Project,
    bool Replayed,
    AssignmentStrategyKind? StrategyKind,
    int AssignedCount,
    IReadOnlyList<AssignmentStrategyBlockedNpc> BlockedNpcs)
{
    /// <summary>
    /// Gets whether replay found fatal blocked NPC rows that make the working project unsafe for output generation.
    /// </summary>
    public bool IsBlocked => BlockedNpcs.Count > 0;
}

/// <summary>
/// Converts assignment-strategy replay failures into the validation contract consumed by headless automation surfaces.
/// </summary>
internal static class AssignmentStrategyReplayDiagnostics
{
    /// <summary>
    /// Creates blocker diagnostics for replay results that could not safely produce BodyGen output.
    /// </summary>
    /// <param name="replayResult">Replay result containing one or more blocked NPC rows.</param>
    /// <returns>A validation report whose blockers represent each replay-blocked NPC.</returns>
    public static ProjectValidationReport CreateBlockedValidationReport(AssignmentStrategyReplayResult replayResult)
    {
        if (replayResult is null) throw new ArgumentNullException(nameof(replayResult));

        return new ProjectValidationReport(replayResult.BlockedNpcs.Select(blocked =>
            new DiagnosticFinding(
                DiagnosticSeverity.Blocker,
                "Assignment strategy replay",
                "NPC has no eligible preset",
                "NPC '" + blocked.Npc.Name + "' cannot be assigned: " + blocked.Reason,
                targetKey: blocked.Npc.Name,
                actionHint: "Adjust saved assignment strategy rules or add an eligible preset for this NPC.",
                code: "ASSIGNMENT_REPLAY_BLOCKED",
                category: "Automation")));
    }
}
