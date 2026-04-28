using BS2BG.Core.Models;
using BS2BG.Core.Morphs;

namespace BS2BG.Core.Automation;

/// <summary>
/// Prepares automation-safe project state by replaying saved assignment strategies before BodyGen generation.
/// </summary>
public sealed class AssignmentStrategyReplayService
{
    private static readonly IReadOnlyList<AssignmentStrategyBlockedNpc> NoBlockedNpcs =
        Array.Empty<AssignmentStrategyBlockedNpc>();

    private readonly MorphAssignmentService morphAssignmentService;

    /// <summary>
    /// Creates a replay service that delegates strategy execution through the existing morph assignment seam.
    /// </summary>
    /// <param name="morphAssignmentService">Service used to apply saved assignment strategies.</param>
    public AssignmentStrategyReplayService(MorphAssignmentService morphAssignmentService)
    {
        this.morphAssignmentService = morphAssignmentService
                                      ?? throw new ArgumentNullException(nameof(morphAssignmentService));
    }

    /// <summary>
    /// Returns a request-scoped project prepared for BodyGen output, replaying a saved strategy when needed.
    /// </summary>
    /// <param name="sourceProject">Source project loaded by the caller.</param>
    /// <param name="intent">Requested automation output intent.</param>
    /// <param name="cloneBeforeReplay">Whether replay should happen on an isolated working clone.</param>
    /// <returns>Replay status, blocker details, and the project instance that is safe to consume when unblocked.</returns>
    public AssignmentStrategyReplayResult PrepareForBodyGen(
        ProjectModel sourceProject,
        OutputIntent intent,
        bool cloneBeforeReplay)
    {
        if (sourceProject is null) throw new ArgumentNullException(nameof(sourceProject));

        var workingProject = cloneBeforeReplay ? CloneProject(sourceProject) : sourceProject;
        if (!IncludesBodyGen(intent) || workingProject.AssignmentStrategy is null)
            return new AssignmentStrategyReplayResult(workingProject, false, null, 0, NoBlockedNpcs);

        var strategy = workingProject.AssignmentStrategy;
        var result = morphAssignmentService.ApplyStrategy(workingProject, strategy);
        return new AssignmentStrategyReplayResult(
            workingProject,
            true,
            strategy.Kind,
            result.AssignedCount,
            result.BlockedNpcs);
    }

    private static bool IncludesBodyGen(OutputIntent intent) => intent is OutputIntent.BodyGen or OutputIntent.All;

    private static ProjectModel CloneProject(ProjectModel sourceProject)
    {
        var workingProject = new ProjectModel();
        workingProject.ReplaceWith(sourceProject);
        return workingProject;
    }
}
