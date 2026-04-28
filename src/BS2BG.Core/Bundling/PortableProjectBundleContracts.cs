using System.Text.Json;
using System.Text.Encodings.Web;
using System.Text.Json.Serialization;
using BS2BG.Core.Automation;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;

namespace BS2BG.Core.Bundling;

/// <summary>
/// Stable outcome contract used by CLI and GUI callers to map expected portable bundle failures without parsing exceptions.
/// </summary>
public enum PortableProjectBundleOutcome
{
    Success,
    ValidationBlocked,
    OverwriteRefused,
    MissingProfile,
    IoFailure,
}

/// <summary>
/// Captures all caller-selected inputs for previewing or creating a portable project bundle.
/// </summary>
/// <param name="Project">Project model to serialize, validate, and generate outputs from.</param>
/// <param name="BundlePath">Destination zip path for Create; Preview never writes this path.</param>
/// <param name="SourceProjectFileName">Original source project path or filename; manifests reduce it to a filename.</param>
/// <param name="Intent">Requested generated output family included in the bundle.</param>
/// <param name="Overwrite">Whether an existing destination zip may be replaced.</param>
/// <param name="CreatedUtc">Optional deterministic timestamp used for manifests and zip entry timestamps.</param>
/// <param name="SaveContext">Optional save context for referenced local custom profile embedding.</param>
/// <param name="PrivateRoots">Local roots that must be scrubbed from reports and support artifacts.</param>
public sealed record PortableProjectBundleRequest(
    ProjectModel Project,
    string BundlePath,
    string SourceProjectFileName,
    OutputIntent Intent,
    bool Overwrite,
    DateTimeOffset? CreatedUtc,
    ProjectSaveContext? SaveContext,
    IReadOnlyList<string> PrivateRoots);

/// <summary>
/// Describes a bundle plan without writing the destination zip, including path privacy findings.
/// </summary>
/// <param name="Outcome">Expected bundle outcome for the current project/request.</param>
/// <param name="Entries">Manifest entries that would be written when outcome is Success.</param>
/// <param name="ManifestJson">Deterministic manifest JSON for the planned entries.</param>
/// <param name="ValidationReport">Validation report used by the preview.</param>
/// <param name="PrivacyFindings">Privacy scan notes for manifest and report text.</param>
public sealed record PortableProjectBundlePreview(
    PortableProjectBundleOutcome Outcome,
    IReadOnlyList<BundleManifestEntry> Entries,
    string ManifestJson,
    ProjectValidationReport ValidationReport,
    IReadOnlyList<string> PrivacyFindings)
{
    /// <summary>
    /// Gets concise saved-assignment replay status for preview/report surfaces without overloading privacy findings.
    /// </summary>
    public string ReplayReportText { get; init; } = string.Empty;
}

/// <summary>
/// Describes the result of attempting to create a portable project bundle zip.
/// </summary>
/// <param name="Outcome">Stable status for success and expected failures.</param>
/// <param name="BundlePath">Destination zip path supplied by the caller.</param>
/// <param name="Entries">Zip entry names written on success, or planned/blocked entries otherwise.</param>
/// <param name="ManifestJson">Manifest JSON associated with the attempt.</param>
/// <param name="ValidationReport">Validation report associated with the attempt.</param>
/// <param name="PrivacyFindings">Privacy scan notes for manifest and report text.</param>
public sealed record PortableProjectBundleResult(
    PortableProjectBundleOutcome Outcome,
    string BundlePath,
    IReadOnlyList<string> Entries,
    string ManifestJson,
    ProjectValidationReport ValidationReport,
    IReadOnlyList<string> PrivacyFindings)
{
    /// <summary>
    /// Gets concise saved-assignment replay status for callers and CLI output without changing positional construction.
    /// </summary>
    public string ReplayReportText { get; init; } = string.Empty;
}

/// <summary>
/// Root manifest schema for Phase 5 portable bundle archives.
/// </summary>
/// <param name="SchemaVersion">Manifest schema version; Phase 5 writes 1.</param>
/// <param name="CreatedUtc">UTC timestamp used for manifest and zip entry timestamps.</param>
/// <param name="BundleSourceProjectName">Filename-only project source identity.</param>
/// <param name="Entries">Relative archive entries with SHA-256 checksums.</param>
public sealed record BundleManifest(
    int SchemaVersion,
    DateTimeOffset CreatedUtc,
    string BundleSourceProjectName,
    IReadOnlyList<BundleManifestEntry> Entries);

/// <summary>
/// Manifest row describing one normalized bundle entry and the checksum of its exact bytes.
/// </summary>
/// <param name="Path">Forward-slash bundle-relative path.</param>
/// <param name="Kind">Entry kind such as project, bodygen, bos, profile, report, manifest, or checksum.</param>
/// <param name="Sha256">Lowercase hexadecimal SHA-256 hash.</param>
public sealed record BundleManifestEntry(string Path, string Kind, string Sha256);

/// <summary>
/// Serializes portable bundle manifests with fixed camel-case JSON property names.
/// </summary>
public static class PortableProjectBundleManifestSerializer
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    /// <summary>
    /// Serializes the manifest after reducing the source project field to a filename and normalizing every entry path.
    /// </summary>
    /// <param name="manifest">Manifest to serialize.</param>
    /// <returns>Indented JSON text with LF newlines and no private source directory in the project name.</returns>
    public static string Serialize(BundleManifest manifest)
    {
        if (manifest is null) throw new ArgumentNullException(nameof(manifest));

        var safeManifest = manifest with
        {
            BundleSourceProjectName = Path.GetFileName(manifest.BundleSourceProjectName),
            CreatedUtc = manifest.CreatedUtc.ToUniversalTime(),
            Entries = manifest.Entries
                .Select(entry => entry with
                {
                    Path = BundlePathScrubber.NormalizeEntryPath(entry.Path),
                    Sha256 = entry.Sha256.ToLowerInvariant(),
                })
                .ToArray(),
        };

        var dto = new
        {
            safeManifest.SchemaVersion,
            CreatedUtc = safeManifest.CreatedUtc.ToString("O"),
            safeManifest.BundleSourceProjectName,
            safeManifest.Entries,
        };

        return JsonSerializer.Serialize(dto, JsonOptions).Replace("\r\n", "\n", StringComparison.Ordinal);
    }
}
