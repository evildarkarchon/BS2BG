using BS2BG.Core.Automation;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.Core.Export;

/// <summary>
/// Identifies an atomic output family whose artifacts commit together and whose position controls cross-family commit order.
/// </summary>
public enum OutputArtifactGroupKind
{
    BodyGen,
    BosJson,
}

/// <summary>
/// Identifies the semantic purpose of one generated output artifact without requiring callers to parse its filename.
/// </summary>
public enum OutputArtifactRole
{
    BodyGenTemplates,
    BodyGenMorphs,
    BosPresetJson,
}

/// <summary>
/// Captures the generation-ready state consumed synchronously by output artifact planning.
/// </summary>
public sealed class OutputArtifactPlanningInput
{
    /// <summary>
    /// Creates planning input for one output operation. Callers must not mutate the project or catalog during planning.
    /// </summary>
    /// <param name="project">Generation-ready project state, including any saved-strategy replay already applied.</param>
    /// <param name="profileCatalog">Effective profile catalog selected by the caller's readiness policy.</param>
    /// <param name="intent">Output families to include.</param>
    /// <param name="omitRedundantSliders">Whether redundant template sliders are omitted.</param>
    public OutputArtifactPlanningInput(
        ProjectModel project,
        TemplateProfileCatalog profileCatalog,
        OutputIntent intent,
        bool omitRedundantSliders)
    {
        Project = project ?? throw new ArgumentNullException(nameof(project));
        ProfileCatalog = profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog));
        if (!Enum.IsDefined(typeof(OutputIntent), intent))
            throw new ArgumentOutOfRangeException(nameof(intent), intent, "Unknown output intent.");
        Intent = intent;
        OmitRedundantSliders = omitRedundantSliders;
    }

    public ProjectModel Project { get; }

    public TemplateProfileCatalog ProfileCatalog { get; }

    public OutputIntent Intent { get; }

    public bool OmitRedundantSliders { get; }
}

/// <summary>
/// Holds one immutable generated file with a semantic role, a commit-group-relative leaf name, and exact bytes.
/// </summary>
public sealed class OutputArtifact
{
    private readonly byte[] content;

    /// <summary>
    /// Creates an artifact and defensively captures its exact byte content.
    /// </summary>
    /// <param name="role">Semantic role used by previews, manifests, and commit validation.</param>
    /// <param name="relativePath">Single leaf filename relative to the artifact's commit-group root.</param>
    /// <param name="content">Exact bytes that every consumer must use unchanged.</param>
    public OutputArtifact(OutputArtifactRole role, string relativePath, byte[] content)
    {
        Role = role;
        RelativePath = ValidateRelativePath(relativePath);
        this.content = (content ?? throw new ArgumentNullException(nameof(content))).ToArray();
    }

    public OutputArtifactRole Role { get; }

    public string RelativePath { get; }

    public int ByteCount => content.Length;

    /// <summary>
    /// Returns a detached copy of the authoritative bytes so callers cannot mutate the plan.
    /// </summary>
    public byte[] CopyContent() => content.ToArray();

    /// <summary>
    /// Restricts group-relative paths to safe leaf names so consumers can mount them without traversal.
    /// </summary>
    private static string ValidateRelativePath(string relativePath)
    {
        if (string.IsNullOrWhiteSpace(relativePath))
            throw new ArgumentException("Artifact relative paths must not be blank.", nameof(relativePath));

        if (Path.IsPathRooted(relativePath)
            || relativePath is "." or ".."
            || relativePath.Contains('/')
            || relativePath.Contains('\\')
            || relativePath.Contains(':')
            || relativePath.Contains('\0')
            || !string.Equals(Path.GetFileName(relativePath), relativePath, StringComparison.Ordinal))
            throw new ArgumentException(
                "Artifact relative paths must be safe leaf filenames without roots or directory traversal.",
                nameof(relativePath));

        return relativePath;
    }
}

/// <summary>
/// Groups artifacts that commit atomically while preserving their deterministic write and presentation order.
/// </summary>
public sealed class OutputArtifactCommitGroup
{
    /// <summary>
    /// Creates a non-empty atomic group and validates role membership and case-insensitive path uniqueness.
    /// </summary>
    /// <param name="kind">Output family whose artifacts share one atomic commit.</param>
    /// <param name="artifacts">Artifacts in deterministic commit and presentation order.</param>
    public OutputArtifactCommitGroup(OutputArtifactGroupKind kind, IEnumerable<OutputArtifact> artifacts)
    {
        Kind = kind;
        var materialized = (artifacts ?? throw new ArgumentNullException(nameof(artifacts))).ToArray();
        if (materialized.Length == 0)
            throw new ArgumentException("Output artifact commit groups must not be empty.", nameof(artifacts));

        var paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var artifact in materialized)
        {
            if (artifact is null)
                throw new ArgumentException("Output artifact commit groups must not contain null artifacts.", nameof(artifacts));
            if (!paths.Add(artifact.RelativePath))
                throw new ArgumentException("Duplicate artifact relative path: " + artifact.RelativePath, nameof(artifacts));
            if (!RoleBelongsToGroup(artifact.Role, kind))
                throw new ArgumentException(
                    "Artifact role '" + artifact.Role + "' does not belong to commit group '" + kind + "'.",
                    nameof(artifacts));
        }

        if (kind == OutputArtifactGroupKind.BodyGen
            && (materialized.Length != 2
                || materialized.Count(artifact => artifact.Role == OutputArtifactRole.BodyGenTemplates) != 1
                || materialized.Count(artifact => artifact.Role == OutputArtifactRole.BodyGenMorphs) != 1))
            throw new ArgumentException(
                "BodyGen commit groups require exactly one templates artifact and one morphs artifact.",
                nameof(artifacts));

        Artifacts = Array.AsReadOnly(materialized);
    }

    public OutputArtifactGroupKind Kind { get; }

    public IReadOnlyList<OutputArtifact> Artifacts { get; }

    /// <summary>
    /// Keeps semantic artifact roles inside their declared atomic output family.
    /// </summary>
    private static bool RoleBelongsToGroup(OutputArtifactRole role, OutputArtifactGroupKind kind) => kind switch
    {
        OutputArtifactGroupKind.BodyGen => role is OutputArtifactRole.BodyGenTemplates or OutputArtifactRole.BodyGenMorphs,
        OutputArtifactGroupKind.BosJson => role == OutputArtifactRole.BosPresetJson,
        _ => false,
    };
}

/// <summary>
/// Represents the complete immutable output artifact plan for one project state and output operation.
/// </summary>
public sealed class OutputArtifactPlan
{
    /// <summary>
    /// Creates a plan from ordered, non-duplicated commit groups.
    /// </summary>
    /// <param name="intent">Output families represented by the plan.</param>
    /// <param name="groups">Commit groups in cross-family commit order.</param>
    public OutputArtifactPlan(OutputIntent intent, IEnumerable<OutputArtifactCommitGroup> groups)
    {
        if (!Enum.IsDefined(typeof(OutputIntent), intent))
            throw new ArgumentOutOfRangeException(nameof(intent), intent, "Unknown output intent.");
        Intent = intent;
        var materialized = (groups ?? throw new ArgumentNullException(nameof(groups))).ToArray();
        if (materialized.Select(group => group.Kind).Distinct().Count() != materialized.Length)
            throw new ArgumentException("Output artifact plans must not repeat commit groups.", nameof(groups));
        ValidateGroupOrder(intent, materialized);

        Groups = Array.AsReadOnly(materialized);
    }

    public OutputIntent Intent { get; }

    public IReadOnlyList<OutputArtifactCommitGroup> Groups { get; }

    /// <summary>
    /// Returns the required commit group or throws when the plan's output intent did not include it.
    /// </summary>
    /// <param name="kind">Commit group requested by the consumer.</param>
    /// <returns>The matching immutable commit group.</returns>
    /// <exception cref="InvalidOperationException">The plan does not contain the requested group.</exception>
    public OutputArtifactCommitGroup GetRequiredGroup(OutputArtifactGroupKind kind) =>
        Groups.FirstOrDefault(group => group.Kind == kind)
        ?? throw new InvalidOperationException("Output artifact plan does not contain commit group '" + kind + "'.");

    /// <summary>
    /// Enforces intent membership and BodyGen-before-BoS order so partial-success semantics cannot be rearranged by consumers.
    /// </summary>
    private static void ValidateGroupOrder(OutputIntent intent, OutputArtifactCommitGroup[] groups)
    {
        var valid = intent switch
        {
            OutputIntent.BodyGen => groups.Length == 1 && groups[0].Kind == OutputArtifactGroupKind.BodyGen,
            OutputIntent.BosJson => groups.Length <= 1
                                    && groups.All(group => group.Kind == OutputArtifactGroupKind.BosJson),
            OutputIntent.All => groups.Length is 1 or 2
                                && groups[0].Kind == OutputArtifactGroupKind.BodyGen
                                && (groups.Length == 1 || groups[1].Kind == OutputArtifactGroupKind.BosJson),
            _ => false,
        };
        if (!valid)
            throw new ArgumentException(
                "Output artifact commit groups do not match the requested intent or required commit order.",
                nameof(groups));
    }
}
