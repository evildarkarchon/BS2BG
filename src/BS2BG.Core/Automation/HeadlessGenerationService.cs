using System.Text.Json;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;

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
    TemplateProfileCatalog profileCatalog)
{
    private readonly ProjectFileService projectFileService = projectFileService ?? throw new ArgumentNullException(nameof(projectFileService));
    private readonly TemplateGenerationService templateGenerationService = templateGenerationService ?? throw new ArgumentNullException(nameof(templateGenerationService));
    private readonly MorphGenerationService morphGenerationService = morphGenerationService ?? throw new ArgumentNullException(nameof(morphGenerationService));
    private readonly BodyGenIniExportWriter bodyGenIniExportWriter = bodyGenIniExportWriter ?? throw new ArgumentNullException(nameof(bodyGenIniExportWriter));
    private readonly BosJsonExportWriter bosJsonExportWriter = bosJsonExportWriter ?? throw new ArgumentNullException(nameof(bosJsonExportWriter));
    private readonly BosJsonExportPlanner bosJsonExportPlanner = bosJsonExportPlanner ?? throw new ArgumentNullException(nameof(bosJsonExportPlanner));
    private readonly TemplateProfileCatalog profileCatalog = profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog));

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

        var validationReport = ProjectValidationService.Validate(project, profileCatalog);
        if (validationReport.BlockerCount > 0)
            return new HeadlessGenerationResult(
                AutomationExitCode.ValidationBlocked,
                FormatValidationMessage(validationReport),
                Array.Empty<string>(),
                validationReport);

        var plannedTargets = PlanTargets(request, project).ToArray();
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
                var templates = templateGenerationService.GenerateTemplates(project.SliderPresets, profileCatalog, request.OmitRedundantSliders);
                var morphs = morphGenerationService.GenerateMorphs(project).Text;
                var result = bodyGenIniExportWriter.Write(request.OutputDirectory, templates, morphs);
                writtenFiles.Add(result.TemplatesPath);
                writtenFiles.Add(result.MorphsPath);
                ledger.Add(new FileWriteLedgerEntry(result.TemplatesPath, FileWriteOutcome.Written));
                ledger.Add(new FileWriteLedgerEntry(result.MorphsPath, FileWriteOutcome.Written));
            }

            if (IncludesBosJson(request.Intent))
            {
                var result = bosJsonExportWriter.Write(request.OutputDirectory, project.SliderPresets, profileCatalog);
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
            "Generation completed successfully.",
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

    private static string CreateIoFailureMessage(OutputIntent intent, IReadOnlyList<string> writtenFiles, Exception exception)
    {
        var message = "Generation failed while writing output: " + exception.Message;
        if (intent == OutputIntent.All && writtenFiles.Count > 0)
            message += Environment.NewLine + "BodyGen artifacts remain present after the later output failure.";
        return message;
    }
}
