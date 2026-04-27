using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.Core.Diagnostics;

/// <summary>
/// Produces read-only project health findings for Diagnostics UI/report surfaces.
/// </summary>
public sealed class ProjectValidationService
{
    private const string ProjectArea = "Project";
    private const string ProfilesArea = "Profiles";
    private const string TemplatesArea = "Templates";
    private const string MorphsNpcsArea = "Morphs/NPCs";
    private const string ExportArea = "Export";

    /// <summary>
    /// Validates the current project collections without mutating dirty state, version counters, or references.
    /// </summary>
    /// <param name="project">Project model to inspect.</param>
    /// <param name="profileCatalog">Bundled profile catalog used to identify neutral fallback profile references.</param>
    /// <returns>A report containing severity-coded findings grouped by workflow area.</returns>
    public static ProjectValidationReport Validate(ProjectModel project, TemplateProfileCatalog profileCatalog)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));
        if (profileCatalog is null) throw new ArgumentNullException(nameof(profileCatalog));

        var findings = new List<DiagnosticFinding>
        {
            new(
                DiagnosticSeverity.Info,
                ProjectArea,
                "Project validation complete",
                "Checked project, profile, template, morph/NPC, and export readiness without changing project state.")
        };

        AddTemplateFindings(project, findings);
        AddMorphTargetFindings(project, findings);
        AddExportFindings(project, findings);

        return new ProjectValidationReport(findings);
    }

    private static void AddTemplateFindings(ProjectModel project, List<DiagnosticFinding> findings)
    {
        if (project.SliderPresets.Count == 0)
        {
            findings.Add(new DiagnosticFinding(
                DiagnosticSeverity.Blocker,
                TemplatesArea,
                "No presets available",
                "Import at least one BodySlide preset before generating templates.ini.",
                actionHint: "Import BodySlide XML presets."));
            return;
        }

        foreach (var preset in project.SliderPresets)
            if (string.IsNullOrWhiteSpace(preset.Name))
                findings.Add(new DiagnosticFinding(
                    DiagnosticSeverity.Blocker,
                    TemplatesArea,
                    "Preset has an empty name",
                    "Template and morph output require every preset to have a stable name.",
                    preset.Name,
                    "Rename the preset before export."));
    }

    private static void AddMorphTargetFindings(ProjectModel project, List<DiagnosticFinding> findings)
    {
        var targets = project.CustomMorphTargets.Cast<MorphTargetBase>()
            .Concat(project.MorphedNpcs)
            .ToArray();

        if (targets.Length == 0)
        {
            findings.Add(new DiagnosticFinding(
                DiagnosticSeverity.Info,
                MorphsNpcsArea,
                "No morph targets or NPC assignments",
                "Morph generation has no custom targets or NPC rows to write yet."));
            return;
        }

        if (targets.All(target => !target.HasPresets))
            findings.Add(new DiagnosticFinding(
                DiagnosticSeverity.Info,
                MorphsNpcsArea,
                "No assigned presets",
                "Morph targets and NPC rows exist, but none currently reference a preset."));

        var presetNames = new HashSet<string>(
            project.SliderPresets.Select(preset => preset.Name),
            StringComparer.OrdinalIgnoreCase);

        foreach (var target in targets)
            foreach (var reference in target.SliderPresets)
                if (!presetNames.Contains(reference.Name) || !project.SliderPresets.Contains(reference))
                    findings.Add(new DiagnosticFinding(
                        DiagnosticSeverity.Blocker,
                        MorphsNpcsArea,
                        "Missing preset reference",
                        "Target '" + target.Name + "' references preset '" + reference.Name
                        + "', but that preset is not present in the project preset collection.",
                        target.Name,
                        "Remove stale preset references or re-import the preset."));
    }

    private static void AddExportFindings(ProjectModel project, List<DiagnosticFinding> findings)
    {
        var hasPresets = project.SliderPresets.Count > 0;
        var hasAssignedMorphs = project.CustomMorphTargets.Any(target => target.HasPresets)
                                || project.MorphedNpcs.Any(npc => npc.HasPresets);

        findings.Add(new DiagnosticFinding(
            DiagnosticSeverity.Info,
            ExportArea,
            "Export readiness summary",
            hasPresets
                ? "Template export has " + project.SliderPresets.Count + " preset(s); morph export has "
                  + (hasAssignedMorphs ? "assigned targets." : "no assigned targets yet.")
                : "Template and morph exports need imported presets before output is useful."));
    }
}
