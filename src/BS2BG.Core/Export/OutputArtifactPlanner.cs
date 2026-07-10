using System.Globalization;
using System.Text;
using BS2BG.Core.Automation;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.Core.Export;

/// <summary>
/// Produces one authoritative, immutable plan of ordered paths and exact bytes for BodyGen and BoS output consumers.
/// </summary>
public sealed class OutputArtifactPlanner
{
    private static readonly Encoding Utf8NoBom = new UTF8Encoding(false);
    private static readonly HashSet<char> WindowsReservedFileNameCharacters = new("<>:\"/\\|?*");
    private static readonly HashSet<string> WindowsReservedDeviceNames = new(StringComparer.OrdinalIgnoreCase)
    {
        "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    };

    private readonly TemplateGenerationService templateGenerationService;
    private readonly MorphGenerationService morphGenerationService;

    /// <summary>
    /// Creates a planner over the existing parity-sensitive template and morph generation modules.
    /// </summary>
    /// <param name="templateGenerationService">Parity-sensitive template and BoS content generator.</param>
    /// <param name="morphGenerationService">Parity-sensitive BodyGen morph content generator.</param>
    public OutputArtifactPlanner(
        TemplateGenerationService templateGenerationService,
        MorphGenerationService morphGenerationService)
    {
        this.templateGenerationService = templateGenerationService
                                         ?? throw new ArgumentNullException(nameof(templateGenerationService));
        this.morphGenerationService = morphGenerationService
                                      ?? throw new ArgumentNullException(nameof(morphGenerationService));
    }

    /// <summary>
    /// Synchronously consumes generation-ready input and returns a detached plan whose paths and bytes cannot drift later.
    /// </summary>
    /// <param name="input">Prepared project state, effective catalog, output intent, and template option.</param>
    /// <returns>A complete immutable plan for the requested output families.</returns>
    /// <remarks>The returned plan retains no project, preset, profile catalog, or generation result references.</remarks>
    public OutputArtifactPlan Plan(OutputArtifactPlanningInput input) => Plan(input, CancellationToken.None);

    /// <summary>
    /// Synchronously consumes generation-ready input and returns a detached plan while observing cancellation between phases.
    /// </summary>
    /// <param name="input">Prepared project state, effective catalog, output intent, and template option.</param>
    /// <param name="cancellationToken">Cancels planning between generation phases or planned artifacts.</param>
    /// <returns>A complete immutable plan for the requested output families.</returns>
    /// <remarks>The returned plan retains no project, preset, profile catalog, or generation result references.</remarks>
    public OutputArtifactPlan Plan(OutputArtifactPlanningInput input, CancellationToken cancellationToken)
    {
        if (input is null) throw new ArgumentNullException(nameof(input));
        cancellationToken.ThrowIfCancellationRequested();

        var groups = new List<OutputArtifactCommitGroup>();
        if (input.Intent is OutputIntent.BodyGen or OutputIntent.All)
            groups.Add(PlanBodyGen(input, cancellationToken));

        if (input.Intent is OutputIntent.BosJson or OutputIntent.All)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var bosGroup = PlanBosJson(input, cancellationToken);
            if (bosGroup is not null) groups.Add(bosGroup);
        }

        return new OutputArtifactPlan(input.Intent, groups);
    }

    /// <summary>
    /// Freezes the CRLF-normalized templates and morphs texts as one ordered BodyGen pair.
    /// </summary>
    private OutputArtifactCommitGroup PlanBodyGen(
        OutputArtifactPlanningInput input,
        CancellationToken cancellationToken)
    {
        var templates = NormalizeCrLf(templateGenerationService.GenerateTemplates(
            input.Project.SliderPresets,
            input.ProfileCatalog,
            input.OmitRedundantSliders));
        cancellationToken.ThrowIfCancellationRequested();
        var morphs = NormalizeCrLf(morphGenerationService.GenerateMorphs(input.Project).Text);
        cancellationToken.ThrowIfCancellationRequested();

        return new OutputArtifactCommitGroup(OutputArtifactGroupKind.BodyGen, new[]
        {
            new OutputArtifact(OutputArtifactRole.BodyGenTemplates, "templates.ini", Utf8NoBom.GetBytes(templates)),
            new OutputArtifact(OutputArtifactRole.BodyGenMorphs, "morphs.ini", Utf8NoBom.GetBytes(morphs)),
        });
    }

    /// <summary>
    /// Materializes presets once, then freezes sorted, uniquely named LF-only BoS JSON bytes.
    /// </summary>
    private OutputArtifactCommitGroup? PlanBosJson(
        OutputArtifactPlanningInput input,
        CancellationToken cancellationToken)
    {
        var presets = input.Project.SliderPresets
            .OrderBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase)
            .ToArray();
        if (presets.Length == 0) return null;

        var usedFileNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var artifacts = new List<OutputArtifact>(presets.Length);
        foreach (var preset in presets)
        {
            cancellationToken.ThrowIfCancellationRequested();
            artifacts.Add(new OutputArtifact(
                OutputArtifactRole.BosPresetJson,
                GetUniqueFileName(SanitizeFileStem(preset.Name), usedFileNames),
                Utf8NoBom.GetBytes(templateGenerationService.PreviewBosJson(
                    preset,
                    input.ProfileCatalog.GetProfile(preset.ProfileName)))));
        }

        return new OutputArtifactCommitGroup(OutputArtifactGroupKind.BosJson, artifacts);
    }

    /// <summary>
    /// Canonicalizes mixed input newlines to the Java-compatible BodyGen CRLF contract.
    /// </summary>
    private static string NormalizeCrLf(string value) => value
        .Replace("\r\n", "\n", StringComparison.Ordinal)
        .Replace('\r', '\n')
        .Replace("\n", "\r\n", StringComparison.Ordinal);

    /// <summary>
    /// Produces a nonblank Windows-safe BoS filename stem without changing the in-memory preset name.
    /// </summary>
    private static string SanitizeFileStem(string name)
    {
        var builder = new StringBuilder((name ?? string.Empty).Length);
        foreach (var character in name ?? string.Empty)
            builder.Append(IsReservedFileNameCharacter(character) ? '_' : character);

        var sanitized = builder.ToString().Trim().TrimEnd('.', ' ');
        return sanitized.Length == 0 ? "preset" : SanitizeWindowsDeviceName(sanitized);
    }

    /// <summary>
    /// Recognizes control, platform-invalid, and explicitly Windows-reserved filename characters.
    /// </summary>
    private static bool IsReservedFileNameCharacter(char character) =>
        character < ' '
        || WindowsReservedFileNameCharacters.Contains(character)
        || Path.GetInvalidFileNameChars().Contains(character);

    /// <summary>
    /// Makes Windows device stems writable while preserving any suffix after the device token.
    /// </summary>
    private static string SanitizeWindowsDeviceName(string fileStem)
    {
        var extensionSeparator = fileStem.IndexOf('.');
        var deviceNameLength = extensionSeparator < 0 ? fileStem.Length : extensionSeparator;
        if (deviceNameLength == 0) return fileStem;

        var deviceName = fileStem.Substring(0, deviceNameLength);
        return WindowsReservedDeviceNames.Contains(deviceName)
            ? fileStem.Insert(deviceNameLength, "_")
            : fileStem;
    }

    /// <summary>
    /// Reserves a case-insensitively unique JSON filename using deterministic numeric suffixes.
    /// </summary>
    private static string GetUniqueFileName(string fileStem, HashSet<string> usedFileNames)
    {
        var candidate = fileStem + ".json";
        var suffix = 2;
        while (!usedFileNames.Add(candidate))
        {
            candidate = fileStem + " (" + suffix.ToString(CultureInfo.InvariantCulture) + ").json";
            suffix++;
        }

        return candidate;
    }
}
