using System.Text.Json;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;
using BS2BG.Core.Morphs;

namespace BS2BG.Core.Automation;

/// <summary>
/// Orchestrates headless project loading, validation, generation, overwrite preflight, and export through existing Core services.
/// </summary>
public sealed class HeadlessGenerationService(
    ProjectFileService projectFileService,
    TemplateGenerationService templateGenerationService,
    MorphGenerationService morphGenerationService,
    BodyGenIniExportWriter bodyGenIniExportWriter,
    BosJsonExportWriter bosJsonExportWriter,
    BosJsonExportPlanner bosJsonExportPlanner,
    AssignmentStrategyReplayService replayService,
    TemplateProfileCatalog profileCatalog)
{
    private readonly ProjectFileService projectFileService = projectFileService ?? throw new ArgumentNullException(nameof(projectFileService));
    private readonly TemplateGenerationService templateGenerationService = templateGenerationService ?? throw new ArgumentNullException(nameof(templateGenerationService));
    private readonly MorphGenerationService morphGenerationService = morphGenerationService ?? throw new ArgumentNullException(nameof(morphGenerationService));
    private readonly BodyGenIniExportWriter bodyGenIniExportWriter = bodyGenIniExportWriter ?? throw new ArgumentNullException(nameof(bodyGenIniExportWriter));
    private readonly BosJsonExportWriter bosJsonExportWriter = bosJsonExportWriter ?? throw new ArgumentNullException(nameof(bosJsonExportWriter));
    private readonly BosJsonExportPlanner bosJsonExportPlanner = bosJsonExportPlanner ?? throw new ArgumentNullException(nameof(bosJsonExportPlanner));
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

        var requestProfileCatalog = profileCatalogComposer.BuildForProject(project);
        var replayResult = replayService.PrepareForBodyGen(project, request.Intent, cloneBeforeReplay: true);
        if (replayResult.IsBlocked)
            return new HeadlessGenerationResult(
                AutomationExitCode.ValidationBlocked,
                FormatReplayBlockedMessage(replayResult),
                Array.Empty<string>(),
                ProjectValidationService.Validate(replayResult.Project, requestProfileCatalog));

        var generationProject = replayResult.Project;
        var validationReport = ProjectValidationService.Validate(generationProject, requestProfileCatalog);
        if (validationReport.BlockerCount > 0)
            return new HeadlessGenerationResult(
                AutomationExitCode.ValidationBlocked,
                FormatValidationMessage(validationReport),
                Array.Empty<string>(),
                validationReport);

        var missingProfiles = FindMissingReferencedCustomProfiles(generationProject, requestProfileCatalog).ToArray();
        if (missingProfiles.Length > 0)
            return new HeadlessGenerationResult(
                AutomationExitCode.ValidationBlocked,
                "Generation blocked because referenced custom profiles could not be resolved from embedded project data: "
                + string.Join(", ", missingProfiles),
                Array.Empty<string>(),
                validationReport);

        var plannedTargets = PlanTargets(request, generationProject).ToArray();
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
            if (IncludesBodyGen(request.Intent))
            {
                var templates = templateGenerationService.GenerateTemplates(generationProject.SliderPresets, requestProfileCatalog, request.OmitRedundantSliders);
                var morphs = morphGenerationService.GenerateMorphs(generationProject).Text;
                var result = bodyGenIniExportWriter.Write(request.OutputDirectory, templates, morphs);
                writtenFiles.Add(result.TemplatesPath);
                writtenFiles.Add(result.MorphsPath);
                ledger.Add(new FileWriteLedgerEntry(result.TemplatesPath, FileWriteOutcome.Written));
                ledger.Add(new FileWriteLedgerEntry(result.MorphsPath, FileWriteOutcome.Written));
            }

            if (IncludesBosJson(request.Intent))
            {
                var result = bosJsonExportWriter.Write(request.OutputDirectory, generationProject.SliderPresets, requestProfileCatalog);
                writtenFiles.AddRange(result.FilePaths);
                ledger.AddRange(result.FilePaths.Select(path => new FileWriteLedgerEntry(path, FileWriteOutcome.Written)));
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

    private IEnumerable<string> PlanTargets(HeadlessGenerationRequest request, ProjectModel project)
    {
        if (IncludesBodyGen(request.Intent))
        {
            yield return Path.Combine(request.OutputDirectory, "templates.ini");
            yield return Path.Combine(request.OutputDirectory, "morphs.ini");
        }

        if (IncludesBosJson(request.Intent))
            foreach (var path in bosJsonExportPlanner.Plan(request.OutputDirectory, project.SliderPresets))
                yield return path;
    }

    /// <summary>
    /// Finds non-bundled preset profile references that the request catalog cannot resolve before fallback generation can occur.
    /// </summary>
    /// <param name="project">Loaded project containing the profile references to check.</param>
    /// <param name="requestProfileCatalog">Catalog composed for this request after project load.</param>
    /// <returns>Distinct missing profile names ordered deterministically for stable CLI messages.</returns>
    private static IEnumerable<string> FindMissingReferencedCustomProfiles(
        ProjectModel project,
        TemplateProfileCatalog requestProfileCatalog) => project.SliderPresets
            .Select(preset => preset.ProfileName)
            .Where(name => !requestProfileCatalog.ContainsProfile(name))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(name => name, StringComparer.OrdinalIgnoreCase);

    private static bool IncludesBodyGen(OutputIntent intent) => intent is OutputIntent.BodyGen or OutputIntent.All;

    private static bool IncludesBosJson(OutputIntent intent) => intent is OutputIntent.BosJson or OutputIntent.All;

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

    private static string CreateIoFailureMessage(OutputIntent intent, IReadOnlyList<string> writtenFiles, Exception exception)
    {
        var message = "Generation failed while writing output: " + exception.Message;
        if (intent == OutputIntent.All && writtenFiles.Count > 0)
            message += Environment.NewLine + "BodyGen artifacts remain present after the later output failure.";
        return message;
    }
}
