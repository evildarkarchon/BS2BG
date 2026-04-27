using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.Core.Diagnostics;

/// <summary>
/// Describes the explicit recovery actions available for an unresolved custom profile reference.
/// </summary>
public enum ProfileRecoveryActionKind
{
    /// <summary>
    /// Import a custom profile JSON whose internal display name exactly matches the missing reference.
    /// </summary>
    ImportMatchingProfile,

    /// <summary>
    /// Activate the project-embedded copy that matches the missing reference.
    /// </summary>
    UseProjectEmbeddedCopy,

    /// <summary>
    /// Change affected presets to a currently installed profile.
    /// </summary>
    RemapToInstalledProfile,

    /// <summary>
    /// Leave the saved profile name unresolved while continuing with visible fallback calculation.
    /// </summary>
    KeepUnresolvedForNow,
}

/// <summary>
/// Read-only recovery diagnostic for a project profile reference that is absent from the active catalog.
/// </summary>
public sealed record ProfileRecoveryDiagnostic(
    DiagnosticSeverity Severity,
    string Code,
    string Category,
    string MissingProfileName,
    string FallbackCalculationProfileName,
    IReadOnlyList<string> AffectedPresetNames,
    string Detail,
    IReadOnlyList<ProfileRecoveryActionKind> Actions);

/// <summary>
/// Finds unresolved custom profile references and reports neutral recovery options without mutating project or catalog state.
/// </summary>
public sealed class ProfileRecoveryDiagnosticsService
{
    public const string MissingCustomProfileCode = "MissingCustomProfile";
    public const string ProfileRecoveryCategory = "ProfileRecovery";

    /// <summary>
    /// Reports whether an imported profile can explicitly resolve a missing reference by internal display-name identity only.
    /// </summary>
    /// <param name="missingProfileName">Saved project profile name that is absent from the active catalog.</param>
    /// <param name="importedProfile">Validated profile definition being considered for recovery.</param>
    /// <returns><see langword="true"/> when <paramref name="importedProfile"/> has the same internal name ignoring case.</returns>
    public static bool CanResolveMissingReference(string missingProfileName, CustomProfileDefinition importedProfile)
    {
        if (missingProfileName is null) throw new ArgumentNullException(nameof(missingProfileName));
        if (importedProfile is null) throw new ArgumentNullException(nameof(importedProfile));

        return string.Equals(missingProfileName, importedProfile.Name, StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>
    /// Analyzes project preset profile names against the active catalog and embedded project profile copies.
    /// </summary>
    /// <param name="project">Project whose saved preset profile names should be inspected.</param>
    /// <param name="catalog">Active runtime catalog containing bundled and locally available custom profiles.</param>
    /// <returns>Neutral recovery diagnostics for profile names absent from the active catalog.</returns>
    public IReadOnlyList<ProfileRecoveryDiagnostic> Analyze(ProjectModel project, TemplateProfileCatalog catalog)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));
        if (catalog is null) throw new ArgumentNullException(nameof(catalog));

        var fallbackProfileName = catalog.DefaultProfile.Name;
        var embeddedNames = new HashSet<string>(
            project.CustomProfiles.Select(profile => profile.Name),
            StringComparer.OrdinalIgnoreCase);
        var diagnostics = new List<ProfileRecoveryDiagnostic>();

        foreach (var group in project.SliderPresets
                     .Where(preset => !string.IsNullOrWhiteSpace(preset.ProfileName)
                                      && !catalog.ContainsProfile(preset.ProfileName))
                     .GroupBy(preset => preset.ProfileName, StringComparer.OrdinalIgnoreCase)
                     .OrderBy(group => group.Key, StringComparer.OrdinalIgnoreCase))
        {
            var missingProfileName = group.Key;
            var hasEmbeddedCopy = embeddedNames.Contains(missingProfileName);
            diagnostics.Add(new ProfileRecoveryDiagnostic(
                DiagnosticSeverity.Info,
                MissingCustomProfileCode,
                ProfileRecoveryCategory,
                missingProfileName,
                fallbackProfileName,
                group.Select(preset => preset.Name).OrderBy(name => name, StringComparer.OrdinalIgnoreCase).ToArray(),
                CreateDetail(missingProfileName, fallbackProfileName, hasEmbeddedCopy),
                CreateActions(hasEmbeddedCopy)));
        }

        return diagnostics;
    }

    private static string CreateDetail(string missingProfileName, string fallbackProfileName, bool hasEmbeddedCopy)
    {
        var detail = "Project references custom profile '" + missingProfileName
            + "', but it is not active in the current profile catalog. BS2BG can continue with visible fallback calculation until you resolve it; fallback calculation uses '"
            + fallbackProfileName + "'.";

        if (hasEmbeddedCopy)
            detail += " A project-embedded copy is available for explicit recovery.";

        return detail;
    }

    private static IReadOnlyList<ProfileRecoveryActionKind> CreateActions(bool hasEmbeddedCopy)
    {
        var actions = new List<ProfileRecoveryActionKind>
        {
            ProfileRecoveryActionKind.ImportMatchingProfile,
        };

        if (hasEmbeddedCopy) actions.Add(ProfileRecoveryActionKind.UseProjectEmbeddedCopy);

        actions.Add(ProfileRecoveryActionKind.RemapToInstalledProfile);
        actions.Add(ProfileRecoveryActionKind.KeepUnresolvedForNow);
        return actions;
    }
}
