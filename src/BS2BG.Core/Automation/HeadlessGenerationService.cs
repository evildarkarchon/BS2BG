using System.Text.Json;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Profiles;
using BS2BG.Core.Serialization;

namespace BS2BG.Core.Automation;

/// <summary>
/// Orchestrates headless project loading, validation, generation, overwrite preflight, and export through existing Core services.
/// </summary>
public sealed class HeadlessGenerationService(
    ProjectFileService projectFileService,
    OutputArtifactPlanner outputArtifactPlanner,
    OutputArtifactPreflight outputArtifactPreflight,
    OutputArtifactCommitter outputArtifactCommitter,
    AssignmentStrategyReplayService replayService,
    TemplateProfileCatalog profileCatalog)
{
    private readonly ProjectFileService projectFileService = projectFileService ?? throw new ArgumentNullException(nameof(projectFileService));
    private readonly OutputArtifactPlanner outputArtifactPlanner = outputArtifactPlanner ?? throw new ArgumentNullException(nameof(outputArtifactPlanner));
    private readonly OutputArtifactPreflight outputArtifactPreflight = outputArtifactPreflight ?? throw new ArgumentNullException(nameof(outputArtifactPreflight));
    private readonly OutputArtifactCommitter outputArtifactCommitter = outputArtifactCommitter ?? throw new ArgumentNullException(nameof(outputArtifactCommitter));
    private readonly AssignmentStrategyReplayService replayService = replayService ?? throw new ArgumentNullException(nameof(replayService));
    private readonly RequestScopedProfileCatalogComposer profileCatalogComposer = new(profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog)));

    /// <summary>
    /// Runs a complete headless generation request and returns a stable automation outcome without throwing expected user/input errors.
    /// </summary>
    /// <param name="request">Generation request parsed by the CLI or another automation caller.</param>
    /// <returns>Exit-code-oriented generation result with validation and write ledger details when applicable.</returns>
    public HeadlessGenerationResult Run(HeadlessGenerationRequest request)
    {
        if (request is null) throw new ArgumentNullException(nameof(request));

        ProjectModel project;
        try
        {
            if (!File.Exists(request.ProjectPath))
                return UsageError("Project file was not found: " + request.ProjectPath);

            project = projectFileService.Load(request.ProjectPath);
        }
        catch (JsonException exception)
        {
            return UsageError("Project file is not valid .jbs2bg JSON: " + exception.Message);
        }
        catch (IOException exception)
        {
            return UsageError("Project file could not be read: " + exception.Message);
        }
        catch (UnauthorizedAccessException exception)
        {
            return UsageError("Project file could not be read: " + exception.Message);
        }

        var profileResolution = ReferencedCustomProfileResolver.Resolve(project);
        var requestProfileCatalog = profileCatalogComposer.BuildForProject(profileResolution);
        var replayResult = replayService.PrepareForBodyGen(project, request.Intent, cloneBeforeReplay: true);
        if (replayResult.IsBlocked)
            return new HeadlessGenerationResult(
                AutomationExitCode.ValidationBlocked,
                FormatReplayBlockedMessage(replayResult),
                Array.Empty<string>(),
                AssignmentStrategyReplayDiagnostics.CreateBlockedValidationReport(replayResult));

        var generationProject = replayResult.Project;
        var validationReport = ProjectValidationService.Validate(generationProject, requestProfileCatalog);
        if (validationReport.BlockerCount > 0)
            return new HeadlessGenerationResult(
                AutomationExitCode.ValidationBlocked,
                FormatValidationMessage(validationReport),
                Array.Empty<string>(),
                validationReport);

        var missingProfiles = profileResolution.UnresolvedProfileNames
            .OrderBy(name => name, StringComparer.OrdinalIgnoreCase)
            .ToArray();
        if (missingProfiles.Length > 0)
            return new HeadlessGenerationResult(
                AutomationExitCode.ValidationBlocked,
                "Generation blocked because referenced custom profiles could not be resolved from embedded project data: "
                + string.Join(", ", missingProfiles),
                Array.Empty<string>(),
                validationReport);

        var outputPlan = outputArtifactPlanner.Plan(new OutputArtifactPlanningInput(
            generationProject,
            requestProfileCatalog,
            request.Intent,
            request.OmitRedundantSliders));
        var plannedTargets = outputPlan.Groups
            .SelectMany(group => outputArtifactPreflight.Preview(request.OutputDirectory, group).Files)
            .Select(file => file.Path)
            .ToArray();
        if (!request.Overwrite)
        {
            var existingTargets = plannedTargets.Where(File.Exists).ToArray();
            if (existingTargets.Length > 0)
                return new HeadlessGenerationResult(
                    AutomationExitCode.OverwriteRefused,
                    "Target files already exist. Enable overwrite to replace them. " + string.Join(Environment.NewLine, existingTargets),
                    Array.Empty<string>(),
                    validationReport,
                    existingTargets.Select(path => new FileWriteLedgerEntry(path, FileWriteOutcome.LeftUntouched, "Overwrite refused")).ToArray());
        }

        var writtenFiles = new List<string>();
        var ledger = new List<FileWriteLedgerEntry>();
        try
        {
            foreach (var group in outputPlan.Groups)
            {
                // Groups commit separately so a completed BodyGen pair remains when a later BoS batch fails.
                var result = outputArtifactCommitter.Commit(request.OutputDirectory, group);
                writtenFiles.AddRange(result.WrittenFiles);
                ledger.AddRange(result.WrittenFiles.Select(path => new FileWriteLedgerEntry(path, FileWriteOutcome.Written)));
            }
        }
        catch (AtomicWriteException exception)
        {
            ledger.AddRange(exception.Entries);
            return IoFailure(CreateIoFailureMessage(request.Intent, writtenFiles, exception), writtenFiles, validationReport, ledger);
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
        {
            return IoFailure(CreateIoFailureMessage(request.Intent, writtenFiles, exception), writtenFiles, validationReport, ledger);
        }

        return new HeadlessGenerationResult(
            AutomationExitCode.Success,
            CreateSuccessMessage(replayResult),
            writtenFiles,
            validationReport,
            ledger);
    }

    private static HeadlessGenerationResult UsageError(string message) => new(
        AutomationExitCode.UsageError,
        message,
        Array.Empty<string>(),
        null);

    private static HeadlessGenerationResult IoFailure(
        string message,
        IReadOnlyList<string> writtenFiles,
        ProjectValidationReport validationReport,
        IReadOnlyList<FileWriteLedgerEntry> ledger) => new(
        AutomationExitCode.IoFailure,
        message,
        writtenFiles,
        validationReport,
        ledger);

    private static string FormatValidationMessage(ProjectValidationReport report) => string.Join(
        Environment.NewLine,
        report.Findings.Select(finding => finding.Severity + ": " + finding.Title + " - " + finding.Detail));

    private static string CreateSuccessMessage(AssignmentStrategyReplayResult replayResult)
    {
        if (!replayResult.Replayed) return "Generation completed successfully.";

        return FormatReplaySuccessMessage(replayResult) + Environment.NewLine + "Generation completed successfully.";
    }

    private static string FormatReplaySuccessMessage(AssignmentStrategyReplayResult replayResult)
    {
        var message = "Assignment strategy replayed: " + replayResult.StrategyKind
                      + "; assigned NPCs: " + replayResult.AssignedCount.ToString(System.Globalization.CultureInfo.InvariantCulture)
                      + "; blocked NPCs: 0.";
        if (!ReplayWasSeeded(replayResult))
            message += " (unseeded strategy; assignments may vary between runs)";
        return message;
    }

    private static bool ReplayWasSeeded(AssignmentStrategyReplayResult replayResult) =>
        replayResult.Project.AssignmentStrategy?.Seed is not null;

    private static string FormatReplayBlockedMessage(AssignmentStrategyReplayResult replayResult)
    {
        var lines = new List<string>
        {
            "Assignment strategy replay blocked BodyGen generation because one or more NPCs have no eligible preset."
        };
        foreach (var blocked in replayResult.BlockedNpcs)
            lines.Add(FormatBlockedNpc(blocked));
        return string.Join(Environment.NewLine, lines);
    }

    private static string FormatBlockedNpc(AssignmentStrategyBlockedNpc blocked)
    {
        var npc = blocked.Npc;
        return "Blocked NPC: Mod=" + npc.Mod
               + "; Name=" + npc.Name
               + "; EditorId=" + npc.EditorId
               + "; Race=" + npc.Race
               + "; FormId=" + npc.FormId
               + "; Reason=" + blocked.Reason;
    }

    private static string CreateIoFailureMessage(OutputIntent intent, List<string> writtenFiles, Exception exception)
    {
        var message = "Generation failed while writing output: " + exception.Message;
        if (intent == OutputIntent.All && writtenFiles.Count > 0)
            message += Environment.NewLine + "BodyGen artifacts remain present after the later output failure.";
        return message;
    }
}
