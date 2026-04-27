using BS2BG.Core.Formatting;

namespace BS2BG.Core.Models;

/// <summary>
/// Identifies the trust domain a profile definition came from so downstream workflows can keep bundled, local, and embedded profiles distinct.
/// </summary>
public enum ProfileSourceKind
{
    /// <summary>
    /// A read-only profile distributed with BS2BG.
    /// </summary>
    Bundled,

    /// <summary>
    /// A user-editable profile stored in the local custom profile folder.
    /// </summary>
    LocalCustom,

    /// <summary>
    /// A profile definition embedded in a project file for sharing or recovery.
    /// </summary>
    EmbeddedProject,
}

/// <summary>
/// Describes validation severity for custom profile import, edit, and catalog inclusion decisions.
/// </summary>
public enum ProfileValidationSeverity
{
    /// <summary>
    /// Informational context that does not affect catalog inclusion.
    /// </summary>
    Info,

    /// <summary>
    /// Non-blocking validation context that users may want to review.
    /// </summary>
    Caution,

    /// <summary>
    /// A validation problem that prevents catalog inclusion.
    /// </summary>
    Blocker,
}

/// <summary>
/// Immutable custom profile identity plus the generation-facing slider table data used by existing Core services.
/// </summary>
/// <param name="Name">Internal display name used as profile identity; filenames are not identity.</param>
/// <param name="Game">Optional game-style metadata such as Skyrim or Fallout4.</param>
/// <param name="SliderProfile">Generation-facing slider tables.</param>
/// <param name="SourceKind">Trust domain for the profile definition.</param>
/// <param name="FilePath">Optional source file path for local profile workflows.</param>
public sealed record CustomProfileDefinition(
    string Name,
    string Game,
    SliderProfile SliderProfile,
    ProfileSourceKind SourceKind,
    string? FilePath);

/// <summary>
/// Validation diagnostic emitted while parsing custom profile JSON before catalog inclusion.
/// </summary>
/// <param name="Severity">Severity that determines whether a profile is accepted.</param>
/// <param name="Code">Stable machine-readable diagnostic code.</param>
/// <param name="Message">Human-readable validation message.</param>
/// <param name="Table">Optional table name associated with the diagnostic.</param>
/// <param name="SliderName">Optional slider name associated with the diagnostic.</param>
public sealed record ProfileValidationDiagnostic(
    ProfileValidationSeverity Severity,
    string Code,
    string Message,
    string? Table,
    string? SliderName);

/// <summary>
/// Defines normalized comparison semantics for deciding whether two validated profile definitions conflict.
/// </summary>
public static class ProfileDefinitionEquality
{
    /// <summary>
    /// Compares profile definitions after normalizing identity and table order while preserving exact slider-name and finite-float semantics.
    /// </summary>
    /// <param name="left">First validated profile definition.</param>
    /// <param name="right">Second validated profile definition.</param>
    /// <returns><see langword="true"/> when both definitions represent the same generation-relevant profile data.</returns>
    public static bool DefinitionallyEquals(CustomProfileDefinition left, CustomProfileDefinition right)
    {
        if (left is null) throw new ArgumentNullException(nameof(left));
        if (right is null) throw new ArgumentNullException(nameof(right));

        return string.Equals(left.Name, right.Name, StringComparison.OrdinalIgnoreCase)
            && string.Equals(left.Game, right.Game, StringComparison.Ordinal)
            && DefaultsEqual(left.SliderProfile.Defaults, right.SliderProfile.Defaults)
            && MultipliersEqual(left.SliderProfile.Multipliers, right.SliderProfile.Multipliers)
            && SetsEqual(left.SliderProfile.InvertedNames, right.SliderProfile.InvertedNames);
    }

    private static bool DefaultsEqual(IReadOnlyList<SliderDefault> left, IReadOnlyList<SliderDefault> right)
    {
        if (left.Count != right.Count) return false;

        var rightByName = right.ToDictionary(value => value.Name, StringComparer.Ordinal);
        foreach (var item in left)
        {
            if (!rightByName.TryGetValue(item.Name, out var other)) return false;
            if (!item.ValueSmall.Equals(other.ValueSmall) || !item.ValueBig.Equals(other.ValueBig)) return false;
        }

        return true;
    }

    private static bool MultipliersEqual(IReadOnlyList<SliderMultiplier> left, IReadOnlyList<SliderMultiplier> right)
    {
        if (left.Count != right.Count) return false;

        var rightByName = right.ToDictionary(value => value.Name, value => value.Value, StringComparer.Ordinal);
        foreach (var item in left)
        {
            if (!rightByName.TryGetValue(item.Name, out var other)) return false;
            if (!item.Value.Equals(other)) return false;
        }

        return true;
    }

    private static bool SetsEqual(IReadOnlyList<string> left, IReadOnlyList<string> right)
    {
        if (left.Count != right.Count) return false;

        var rightSet = new HashSet<string>(right, StringComparer.Ordinal);
        return left.All(rightSet.Contains);
    }
}

/// <summary>
/// Immutable validation context for profile import and save operations.
/// </summary>
public sealed class ProfileValidationContext
{
    private ProfileValidationContext(IEnumerable<string> existingProfileNames, ProfileSourceKind sourceKind, string? filePath)
    {
        ExistingProfileNames = new HashSet<string>(existingProfileNames, StringComparer.OrdinalIgnoreCase);
        SourceKind = sourceKind;
        FilePath = filePath;
    }

    /// <summary>
    /// Gets the case-insensitive snapshot of profile names that the candidate must not shadow.
    /// </summary>
    public IReadOnlyCollection<string> ExistingProfileNames { get; }

    /// <summary>
    /// Gets the source kind assigned to a successfully validated profile.
    /// </summary>
    public ProfileSourceKind SourceKind { get; }

    /// <summary>
    /// Gets the optional source path associated with the candidate profile.
    /// </summary>
    public string? FilePath { get; }

    /// <summary>
    /// Creates an import validation context that materializes existing profile names using case-insensitive identity semantics.
    /// </summary>
    /// <param name="existingProfileNames">Existing bundled, local, or embedded display names to reject if duplicated.</param>
    /// <param name="sourceKind">Source kind assigned when validation succeeds.</param>
    /// <param name="filePath">Optional file path for local import diagnostics and storage workflows.</param>
    /// <returns>A validation context with a stable name snapshot.</returns>
    public static ProfileValidationContext ForImport(
        IEnumerable<string> existingProfileNames,
        ProfileSourceKind sourceKind,
        string? filePath = null)
    {
        if (existingProfileNames is null) throw new ArgumentNullException(nameof(existingProfileNames));

        return new ProfileValidationContext(existingProfileNames, sourceKind, filePath);
    }
}

/// <summary>
/// Result object returned by custom profile validation so malformed user JSON produces diagnostics instead of normal workflow exceptions.
/// </summary>
public sealed class ProfileValidationResult
{
    /// <summary>
    /// Initializes a new validation result with an optional profile and immutable diagnostics snapshot.
    /// </summary>
    /// <param name="profile">Validated profile definition, or <see langword="null"/> when blockers prevent catalog inclusion.</param>
    /// <param name="diagnostics">Diagnostics discovered while parsing or validating the profile.</param>
    public ProfileValidationResult(CustomProfileDefinition? profile, IEnumerable<ProfileValidationDiagnostic> diagnostics)
    {
        Profile = profile;
        Diagnostics = (diagnostics ?? throw new ArgumentNullException(nameof(diagnostics))).ToArray();
    }

    /// <summary>
    /// Gets the validated profile definition when no blocker diagnostics were emitted.
    /// </summary>
    public CustomProfileDefinition? Profile { get; }

    /// <summary>
    /// Gets the validation diagnostics emitted while parsing the candidate profile.
    /// </summary>
    public IReadOnlyList<ProfileValidationDiagnostic> Diagnostics { get; }

    /// <summary>
    /// Gets whether the validation result can be included in the catalog.
    /// </summary>
    public bool IsValid => Profile is not null && Diagnostics.All(diagnostic => diagnostic.Severity != ProfileValidationSeverity.Blocker);
}
