using System.CommandLine;
using System.Text.Json;
using BS2BG.Core.Automation;
using BS2BG.Core.Bundling;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;

namespace BS2BG.Cli;

public static class Program
{
    /// <summary>
    /// Parses command-line input and returns System.CommandLine's script-friendly exit code.
    /// </summary>
    public static int Main(string[] args) => CreateRootCommand().Parse(args).Invoke();

    /// <summary>
    /// Creates the CLI command tree with the explicit generation options required by the automation contract.
    /// </summary>
    internal static RootCommand CreateRootCommand()
    {
        var projectOption = new Option<FileInfo>("--project")
        {
            Description = "Path to the .jbs2bg project file to generate from.",
            Required = true,
        };

        var outputOption = new Option<DirectoryInfo>("--output")
        {
            Description = "Directory where generated output files will be written.",
            Required = true,
        };

        var intentOption = new Option<string>("--intent")
        {
            Description = "Output intent to generate: bodygen, bos, or all.",
            Required = true,
        };
        intentOption.AcceptOnlyFromAmong("bodygen", "bos", "all");

        var overwriteOption = new Option<bool>("--overwrite")
        {
            Description = "Allow generation to replace existing output files.",
        };

        var omitRedundantSlidersOption = new Option<bool>("--omit-redundant-sliders")
        {
            Description = "Omit redundant slider values to match the GUI preference.",
        };

        var generateCommand = new Command("generate", "Generate BS2BG outputs from a saved project.")
        {
            projectOption,
            outputOption,
            intentOption,
            overwriteOption,
            omitRedundantSlidersOption,
        };

        var bundleProjectOption = new Option<FileInfo>("--project")
        {
            Description = "Path to the .jbs2bg project file to include in the portable bundle.",
            Required = true,
        };

        var bundleOption = new Option<FileInfo>("--bundle")
        {
            Description = "Destination portable project bundle .zip path.",
            Required = true,
        };

        var bundleIntentOption = new Option<string>("--intent")
        {
            Description = "Bundle output intent to include: bodygen, bos, or all.",
            Required = true,
        };
        bundleIntentOption.AcceptOnlyFromAmong("bodygen", "bos", "all");

        var bundleOverwriteOption = new Option<bool>("--overwrite")
        {
            Description = "Allow bundle creation to replace an existing zip file.",
        };

        var bundleCommand = new Command("bundle", "Create a portable project bundle zip from a saved project.")
        {
            bundleProjectOption,
            bundleOption,
            bundleIntentOption,
            bundleOverwriteOption,
        };

        generateCommand.SetAction(parseResult =>
        {
            var request = new HeadlessGenerationRequest(
                parseResult.GetValue(projectOption)!.FullName,
                parseResult.GetValue(outputOption)!.FullName,
                ParseOutputIntent(parseResult.GetValue(intentOption)),
                parseResult.GetValue(overwriteOption),
                parseResult.GetValue(omitRedundantSlidersOption));

            var result = CreateGenerationService().Run(request);
            WriteResult(result);
            return (int)result.ExitCode;
        });

        bundleCommand.SetAction(parseResult =>
        {
            var projectPath = parseResult.GetValue(bundleProjectOption)!.FullName;
            var bundlePath = parseResult.GetValue(bundleOption)!.FullName;
            var intent = ParseOutputIntent(parseResult.GetValue(bundleIntentOption));
            var overwrite = parseResult.GetValue(bundleOverwriteOption);

            ProjectModel project;
            var projectFileService = new ProjectFileService();
            try
            {
                if (!File.Exists(projectPath)) throw new FileNotFoundException("Project file was not found.", projectPath);
                project = projectFileService.Load(projectPath);
            }
            catch (Exception exception) when (IsProjectLoadException(exception))
            {
                WriteProjectLoadFailure(GetProjectLoadFailureMessage(exception));
                return (int)AutomationExitCode.UsageError;
            }

            try
            {
                var (service, request) = CreateBundleServiceAndRequest(projectFileService, project, projectPath, bundlePath, intent, overwrite);
                var preview = service.Preview(request);
                var result = service.Create(request);
                WriteBundleResult(result, preview);
                return (int)MapBundleOutcome(result.Outcome);
            }
            catch (Exception exception) when (IsExpectedBundleIoException(exception))
            {
                WriteBundleIoFailure();
                return (int)AutomationExitCode.IoFailure;
            }
        });

        var rootCommand = new RootCommand("bs2bg automation CLI");
        rootCommand.Subcommands.Add(generateCommand);
        rootCommand.Subcommands.Add(bundleCommand);
        return rootCommand;
    }

    /// <summary>
    /// Maps bundle service outcomes to the shared automation exit-code contract used by CLI commands.
    /// </summary>
    /// <param name="outcome">Bundle service outcome returned by preview or create.</param>
    /// <returns>A stable process-oriented automation exit code.</returns>
    public static AutomationExitCode MapBundleOutcome(PortableProjectBundleOutcome outcome) => outcome switch
    {
        PortableProjectBundleOutcome.Success => AutomationExitCode.Success,
        PortableProjectBundleOutcome.ValidationBlocked => AutomationExitCode.ValidationBlocked,
        PortableProjectBundleOutcome.MissingProfile => AutomationExitCode.ValidationBlocked,
        PortableProjectBundleOutcome.OverwriteRefused => AutomationExitCode.OverwriteRefused,
        PortableProjectBundleOutcome.IoFailure => AutomationExitCode.IoFailure,
        _ => AutomationExitCode.IoFailure,
    };

    /// <summary>
    /// Converts the parser-constrained intent token into the Core automation enum used by downstream services.
    /// </summary>
    private static OutputIntent ParseOutputIntent(string? intent) => intent switch
    {
        "bodygen" => OutputIntent.BodyGen,
        "bos" => OutputIntent.BosJson,
        "all" => OutputIntent.All,
        _ => OutputIntent.All,
    };

    /// <summary>
    /// Composes CLI-only Core service dependencies without referencing Avalonia or BS2BG.App.
    /// </summary>
    private static HeadlessGenerationService CreateGenerationService()
    {
        var templateGenerationService = new TemplateGenerationService();
        return new HeadlessGenerationService(
            new ProjectFileService(),
            templateGenerationService,
            new MorphGenerationService(),
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(templateGenerationService),
            new BosJsonExportPlanner(),
            new TemplateProfileCatalogFactory().Create());
    }

    /// <summary>
    /// Loads a project and composes the Core bundle service plus request without referencing Avalonia or App services.
    /// </summary>
    private static (PortableProjectBundleService Service, PortableProjectBundleRequest Request) CreateBundleServiceAndRequest(
        ProjectFileService projectFileService,
        ProjectModel project,
        string projectPath,
        string bundlePath,
        OutputIntent intent,
        bool overwrite)
    {
        var templateGenerationService = new TemplateGenerationService();
        var catalog = new TemplateProfileCatalogFactory().Create();
        var service = new PortableProjectBundleService(
            projectFileService,
            templateGenerationService,
            new MorphGenerationService(),
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(templateGenerationService),
            catalog,
            new DiagnosticReportTextFormatter());

        var saveContext = BuildSaveContext(catalog);
        var projectDirectory = Path.GetDirectoryName(Path.GetFullPath(projectPath));
        var bundleDirectory = Path.GetDirectoryName(Path.GetFullPath(bundlePath));
        var privateRoots = new[] { projectDirectory, bundleDirectory }
            .Where(path => !string.IsNullOrWhiteSpace(path))
            .Cast<string>()
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();
        var request = new PortableProjectBundleRequest(
            project,
            bundlePath,
            Path.GetFileName(projectPath),
            intent,
            overwrite,
            CreatedUtc: null,
            saveContext,
            privateRoots);

        return (service, request);
    }

    private static bool IsProjectLoadException(Exception exception) =>
        exception is IOException or UnauthorizedAccessException or JsonException or InvalidDataException
        || exception.InnerException is not null && IsProjectLoadException(exception.InnerException);

    private static bool IsExpectedBundleIoException(Exception exception) =>
        exception is IOException or UnauthorizedAccessException
        || exception.InnerException is not null && IsExpectedBundleIoException(exception.InnerException);

    /// <summary>
    /// Converts expected project-load failures to path-free user-facing text for automation stderr.
    /// </summary>
    private static string GetProjectLoadFailureMessage(Exception exception)
    {
        if (exception is FileNotFoundException or DirectoryNotFoundException)
            return "The project file was not found.";

        if (exception is JsonException or InvalidDataException)
            return "The project file is not valid BS2BG project JSON.";

        if (exception.InnerException is not null)
            return GetProjectLoadFailureMessage(exception.InnerException);

        return "The project file could not be read.";
    }

    private static void WriteProjectLoadFailure(string message)
    {
        Console.Error.WriteLine("Could not load project: " + message);
    }

    private static void WriteBundleIoFailure()
    {
        Console.Error.WriteLine("Bundle creation failed due to a file I/O error.");
    }

    /// <summary>
    /// Converts non-bundled catalog rows into save-context definitions for bundle profile-copy resolution.
    /// </summary>
    private static ProjectSaveContext BuildSaveContext(TemplateProfileCatalog catalog)
    {
        var profiles = catalog.Entries
            .Where(entry => entry.SourceKind != ProfileSourceKind.Bundled)
            .ToDictionary(
                entry => entry.Name,
                entry => new CustomProfileDefinition(
                    entry.Name,
                    entry.TemplateProfile.Name,
                    entry.TemplateProfile.SliderProfile,
                    entry.SourceKind,
                    entry.FilePath),
                StringComparer.OrdinalIgnoreCase);
        return new ProjectSaveContext(profiles);
    }

    /// <summary>
    /// Writes script-friendly result text to stdout for success and stderr for nonzero generation outcomes.
    /// </summary>
    /// <param name="result">Core generation outcome to print.</param>
    private static void WriteResult(HeadlessGenerationResult result)
    {
        var writer = result.ExitCode == AutomationExitCode.Success ? Console.Out : Console.Error;
        writer.WriteLine(result.Message);
        foreach (var path in result.WrittenFiles)
            writer.WriteLine(path);

        foreach (var entry in result.WriteLedger)
            writer.WriteLine(entry.Outcome + ": " + entry.Path + (entry.Detail is null ? string.Empty : " - " + entry.Detail));
    }

    /// <summary>
    /// Writes bundle results to stdout on success and stderr for validation, overwrite, missing-profile, or I/O blockers.
    /// </summary>
    private static void WriteBundleResult(PortableProjectBundleResult result, PortableProjectBundlePreview preview)
    {
        var writer = result.Outcome == PortableProjectBundleOutcome.Success ? Console.Out : Console.Error;
        switch (result.Outcome)
        {
            case PortableProjectBundleOutcome.Success:
                writer.WriteLine("Bundle created: " + result.BundlePath);
                writer.WriteLine("entries: " + result.Entries.Count.ToString(System.Globalization.CultureInfo.InvariantCulture));
                break;
            case PortableProjectBundleOutcome.OverwriteRefused:
                writer.WriteLine("Target files already exist. Enable overwrite to replace them.");
                writer.WriteLine(result.BundlePath);
                break;
            case PortableProjectBundleOutcome.ValidationBlocked:
                writer.WriteLine("Bundle validation blocked.");
                WriteValidationSummary(writer, result.ValidationReport);
                break;
            case PortableProjectBundleOutcome.MissingProfile:
                writer.WriteLine("Bundle blocked because referenced custom profiles could not be resolved from embedded project data or the local catalog.");
                WriteValidationSummary(writer, result.ValidationReport);
                break;
            case PortableProjectBundleOutcome.IoFailure:
                writer.WriteLine("Bundle creation failed due to a file I/O error.");
                writer.WriteLine(result.BundlePath);
                break;
        }

        foreach (var finding in result.PrivacyFindings.Count > 0 ? result.PrivacyFindings : preview.PrivacyFindings)
            writer.WriteLine(finding);
    }

    private static void WriteValidationSummary(TextWriter writer, ProjectValidationReport report)
    {
        foreach (var finding in report.Findings)
            writer.WriteLine(finding.Severity + ": " + finding.Title + " - " + finding.Detail);
    }
}
