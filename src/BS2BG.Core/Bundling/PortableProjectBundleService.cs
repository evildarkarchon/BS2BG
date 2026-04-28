using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;
using BS2BG.Core.Automation;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;

namespace BS2BG.Core.Bundling;

/// <summary>
/// Creates path-scrubbed portable project bundle previews and zip artifacts from existing Core serialization, generation, and export services.
/// </summary>
[SuppressMessage("Performance", "CA1822:Mark members as static",
    Justification = "Bundling is exposed as an injectable service surface for CLI and App composition.")]
public sealed class PortableProjectBundleService
{
    private static readonly Encoding Utf8NoBom = new UTF8Encoding(false);

    private readonly ProjectFileService projectFileService;
    private readonly TemplateGenerationService templateGenerationService;
    private readonly MorphGenerationService morphGenerationService;
    private readonly BodyGenIniExportWriter bodyGenIniExportWriter;
    private readonly BosJsonExportWriter bosJsonExportWriter;
    private readonly AssignmentStrategyReplayService replayService;
    private readonly RequestScopedProfileCatalogComposer profileCatalogComposer;
    private readonly DiagnosticReportTextFormatter reportTextFormatter;
    private readonly string? tempRoot;
    private readonly Action<string, string> bundleCommitter;

    /// <summary>
    /// Initializes a bundle service over existing Core services so bundle output cannot drift from project save/export behavior.
    /// </summary>
    public PortableProjectBundleService(
        ProjectFileService projectFileService,
        TemplateGenerationService templateGenerationService,
        MorphGenerationService morphGenerationService,
        BodyGenIniExportWriter bodyGenIniExportWriter,
        BosJsonExportWriter bosJsonExportWriter,
        AssignmentStrategyReplayService replayService,
        TemplateProfileCatalog profileCatalog,
        DiagnosticReportTextFormatter reportTextFormatter,
        string? tempRoot = null)
        : this(
            projectFileService,
            templateGenerationService,
            morphGenerationService,
            bodyGenIniExportWriter,
            bosJsonExportWriter,
            replayService,
            profileCatalog,
            reportTextFormatter,
            tempRoot,
            CommitBundleFile)
    {
    }

    /// <summary>
    /// Initializes a bundle service with a deterministic final-commit seam for overwrite-safety tests.
    /// </summary>
    /// <param name="bundleCommitter">Commits a fully written temp zip to the final bundle path.</param>
    internal PortableProjectBundleService(
        ProjectFileService projectFileService,
        TemplateGenerationService templateGenerationService,
        MorphGenerationService morphGenerationService,
        BodyGenIniExportWriter bodyGenIniExportWriter,
        BosJsonExportWriter bosJsonExportWriter,
        AssignmentStrategyReplayService replayService,
        TemplateProfileCatalog profileCatalog,
        DiagnosticReportTextFormatter reportTextFormatter,
        string? tempRoot,
        Action<string, string> bundleCommitter)
    {
        this.projectFileService = projectFileService ?? throw new ArgumentNullException(nameof(projectFileService));
        this.templateGenerationService = templateGenerationService ?? throw new ArgumentNullException(nameof(templateGenerationService));
        this.morphGenerationService = morphGenerationService ?? throw new ArgumentNullException(nameof(morphGenerationService));
        this.bodyGenIniExportWriter = bodyGenIniExportWriter ?? throw new ArgumentNullException(nameof(bodyGenIniExportWriter));
        this.bosJsonExportWriter = bosJsonExportWriter ?? throw new ArgumentNullException(nameof(bosJsonExportWriter));
        this.replayService = replayService ?? throw new ArgumentNullException(nameof(replayService));
        profileCatalogComposer = new RequestScopedProfileCatalogComposer(profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog)));
        this.reportTextFormatter = reportTextFormatter ?? throw new ArgumentNullException(nameof(reportTextFormatter));
        this.tempRoot = tempRoot;
        this.bundleCommitter = bundleCommitter ?? throw new ArgumentNullException(nameof(bundleCommitter));
    }

    /// <summary>
    /// Plans the bundle contents and returns the manifest/report without creating the destination zip.
    /// </summary>
    /// <param name="request">Bundle request containing project, output intent, source filename, and privacy roots.</param>
    /// <returns>A deterministic preview describing planned entries or blocking status.</returns>
    public PortableProjectBundlePreview Preview(PortableProjectBundleRequest request)
    {
        if (request is null) throw new ArgumentNullException(nameof(request));

        var plan = BuildPlan(request);
        return new PortableProjectBundlePreview(
            plan.Outcome,
            plan.ManifestEntries,
            plan.ManifestJson,
            plan.ValidationReport,
            plan.PrivacyFindings)
        {
            ReplayReportText = plan.ReplayReportText,
        };
    }

    /// <summary>
    /// Creates the portable project bundle zip unless validation, overwrite, missing profile, or I/O failures block creation.
    /// </summary>
    /// <param name="request">Bundle request containing project, destination path, output intent, and privacy roots.</param>
    /// <returns>A result with the stable outcome and manifest/report context.</returns>
    public PortableProjectBundleResult Create(PortableProjectBundleRequest request)
    {
        if (request is null) throw new ArgumentNullException(nameof(request));

        if (File.Exists(request.BundlePath) && !request.Overwrite)
        {
            var validationReport = ProjectValidationService.Validate(
                request.Project,
                profileCatalogComposer.BuildForProject(request.Project, request.SaveContext));
            var reportText = reportTextFormatter.Format(validationReport, request.PrivateRoots);
            return new PortableProjectBundleResult(
                PortableProjectBundleOutcome.OverwriteRefused,
                request.BundlePath,
                Array.Empty<string>(),
                string.Empty,
                validationReport,
                FindPrivacyFindings(string.Empty, reportText, string.Empty));
        }

        var plan = BuildPlan(request);
        if (plan.Outcome != PortableProjectBundleOutcome.Success)
        {
            return new PortableProjectBundleResult(
                plan.Outcome,
                request.BundlePath,
                plan.ManifestEntries.Select(entry => entry.Path).ToArray(),
                plan.ManifestJson,
                plan.ValidationReport,
                plan.PrivacyFindings)
            {
                ReplayReportText = plan.ReplayReportText,
            };
        }

        var finalPath = Path.GetFullPath(request.BundlePath);
        var parent = Path.GetDirectoryName(finalPath);
        if (string.IsNullOrWhiteSpace(parent)) parent = Directory.GetCurrentDirectory();
        var tempPath = Path.Combine(parent, "." + Path.GetFileName(finalPath) + "." + Guid.NewGuid().ToString("N") + ".tmp");

        try
        {
            Directory.CreateDirectory(parent);

            using (var zipStream = File.Create(tempPath))
            using (var archive = new ZipArchive(zipStream, ZipArchiveMode.Create))
            {
                var usedNames = new HashSet<string>(StringComparer.Ordinal);
                foreach (var entry in plan.Entries.OrderBy(entry => entry.Path, StringComparer.Ordinal))
                {
                    if (!usedNames.Add(entry.Path)) throw new InvalidOperationException("Duplicate bundle entry path: " + entry.Path);

                    var zipEntry = archive.CreateEntry(entry.Path, CompressionLevel.Optimal);
                    zipEntry.LastWriteTime = plan.CreatedUtc.ToLocalTime();
                    using var entryStream = zipEntry.Open();
                    entryStream.Write(entry.Bytes, 0, entry.Bytes.Length);
                }
            }

            bundleCommitter(tempPath, finalPath);

            return new PortableProjectBundleResult(
                PortableProjectBundleOutcome.Success,
                request.BundlePath,
                plan.Entries.Select(entry => entry.Path).ToArray(),
                plan.ManifestJson,
                plan.ValidationReport,
                plan.PrivacyFindings)
            {
                ReplayReportText = plan.ReplayReportText,
            };
        }
        catch (Exception)
        {
            if (File.Exists(tempPath)) TryDeleteFile(tempPath);
            return new PortableProjectBundleResult(
                PortableProjectBundleOutcome.IoFailure,
                request.BundlePath,
                plan.ManifestEntries.Select(entry => entry.Path).ToArray(),
                plan.ManifestJson,
                plan.ValidationReport,
                plan.PrivacyFindings)
            {
                ReplayReportText = plan.ReplayReportText,
            };
        }
    }

    private BundlePlan BuildPlan(PortableProjectBundleRequest request)
    {
        var createdUtc = (request.CreatedUtc ?? DateTimeOffset.UtcNow).ToUniversalTime();
        var requestProfileCatalog = profileCatalogComposer.BuildForProject(request.Project, request.SaveContext);
        var replayResult = replayService.PrepareForBodyGen(request.Project, request.Intent, cloneBeforeReplay: true);
        var replayReportText = FormatReplayReport(replayResult, request, request.PrivateRoots);
        if (replayResult.IsBlocked)
        {
            var blockedValidationReport = ProjectValidationService.Validate(request.Project, requestProfileCatalog);
            return BlockedPlan(
                PortableProjectBundleOutcome.ValidationBlocked,
                createdUtc,
                blockedValidationReport,
                replayReportText,
                replayReportText,
                request.PrivateRoots);
        }

        var outputProject = replayResult.Project;
        var validationReport = ProjectValidationService.Validate(outputProject, requestProfileCatalog);
        var reportText = reportTextFormatter.Format(validationReport, request.PrivateRoots);
        if (validationReport.BlockerCount > 0)
            return BlockedPlan(PortableProjectBundleOutcome.ValidationBlocked, createdUtc, validationReport, reportText, replayReportText, request.PrivateRoots);

        var bundleProfiles = profileCatalogComposer.ResolveReferencedCustomProfiles(request.Project, request.SaveContext).ToArray();
        var missingProfiles = FindMissingReferencedCustomProfiles(request.Project, requestProfileCatalog).ToArray();
        if (missingProfiles.Length > 0)
        {
            var missingText = reportText + "\nMissing custom profiles: " + string.Join(", ", missingProfiles) + "\n";
            return BlockedPlan(PortableProjectBundleOutcome.MissingProfile, createdUtc, validationReport, missingText, replayReportText, request.PrivateRoots);
        }

        var stagingDirectory = CreateStagingDirectory();
        try
        {
            var entries = new List<BundleContentEntry>
            {
                Entry("project/project.jbs2bg", "project", Utf8NoBom.GetBytes(projectFileService.SaveToString(request.Project, request.SaveContext))),
                Entry("reports/validation.txt", "report", Utf8NoBom.GetBytes(reportText)),
            };
            if (replayResult.Replayed)
                entries.Add(Entry("reports/replay.txt", "report", Utf8NoBom.GetBytes(replayReportText)));

            AddProfileEntries(entries, bundleProfiles);
            AddGeneratedOutputEntries(entries, request, outputProject, stagingDirectory, requestProfileCatalog);
            RejectDuplicateEntries(entries.Select(entry => entry.Path));

            entries = entries.OrderBy(entry => entry.Path, StringComparer.Ordinal).ToList();
            var manifestEntries = entries.Select(entry => new BundleManifestEntry(entry.Path, entry.Kind, ComputeSha256(entry.Bytes))).ToList();
            var manifestJson = PortableProjectBundleManifestSerializer.Serialize(new BundleManifest(
                1,
                createdUtc,
                Path.GetFileName(request.SourceProjectFileName),
                manifestEntries));
            var checksumText = BuildChecksums(manifestEntries, manifestJson);

            entries.Add(Entry("manifest.json", "manifest", Utf8NoBom.GetBytes(manifestJson)));
            entries.Add(Entry("SHA256SUMS.txt", "checksum", Utf8NoBom.GetBytes(checksumText)));
            entries = entries.OrderBy(entry => entry.Path, StringComparer.Ordinal).ToList();

            var finalManifestEntries = entries.Select(entry => new BundleManifestEntry(entry.Path, entry.Kind, ComputeSha256(entry.Bytes))).ToArray();
            var privacyFindings = FindPrivacyFindings(manifestJson, reportText, replayReportText);
            return new BundlePlan(
                PortableProjectBundleOutcome.Success,
                createdUtc,
                entries,
                finalManifestEntries,
                manifestJson,
                validationReport,
                privacyFindings,
                replayReportText);
        }
        catch (Exception)
        {
            return new BundlePlan(
                PortableProjectBundleOutcome.IoFailure,
                createdUtc,
                Array.Empty<BundleContentEntry>(),
                Array.Empty<BundleManifestEntry>(),
                string.Empty,
                validationReport,
                FindPrivacyFindings(string.Empty, reportText, replayReportText),
                replayReportText);
        }
        finally
        {
            TryDeleteDirectory(stagingDirectory);
        }
    }

    private void AddGeneratedOutputEntries(
        List<BundleContentEntry> entries,
        PortableProjectBundleRequest request,
        ProjectModel outputProject,
        string stagingDirectory,
        TemplateProfileCatalog requestProfileCatalog)
    {
        if (request.Intent is OutputIntent.BodyGen or OutputIntent.All)
        {
            var bodyGenDirectory = Path.Combine(stagingDirectory, "bodygen");
            var templatesText = templateGenerationService.GenerateTemplates(outputProject.SliderPresets, requestProfileCatalog, omitRedundantSliders: false);
            var morphsText = morphGenerationService.GenerateMorphs(outputProject).Text;
            bodyGenIniExportWriter.Write(bodyGenDirectory, templatesText, morphsText);
            entries.Add(FileEntry("outputs/bodygen/templates.ini", "bodygen", Path.Combine(bodyGenDirectory, "templates.ini")));
            entries.Add(FileEntry("outputs/bodygen/morphs.ini", "bodygen", Path.Combine(bodyGenDirectory, "morphs.ini")));
        }

        if (request.Intent is OutputIntent.BosJson or OutputIntent.All)
        {
            var bosDirectory = Path.Combine(stagingDirectory, "bos");
            var result = bosJsonExportWriter.Write(bosDirectory, outputProject.SliderPresets, requestProfileCatalog);
            foreach (var filePath in result.FilePaths.OrderBy(Path.GetFileName, StringComparer.OrdinalIgnoreCase))
                entries.Add(FileEntry("outputs/bos/" + Path.GetFileName(filePath), "bos", filePath));
        }
    }

    private static void AddProfileEntries(List<BundleContentEntry> entries, IEnumerable<CustomProfileDefinition> bundleProfiles)
    {
        var usedNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var profile in bundleProfiles)
        {
            var safeName = GetUniqueProfileFileName(MakeSafeFileName(profile.Name), usedNames);
            entries.Add(Entry("profiles/" + safeName, "profile", Utf8NoBom.GetBytes(ProfileDefinitionService.ExportProfileJson(profile))));
        }
    }

    /// <summary>
    /// Produces deterministic profile entry filenames when distinct profile names sanitize to the same archive path.
    /// </summary>
    /// <param name="stem">Sanitized filename stem without the JSON extension.</param>
    /// <param name="usedNames">Case-insensitive set of profile filenames already reserved in this bundle.</param>
    /// <returns>A unique JSON filename for the profile entry.</returns>
    private static string GetUniqueProfileFileName(string stem, HashSet<string> usedNames)
    {
        var candidate = stem + ".json";
        var suffix = 2;
        while (!usedNames.Add(candidate))
        {
            candidate = stem + " (" + suffix.ToString(CultureInfo.InvariantCulture) + ").json";
            suffix++;
        }

        return candidate;
    }

    private static IEnumerable<string> FindMissingReferencedCustomProfiles(
        ProjectModel project,
        TemplateProfileCatalog requestProfileCatalog) => project.SliderPresets
            .Select(preset => preset.ProfileName)
            .Where(name => !requestProfileCatalog.ContainsProfile(name))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(name => name, StringComparer.OrdinalIgnoreCase);

    private static BundlePlan BlockedPlan(
        PortableProjectBundleOutcome outcome,
        DateTimeOffset createdUtc,
        ProjectValidationReport validationReport,
        string reportText,
        string replayReportText,
        IReadOnlyList<string> privateRoots)
    {
        var scrubbedReport = BundlePathScrubber.Scrub(reportText, privateRoots);
        var scrubbedReplayReport = BundlePathScrubber.Scrub(replayReportText, privateRoots);
        return new BundlePlan(
            outcome,
            createdUtc,
            Array.Empty<BundleContentEntry>(),
            Array.Empty<BundleManifestEntry>(),
            string.Empty,
            validationReport,
            FindPrivacyFindings(string.Empty, scrubbedReport, scrubbedReplayReport),
            scrubbedReplayReport);
    }

    /// <summary>
    /// Formats the explicit replay-report contract used by previews, CLI output, and optional bundle report entries.
    /// </summary>
    private static string FormatReplayReport(
        AssignmentStrategyReplayResult replayResult,
        PortableProjectBundleRequest request,
        IReadOnlyList<string> privateRoots)
    {
        if (!replayResult.Replayed)
            return request.Project.AssignmentStrategy is null
                ? "No saved assignment strategy; generated from existing project assignments."
                : "Saved assignment strategy was not replayed because the output intent does not include BodyGen.";

        if (!replayResult.IsBlocked)
            return "Assignment strategy replayed: " + replayResult.StrategyKind + "; assigned NPCs: "
                   + replayResult.AssignedCount.ToString(CultureInfo.InvariantCulture) + "; blocked NPCs: 0.";

        var builder = new StringBuilder();
        builder.AppendLine("Assignment strategy replay blocked.");
        builder.AppendLine("Strategy: " + replayResult.StrategyKind);
        builder.AppendLine("Assigned NPCs before blocker: " + replayResult.AssignedCount.ToString(CultureInfo.InvariantCulture));
        builder.AppendLine("Blocked NPCs:");
        foreach (var blocked in replayResult.BlockedNpcs)
        {
            var npc = blocked.Npc;
            builder.Append("- Mod=").Append(npc.Mod)
                .Append("; Name=").Append(npc.Name)
                .Append("; EditorId=").Append(npc.EditorId)
                .Append("; Race=").Append(npc.Race)
                .Append("; FormId=").Append(npc.FormId)
                .Append("; Reason=").AppendLine(blocked.Reason);
        }

        return BundlePathScrubber.Scrub(builder.ToString().TrimEnd(), privateRoots);
    }

    private static string BuildChecksums(IEnumerable<BundleManifestEntry> manifestEntries, string manifestJson)
    {
        var rows = manifestEntries
            .Select(entry => entry.Sha256 + "  " + entry.Path)
            .Append(ComputeSha256(Utf8NoBom.GetBytes(manifestJson)) + "  manifest.json")
            .OrderBy(row => row, StringComparer.Ordinal);
        return string.Join("\n", rows) + "\n";
    }

    private static BundleContentEntry FileEntry(string entryPath, string kind, string sourcePath) =>
        Entry(entryPath, kind, File.ReadAllBytes(sourcePath));

    private static BundleContentEntry Entry(string path, string kind, byte[] bytes) =>
        new(BundlePathScrubber.NormalizeEntryPath(path), kind, bytes);

    private static string ComputeSha256(byte[] bytes)
    {
        using var sha256 = SHA256.Create();
        return ToLowerHex(sha256.ComputeHash(bytes));
    }

    private static string ToLowerHex(byte[] bytes)
    {
        var builder = new StringBuilder(bytes.Length * 2);
        foreach (var value in bytes) builder.Append(value.ToString("x2", System.Globalization.CultureInfo.InvariantCulture));
        return builder.ToString();
    }

    private static string[] FindPrivacyFindings(string manifestJson, string reportText, string replayReportText)
    {
        return BundlePathScrubber.IsPrivatePathLeak(manifestJson)
               || BundlePathScrubber.IsPrivatePathLeak(reportText)
               || BundlePathScrubber.IsPrivatePathLeak(replayReportText)
            ? new[] { "Private path leak detected in bundle manifest, validation report, or replay report." }
            : new[] { "No private path leaks detected." };
    }

    private string CreateStagingDirectory()
    {
        var root = tempRoot ?? Path.GetTempPath();
        Directory.CreateDirectory(root);
        var path = Path.Combine(root, "bs2bg-bundle-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(path);
        return path;
    }

    private static void RejectDuplicateEntries(IEnumerable<string> paths)
    {
        var seen = new HashSet<string>(StringComparer.Ordinal);
        foreach (var path in paths)
            if (!seen.Add(path))
                throw new InvalidOperationException("Duplicate bundle entry path: " + path);
    }

    private static string MakeSafeFileName(string name)
    {
        var invalid = Path.GetInvalidFileNameChars().ToHashSet();
        var safe = new string(name.Select(ch => invalid.Contains(ch) ? '_' : ch).ToArray()).Trim();
        return safe.Length == 0 ? "profile" : safe;
    }

    private static void TryDeleteDirectory(string path)
    {
        try
        {
            if (Directory.Exists(path)) Directory.Delete(path, recursive: true);
        }
        catch (Exception)
        {
            // Cleanup is best-effort so a secondary temp deletion problem does not mask the primary bundle outcome.
        }
    }

    private static void TryDeleteFile(string path)
    {
        try
        {
            File.Delete(path);
        }
        catch (Exception)
        {
            // Cleanup is best-effort so a secondary partial-zip deletion problem does not mask the primary bundle outcome.
        }
    }

    private static void CommitBundleFile(string tempPath, string finalPath)
    {
        if (File.Exists(finalPath))
            File.Replace(tempPath, finalPath, destinationBackupFileName: null);
        else
            File.Move(tempPath, finalPath);
    }

    private sealed record BundleContentEntry(string Path, string Kind, byte[] Bytes);

    private sealed record BundlePlan(
        PortableProjectBundleOutcome Outcome,
        DateTimeOffset CreatedUtc,
        IReadOnlyList<BundleContentEntry> Entries,
        IReadOnlyList<BundleManifestEntry> ManifestEntries,
        string ManifestJson,
        ProjectValidationReport ValidationReport,
        IReadOnlyList<string> PrivacyFindings,
        string ReplayReportText);
}
