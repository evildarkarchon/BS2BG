using System.Diagnostics.CodeAnalysis;
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
    private readonly TemplateProfileCatalog profileCatalog;
    private readonly DiagnosticReportTextFormatter reportTextFormatter;
    private readonly string? tempRoot;

    /// <summary>
    /// Initializes a bundle service over existing Core services so bundle output cannot drift from project save/export behavior.
    /// </summary>
    public PortableProjectBundleService(
        ProjectFileService projectFileService,
        TemplateGenerationService templateGenerationService,
        MorphGenerationService morphGenerationService,
        BodyGenIniExportWriter bodyGenIniExportWriter,
        BosJsonExportWriter bosJsonExportWriter,
        TemplateProfileCatalog profileCatalog,
        DiagnosticReportTextFormatter reportTextFormatter,
        string? tempRoot = null)
    {
        this.projectFileService = projectFileService ?? throw new ArgumentNullException(nameof(projectFileService));
        this.templateGenerationService = templateGenerationService ?? throw new ArgumentNullException(nameof(templateGenerationService));
        this.morphGenerationService = morphGenerationService ?? throw new ArgumentNullException(nameof(morphGenerationService));
        this.bodyGenIniExportWriter = bodyGenIniExportWriter ?? throw new ArgumentNullException(nameof(bodyGenIniExportWriter));
        this.bosJsonExportWriter = bosJsonExportWriter ?? throw new ArgumentNullException(nameof(bosJsonExportWriter));
        this.profileCatalog = profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog));
        this.reportTextFormatter = reportTextFormatter ?? throw new ArgumentNullException(nameof(reportTextFormatter));
        this.tempRoot = tempRoot;
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
            plan.PrivacyFindings);
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
            var validationReport = ProjectValidationService.Validate(request.Project, profileCatalog);
            var reportText = reportTextFormatter.Format(validationReport, request.PrivateRoots);
            return new PortableProjectBundleResult(
                PortableProjectBundleOutcome.OverwriteRefused,
                request.BundlePath,
                Array.Empty<string>(),
                string.Empty,
                validationReport,
                FindPrivacyFindings(string.Empty, reportText));
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
                plan.PrivacyFindings);
        }

        try
        {
            var parent = Path.GetDirectoryName(Path.GetFullPath(request.BundlePath));
            if (!string.IsNullOrWhiteSpace(parent)) Directory.CreateDirectory(parent);
            if (File.Exists(request.BundlePath) && request.Overwrite) File.Delete(request.BundlePath);

            using var zipStream = File.Create(request.BundlePath);
            using var archive = new ZipArchive(zipStream, ZipArchiveMode.Create);
            var usedNames = new HashSet<string>(StringComparer.Ordinal);
            foreach (var entry in plan.Entries.OrderBy(entry => entry.Path, StringComparer.Ordinal))
            {
                if (!usedNames.Add(entry.Path)) throw new InvalidOperationException("Duplicate bundle entry path: " + entry.Path);

                var zipEntry = archive.CreateEntry(entry.Path, CompressionLevel.Optimal);
                zipEntry.LastWriteTime = plan.CreatedUtc.ToLocalTime();
                using var entryStream = zipEntry.Open();
                entryStream.Write(entry.Bytes, 0, entry.Bytes.Length);
            }

            return new PortableProjectBundleResult(
                PortableProjectBundleOutcome.Success,
                request.BundlePath,
                plan.Entries.Select(entry => entry.Path).ToArray(),
                plan.ManifestJson,
                plan.ValidationReport,
                plan.PrivacyFindings);
        }
        catch (Exception)
        {
            if (File.Exists(request.BundlePath)) TryDeleteFile(request.BundlePath);
            return new PortableProjectBundleResult(
                PortableProjectBundleOutcome.IoFailure,
                request.BundlePath,
                plan.ManifestEntries.Select(entry => entry.Path).ToArray(),
                plan.ManifestJson,
                plan.ValidationReport,
                plan.PrivacyFindings);
        }
    }

    private BundlePlan BuildPlan(PortableProjectBundleRequest request)
    {
        var createdUtc = (request.CreatedUtc ?? DateTimeOffset.UtcNow).ToUniversalTime();
        var validationReport = ProjectValidationService.Validate(request.Project, profileCatalog);
        var reportText = reportTextFormatter.Format(validationReport, request.PrivateRoots);
        if (validationReport.BlockerCount > 0)
            return BlockedPlan(PortableProjectBundleOutcome.ValidationBlocked, createdUtc, validationReport, reportText, request.PrivateRoots);

        var missingProfiles = FindMissingReferencedCustomProfiles(request.Project, request.SaveContext).ToArray();
        if (missingProfiles.Length > 0)
        {
            var missingText = reportText + "\nMissing custom profiles: " + string.Join(", ", missingProfiles) + "\n";
            return BlockedPlan(PortableProjectBundleOutcome.MissingProfile, createdUtc, validationReport, missingText, request.PrivateRoots);
        }

        var stagingDirectory = CreateStagingDirectory();
        try
        {
            var entries = new List<BundleContentEntry>
            {
                Entry("project/project.jbs2bg", "project", Utf8NoBom.GetBytes(projectFileService.SaveToString(request.Project, request.SaveContext))),
                Entry("reports/validation.txt", "report", Utf8NoBom.GetBytes(reportText)),
            };

            AddProfileEntries(entries, request.Project, request.SaveContext);
            AddGeneratedOutputEntries(entries, request, stagingDirectory);
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
            var privacyFindings = FindPrivacyFindings(manifestJson, reportText);
            return new BundlePlan(
                PortableProjectBundleOutcome.Success,
                createdUtc,
                entries,
                finalManifestEntries,
                manifestJson,
                validationReport,
                privacyFindings);
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
                FindPrivacyFindings(string.Empty, reportText));
        }
        finally
        {
            TryDeleteDirectory(stagingDirectory);
        }
    }

    private void AddGeneratedOutputEntries(List<BundleContentEntry> entries, PortableProjectBundleRequest request, string stagingDirectory)
    {
        if (request.Intent is OutputIntent.BodyGen or OutputIntent.All)
        {
            var bodyGenDirectory = Path.Combine(stagingDirectory, "bodygen");
            var templatesText = templateGenerationService.GenerateTemplates(request.Project.SliderPresets, profileCatalog, omitRedundantSliders: false);
            var morphsText = morphGenerationService.GenerateMorphs(request.Project).Text;
            bodyGenIniExportWriter.Write(bodyGenDirectory, templatesText, morphsText);
            entries.Add(FileEntry("outputs/bodygen/templates.ini", "bodygen", Path.Combine(bodyGenDirectory, "templates.ini")));
            entries.Add(FileEntry("outputs/bodygen/morphs.ini", "bodygen", Path.Combine(bodyGenDirectory, "morphs.ini")));
        }

        if (request.Intent is OutputIntent.BosJson or OutputIntent.All)
        {
            var bosDirectory = Path.Combine(stagingDirectory, "bos");
            var result = bosJsonExportWriter.Write(bosDirectory, request.Project.SliderPresets, profileCatalog);
            foreach (var filePath in result.FilePaths.OrderBy(Path.GetFileName, StringComparer.OrdinalIgnoreCase))
                entries.Add(FileEntry("outputs/bos/" + Path.GetFileName(filePath), "bos", filePath));
        }
    }

    private static void AddProfileEntries(List<BundleContentEntry> entries, ProjectModel project, ProjectSaveContext? saveContext)
    {
        foreach (var profile in ResolveReferencedCustomProfiles(project, saveContext))
        {
            var safeName = MakeSafeFileName(profile.Name) + ".json";
            entries.Add(Entry("profiles/" + safeName, "profile", Utf8NoBom.GetBytes(ProfileDefinitionService.ExportProfileJson(profile))));
        }
    }

    private static IEnumerable<string> FindMissingReferencedCustomProfiles(ProjectModel project, ProjectSaveContext? saveContext)
    {
        var resolved = ResolveReferencedCustomProfiles(project, saveContext)
            .Select(profile => profile.Name)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        return ReferencedCustomProfileNames(project)
            .Where(name => !resolved.Contains(name))
            .OrderBy(name => name, StringComparer.OrdinalIgnoreCase);
    }

    private static IEnumerable<CustomProfileDefinition> ResolveReferencedCustomProfiles(ProjectModel project, ProjectSaveContext? saveContext)
    {
        var projectProfiles = project.CustomProfiles
            .Where(profile => !IsBundledProfileName(profile.Name) && profile.SourceKind != ProfileSourceKind.Bundled)
            .ToDictionary(profile => profile.Name, StringComparer.OrdinalIgnoreCase);

        foreach (var name in ReferencedCustomProfileNames(project))
        {
            if (projectProfiles.TryGetValue(name, out var projectProfile))
            {
                yield return projectProfile;
                continue;
            }

            if (saveContext?.AvailableCustomProfilesByName.TryGetValue(name, out var contextProfile) == true
                && !IsBundledProfileName(contextProfile.Name)
                && contextProfile.SourceKind != ProfileSourceKind.Bundled)
            {
                yield return contextProfile;
            }
        }
    }

    private static IEnumerable<string> ReferencedCustomProfileNames(ProjectModel project) => project.SliderPresets
        .Select(preset => preset.ProfileName)
        .Where(name => !IsBundledProfileName(name))
        .Distinct(StringComparer.OrdinalIgnoreCase)
        .OrderBy(name => name, StringComparer.OrdinalIgnoreCase);

    private static bool IsBundledProfileName(string? name) =>
        string.Equals(name, ProjectProfileMapping.SkyrimCbbe, StringComparison.OrdinalIgnoreCase)
        || string.Equals(name, ProjectProfileMapping.SkyrimUunp, StringComparison.OrdinalIgnoreCase)
        || string.Equals(name, ProjectProfileMapping.Fallout4Cbbe, StringComparison.OrdinalIgnoreCase);

    private static BundlePlan BlockedPlan(
        PortableProjectBundleOutcome outcome,
        DateTimeOffset createdUtc,
        ProjectValidationReport validationReport,
        string reportText,
        IReadOnlyList<string> privateRoots)
    {
        var scrubbedReport = BundlePathScrubber.Scrub(reportText, privateRoots);
        return new BundlePlan(
            outcome,
            createdUtc,
            Array.Empty<BundleContentEntry>(),
            Array.Empty<BundleManifestEntry>(),
            string.Empty,
            validationReport,
            FindPrivacyFindings(string.Empty, scrubbedReport));
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

    private static string[] FindPrivacyFindings(string manifestJson, string reportText)
    {
        return BundlePathScrubber.IsPrivatePathLeak(manifestJson) || BundlePathScrubber.IsPrivatePathLeak(reportText)
            ? new[] { "Private path leak detected in bundle manifest or validation report." }
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

    private sealed record BundleContentEntry(string Path, string Kind, byte[] Bytes);

    private sealed record BundlePlan(
        PortableProjectBundleOutcome Outcome,
        DateTimeOffset CreatedUtc,
        IReadOnlyList<BundleContentEntry> Entries,
        IReadOnlyList<BundleManifestEntry> ManifestEntries,
        string ManifestJson,
        ProjectValidationReport ValidationReport,
        IReadOnlyList<string> PrivacyFindings);
}
