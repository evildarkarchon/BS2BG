using System.Collections;
using System.Diagnostics.CodeAnalysis;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using BS2BG.Core.Generation;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;

namespace BS2BG.Core.Serialization;

/// <summary>
/// Project load result that preserves recoverable diagnostics for optional embedded project sections.
/// </summary>
/// <param name="Project">Loaded project data, including legacy fields even when optional embedded data is invalid.</param>
/// <param name="Diagnostics">Recoverable diagnostics emitted while hydrating optional embedded custom profiles.</param>
public sealed record ProjectLoadResult(ProjectModel Project, IReadOnlyList<ProjectLoadDiagnostic> Diagnostics);

/// <summary>
/// Stable diagnostic emitted while opening a project file with optional embedded profile data.
/// </summary>
/// <param name="Code">Machine-readable diagnostic code.</param>
/// <param name="Message">Human-readable diagnostic message.</param>
/// <param name="ProfileName">Optional embedded profile name associated with the issue.</param>
public sealed record ProjectLoadDiagnostic(string Code, string Message, string? ProfileName);

/// <summary>
/// Save-time profile resolver used to embed referenced local custom profiles that are not already project-owned.
/// </summary>
/// <param name="AvailableCustomProfilesByName">Case-insensitive custom profile definitions available from the runtime catalog or store.</param>
public sealed record ProjectSaveContext(IReadOnlyDictionary<string, CustomProfileDefinition> AvailableCustomProfilesByName);

[SuppressMessage("Performance", "CA1822:Mark members as static",
    Justification = "Project file I/O is exposed as an injectable service surface.")]
public class ProjectFileService
{
    private static readonly JsonSerializerOptions JsonOptions = CreateJsonOptions();
    private static readonly Encoding Utf8NoBom = new UTF8Encoding(false);

    private static JsonSerializerOptions CreateJsonOptions()
    {
        var options = new JsonSerializerOptions
        {
            AllowTrailingCommas = true, ReadCommentHandling = JsonCommentHandling.Skip, WriteIndented = true
        };
        options.Converters.Add(new NamedNpcObjectListJsonConverter());
        return options;
    }

    public ProjectModel Load(string path)
    {
        if (path is null) throw new ArgumentNullException(nameof(path));

        return LoadFromString(File.ReadAllText(path, Encoding.UTF8));
    }

    public ProjectModel LoadFromString(string json)
    {
        if (json is null) throw new ArgumentNullException(nameof(json));

        return LoadWithDiagnosticsFromString(json).Project;
    }

    /// <summary>
    /// Loads project JSON and reports recoverable diagnostics for optional embedded custom profile data without blocking legacy project fields.
    /// </summary>
    /// <param name="json">Project JSON text.</param>
    /// <returns>The loaded project plus diagnostics for invalid or conflicting embedded profiles.</returns>
    public ProjectLoadResult LoadWithDiagnosticsFromString(string json)
    {
        if (json is null) throw new ArgumentNullException(nameof(json));

        var dto = JsonSerializer.Deserialize<ProjectFileDto>(json, JsonOptions)
                  ?? new ProjectFileDto();
        var project = new ProjectModel();
        var diagnostics = new List<ProjectLoadDiagnostic>();

        foreach (var (presetName, presetDto) in Enumerate(dto.SliderPresets))
            project.SliderPresets.Add(ToModel(presetName, presetDto));

        project.SortPresets();

        foreach (var (targetName, targetDto) in Enumerate(dto.CustomMorphTargets))
            project.CustomMorphTargets.Add(ToModel(targetName, targetDto, project));

        project.SortCustomMorphTargets();

        foreach (var (npcName, npcDto) in Enumerate(dto.MorphedNpCs))
            project.MorphedNpcs.Add(ToModel(npcName, npcDto, project));

        foreach (var profile in LoadEmbeddedProfiles(dto.CustomProfiles, diagnostics))
            project.CustomProfiles.Add(profile);

        project.AssignmentStrategy = LoadAssignmentStrategy(dto.AssignmentStrategy, diagnostics);

        project.MarkClean();
        return new ProjectLoadResult(project, diagnostics);
    }

    public void Save(ProjectModel project, string path)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));

        if (path is null) throw new ArgumentNullException(nameof(path));

        WriteAtomic(SaveToString(project), path);
    }

    /// <summary>
    /// Writes serialized project content atomically while preserving ledger details for commit failures.
    /// </summary>
    public virtual void WriteAtomic(string content, string path)
    {
        if (content is null) throw new ArgumentNullException(nameof(content));

        if (path is null) throw new ArgumentNullException(nameof(path));

        AtomicFileWriter.WriteAtomicBatch(new[] { (path, content) }, Utf8NoBom);
    }

    public string SaveToString(ProjectModel project)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));

        return SaveToString(project, null);
    }

    /// <summary>
    /// Serializes a project and optionally embeds only referenced non-bundled custom profile definitions available at save time.
    /// </summary>
    /// <param name="project">Project model to serialize.</param>
    /// <param name="saveContext">Optional resolver for referenced local custom profiles not already stored on the project.</param>
    /// <returns>Indented project JSON with legacy root fields kept first and optional CustomProfiles omitted when empty.</returns>
    public string SaveToString(ProjectModel project, ProjectSaveContext? saveContext)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));

        return JsonSerializer.Serialize(ToDto(project, saveContext), JsonOptions);
    }

    private static SliderPreset ToModel(string presetName, SliderPresetDto? dto)
    {
        var profileName = ProjectProfileMapping.Resolve(dto?.Profile, dto?.IsUunp ?? false);
        var preset = new SliderPreset(presetName, profileName);

        foreach (var sliderDto in dto?.SetSliders ?? Enumerable.Empty<SetSliderDto?>())
        {
            if (sliderDto is null) continue;

            preset.AddSetSlider(new SetSlider(sliderDto.Name ?? string.Empty)
            {
                Enabled = sliderDto.Enabled ?? true,
                ValueSmall = sliderDto.ValueSmall,
                ValueBig = sliderDto.ValueBig,
                PercentMin = sliderDto.PercentMin ?? 100,
                PercentMax = sliderDto.PercentMax ?? 100
            });
        }

        return preset;
    }

    private static CustomMorphTarget ToModel(string targetName, MorphTargetDto? dto, ProjectModel project)
    {
        var target = new CustomMorphTarget(targetName);
        AddResolvedPresetReferences(target, dto?.SliderPresets, project);
        return target;
    }

    private static Npc ToModel(string npcName, NpcDto? dto, ProjectModel project)
    {
        var npc = new Npc(npcName)
        {
            Mod = dto?.Mod ?? string.Empty,
            EditorId = dto?.EditorId ?? string.Empty,
            Race = dto?.Race ?? string.Empty,
            FormId = dto?.FormId ?? string.Empty
        };
        AddResolvedPresetReferences(npc, dto?.SliderPresets, project);
        return npc;
    }

    private static void AddResolvedPresetReferences(
        MorphTargetBase target,
        IEnumerable<string>? presetNames,
        ProjectModel project)
    {
        foreach (var presetName in presetNames ?? Enumerable.Empty<string>())
        {
            var preset = project.FindSliderPreset(presetName);
            if (preset is not null) target.AddSliderPreset(preset);
        }
    }

    private static ProjectFileDto ToDto(ProjectModel project, ProjectSaveContext? saveContext)
    {
        return new ProjectFileDto
        {
            SliderPresets = project.SliderPresets
                .OrderBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase)
                .ToDictionary(preset => preset.Name, ToDto, StringComparer.Ordinal),
            CustomMorphTargets = project.CustomMorphTargets
                .OrderBy(target => target.Name, StringComparer.OrdinalIgnoreCase)
                .ToDictionary(target => target.Name, ToDto, StringComparer.Ordinal),
            MorphedNpCs = new NamedNpcObjectList(project.MorphedNpcs
                .Select(npc => new NamedNpcObject(npc.Name, ToDto(npc)))),
            CustomProfiles = ToEmbeddedProfileDtos(project, saveContext),
            AssignmentStrategy = ToAssignmentStrategyElement(project.AssignmentStrategy),
        };
    }

    private static JsonElement? ToAssignmentStrategyElement(AssignmentStrategyDefinition? strategy)
    {
        return strategy is null ? null : JsonSerializer.SerializeToElement(ToAssignmentStrategyDto(strategy), JsonOptions);
    }

    private static AssignmentStrategyDto ToAssignmentStrategyDto(AssignmentStrategyDefinition strategy)
    {
        return new AssignmentStrategyDto
        {
            SchemaVersion = strategy.SchemaVersion,
            Kind = strategy.Kind.ToString(),
            Seed = strategy.Seed,
            Rules = strategy.Rules.Select(rule => new AssignmentStrategyRuleDto
            {
                Name = rule.Name,
                PresetNames = rule.PresetNames.ToList(),
                RaceFilters = rule.RaceFilters.ToList(),
                Weight = rule.Weight,
                BucketName = rule.BucketName
            }).ToList()
        };
    }

    private static JsonElement? ToEmbeddedProfileDtos(ProjectModel project, ProjectSaveContext? saveContext)
    {
        var referencedNames = project.SliderPresets
            .Select(preset => preset.ProfileName)
            .Where(name => !IsBundledProfileName(name))
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        if (referencedNames.Count == 0) return null;

        var projectProfiles = project.CustomProfiles
            .Where(profile => !IsBundledProfileName(profile.Name) && profile.SourceKind != ProfileSourceKind.Bundled)
            .ToDictionary(profile => profile.Name, StringComparer.OrdinalIgnoreCase);
        var resolved = new List<CustomProfileDefinition>();
        foreach (var name in referencedNames)
        {
            if (projectProfiles.TryGetValue(name, out var projectProfile))
            {
                resolved.Add(projectProfile);
                continue;
            }

            if (saveContext?.AvailableCustomProfilesByName.TryGetValue(name, out var contextProfile) == true
                && !IsBundledProfileName(contextProfile.Name)
                && contextProfile.SourceKind != ProfileSourceKind.Bundled)
            {
                resolved.Add(contextProfile);
            }
        }

        if (resolved.Count == 0) return null;

        var profiles = resolved
            .OrderBy(profile => profile.Name, StringComparer.OrdinalIgnoreCase)
            .Select(ToEmbeddedProfileElement)
            .ToList();
        return JsonSerializer.SerializeToElement(profiles, JsonOptions);
    }

    private static JsonElement ToEmbeddedProfileElement(CustomProfileDefinition profile) =>
        JsonSerializer.SerializeToElement(ToEmbeddedProfileDto(profile), JsonOptions);

    private static EmbeddedProfileDto ToEmbeddedProfileDto(CustomProfileDefinition profile) =>
        new()
        {
            Version = 1,
            Name = profile.Name,
            Game = profile.Game,
            Defaults = profile.SliderProfile.Defaults
                .OrderBy(value => value.Name, StringComparer.OrdinalIgnoreCase)
                .ToDictionary(value => value.Name, ToDto, StringComparer.Ordinal),
            Multipliers = profile.SliderProfile.Multipliers
                .OrderBy(value => value.Name, StringComparer.OrdinalIgnoreCase)
                .ToDictionary(value => value.Name, value => value.Value, StringComparer.Ordinal),
            Inverted = profile.SliderProfile.InvertedNames
                .OrderBy(value => value, StringComparer.OrdinalIgnoreCase)
                .ToList(),
        };

    private static EmbeddedDefaultDto ToDto(Formatting.SliderDefault value) =>
        new() { ValueSmall = value.ValueSmall, ValueBig = value.ValueBig };

    private static SliderPresetDto ToDto(SliderPreset preset)
    {
        return new SliderPresetDto
        {
            IsUunp = preset.IsUunp,
            Profile = preset.ProfileName,
            SetSliders = preset.GetAllSetSlidersForSave()
                .Where(slider => !ShouldOmitMissingDefault(slider))
                .Select(ToDto)
                .ToList()
        };
    }

    private static SetSliderDto ToDto(SetSlider slider)
    {
        return new SetSliderDto
        {
            Name = slider.Name,
            Enabled = slider.Enabled,
            ValueSmall = slider.ValueSmall,
            ValueBig = slider.ValueBig,
            PercentMin = slider.PercentMin,
            PercentMax = slider.PercentMax
        };
    }

    private static MorphTargetDto ToDto(CustomMorphTarget target)
    {
        return new MorphTargetDto
        {
            SliderPresets = target.SliderPresets
                .Select(preset => preset.Name)
                .ToList()
        };
    }

    private static NpcDto ToDto(Npc npc)
    {
        return new NpcDto
        {
            Mod = npc.Mod,
            EditorId = npc.EditorId,
            Race = npc.Race,
            FormId = npc.FormId,
            SliderPresets = npc.SliderPresets
                .Select(preset => preset.Name)
                .ToList()
        };
    }

    private static bool ShouldOmitMissingDefault(SetSlider slider)
    {
        return slider.IsMissingDefault
               && slider.Enabled
               && slider.PercentMin == 100
               && slider.PercentMax == 100;
    }

    private static IEnumerable<KeyValuePair<string, TValue>> Enumerate<TValue>(
        Dictionary<string, TValue>? values) =>
        values ?? Enumerable.Empty<KeyValuePair<string, TValue>>();

    private static AssignmentStrategyDefinition? LoadAssignmentStrategy(
        JsonElement? strategyElement,
        List<ProjectLoadDiagnostic> diagnostics)
    {
        if (strategyElement is null) return null;

        if (strategyElement.Value.ValueKind != JsonValueKind.Object)
        {
            diagnostics.Add(new ProjectLoadDiagnostic(
                "AssignmentStrategyInvalid",
                "Assignment strategy must be a JSON object and was ignored.",
                null));
            return null;
        }

        var schemaVersion = ReadInt(strategyElement.Value, "schemaVersion", "SchemaVersion")
                            ?? AssignmentStrategyDefinition.CurrentSchemaVersion;
        if (schemaVersion != AssignmentStrategyDefinition.CurrentSchemaVersion)
        {
            diagnostics.Add(new ProjectLoadDiagnostic(
                "AssignmentStrategyUnsupportedSchemaVersion",
                $"Assignment strategy schemaVersion {schemaVersion} is not supported and was ignored.",
                null));
            return null;
        }

        if (!Enum.TryParse(ReadString(strategyElement.Value, "kind", "Kind"), true, out AssignmentStrategyKind kind))
            kind = AssignmentStrategyKind.SeededRandom;

        var strategy = new AssignmentStrategyDefinition(
            schemaVersion,
            kind,
            ReadInt(strategyElement.Value, "seed", "Seed"),
            ReadAssignmentStrategyRules(strategyElement.Value));
        var validationMessage = ValidateAssignmentStrategy(strategy);
        if (validationMessage is null) return strategy;

        diagnostics.Add(new ProjectLoadDiagnostic(
            "AssignmentStrategyInvalid",
            validationMessage,
            null));
        return null;
    }

    private static List<AssignmentStrategyRule> ReadAssignmentStrategyRules(JsonElement strategyElement)
    {
        if (!TryGetProperty(strategyElement, out var rulesElement, "rules", "Rules")
            || rulesElement.ValueKind != JsonValueKind.Array)
            return new List<AssignmentStrategyRule>();

        var rules = new List<AssignmentStrategyRule>();
        foreach (var ruleElement in rulesElement.EnumerateArray())
        {
            if (ruleElement.ValueKind != JsonValueKind.Object) continue;

            rules.Add(new AssignmentStrategyRule(
                ReadString(ruleElement, "name", "Name") ?? string.Empty,
                ReadStringArray(ruleElement, "presetNames", "PresetNames"),
                ReadStringArray(ruleElement, "raceFilters", "RaceFilters"),
                ReadDouble(ruleElement, "weight", "Weight") ?? 1.0,
                ReadString(ruleElement, "bucketName", "BucketName")));
        }

        return rules;
    }

    private static string? ValidateAssignmentStrategy(AssignmentStrategyDefinition strategy)
    {
        var namedRules = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var rule in strategy.Rules)
        {
            if (!double.IsFinite(rule.Weight) || rule.Weight < 0)
                return "Assignment strategy rule weights must be finite and non-negative.";

            if (!string.IsNullOrWhiteSpace(rule.Name) && !namedRules.Add(rule.Name))
                return "Assignment strategy rule names must be unique when provided.";
        }

        return strategy.Kind switch
        {
            AssignmentStrategyKind.Weighted => ValidateWeightedStrategy(strategy),
            AssignmentStrategyKind.RaceFilters => ValidateRaceFilterStrategy(strategy),
            AssignmentStrategyKind.GroupsBuckets => ValidateGroupsBucketsStrategy(strategy),
            _ => ValidateAssignableRules(strategy)
        };
    }

    private static string? ValidateWeightedStrategy(AssignmentStrategyDefinition strategy)
    {
        if (strategy.Rules.Count == 0 || strategy.Rules.All(rule => rule.Weight <= 0))
            return "Assignment strategy weighted rules require at least one positive weight.";

        return ValidateAssignableRules(strategy);
    }

    private static string? ValidateRaceFilterStrategy(AssignmentStrategyDefinition strategy)
    {
        foreach (var rule in strategy.Rules)
        {
            if (rule.PresetNames.Count == 0)
                return "Assignment strategy race-filter rules require at least one preset name.";

            if (rule.RaceFilters.Count == 0)
                return "Assignment strategy race-filter rules require at least one imported race filter.";
        }

        return null;
    }

    private static string? ValidateGroupsBucketsStrategy(AssignmentStrategyDefinition strategy)
    {
        foreach (var rule in strategy.Rules)
        {
            if (rule.PresetNames.Count == 0)
                return "Assignment strategy group/bucket rules require at least one preset name.";

            if (string.IsNullOrWhiteSpace(rule.BucketName))
                return "Assignment strategy group/bucket rules require a bucket name.";
        }

        return null;
    }

    private static string? ValidateAssignableRules(AssignmentStrategyDefinition strategy)
    {
        foreach (var rule in strategy.Rules)
            if (rule.PresetNames.Count == 0)
                return "Assignment strategy rules that assign presets require at least one preset name.";

        return null;
    }

    private static string[] ReadStringArray(JsonElement element, params string[] propertyNames)
    {
        if (!TryGetProperty(element, out var arrayElement, propertyNames)
            || arrayElement.ValueKind != JsonValueKind.Array)
            return Array.Empty<string>();

        return arrayElement.EnumerateArray()
            .Where(value => value.ValueKind == JsonValueKind.String)
            .Select(value => value.GetString() ?? string.Empty)
            .ToArray();
    }

    private static string? ReadString(JsonElement element, params string[] propertyNames)
    {
        return TryGetProperty(element, out var value, propertyNames) && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;
    }

    private static int? ReadInt(JsonElement element, params string[] propertyNames)
    {
        return TryGetProperty(element, out var value, propertyNames) && value.ValueKind == JsonValueKind.Number
               && value.TryGetInt32(out var result)
            ? result
            : null;
    }

    private static double? ReadDouble(JsonElement element, params string[] propertyNames)
    {
        return TryGetProperty(element, out var value, propertyNames) && value.ValueKind == JsonValueKind.Number
            ? value.GetDouble()
            : null;
    }

    private static bool TryGetProperty(JsonElement element, out JsonElement value, params string[] propertyNames)
    {
        foreach (var propertyName in propertyNames)
            if (element.TryGetProperty(propertyName, out value))
                return true;

        value = default;
        return false;
    }

    private static List<CustomProfileDefinition> LoadEmbeddedProfiles(
        JsonElement? profileDtos,
        List<ProjectLoadDiagnostic> diagnostics)
    {
        var profiles = new List<CustomProfileDefinition>();
        if (profileDtos is null) return profiles;

        if (profileDtos.Value.ValueKind != JsonValueKind.Array)
        {
            diagnostics.Add(new ProjectLoadDiagnostic(
                "EmbeddedProfileSectionInvalid",
                "CustomProfiles must be an array; embedded profiles were ignored.",
                null));
            return profiles;
        }

        var seenNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var profileDto in profileDtos.Value.EnumerateArray())
        {
            var result = ProfileDefinitionService.ValidateProfileJson(
                profileDto.GetRawText(),
                ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.EmbeddedProject));
            if (!result.IsValid || result.Profile is null)
            {
                diagnostics.Add(new ProjectLoadDiagnostic(
                    "EmbeddedProfileInvalid",
                    "Embedded custom profile is malformed and was not added to the project profile overlay.",
                    TryReadEmbeddedProfileName(profileDto)));
                continue;
            }

            var profile = result.Profile;
            if (IsBundledProfileName(profile.Name))
            {
                diagnostics.Add(new ProjectLoadDiagnostic(
                    "EmbeddedProfileBundledNameCollision",
                    $"Embedded custom profile '{profile.Name}' collides with a bundled profile name and was ignored.",
                    profile.Name));
                continue;
            }

            if (!seenNames.Add(profile.Name))
            {
                diagnostics.Add(new ProjectLoadDiagnostic(
                    "EmbeddedProfileDuplicateName",
                    $"Embedded custom profile '{profile.Name}' duplicates another embedded profile and was ignored.",
                    profile.Name));
                continue;
            }

            profiles.Add(profile);
        }

        return profiles;
    }

    private static string? TryReadEmbeddedProfileName(JsonElement profileElement)
    {
        if (profileElement.ValueKind == JsonValueKind.Object
            && profileElement.TryGetProperty("Name", out var nameElement)
            && nameElement.ValueKind == JsonValueKind.String)
        {
            return nameElement.GetString();
        }

        return null;
    }

    private static bool IsBundledProfileName(string? name) =>
        string.Equals(name, ProjectProfileMapping.SkyrimCbbe, StringComparison.OrdinalIgnoreCase)
        || string.Equals(name, ProjectProfileMapping.SkyrimUunp, StringComparison.OrdinalIgnoreCase)
        || string.Equals(name, ProjectProfileMapping.Fallout4Cbbe, StringComparison.OrdinalIgnoreCase);

    private static IEnumerable<KeyValuePair<string, NpcDto?>> Enumerate(NamedNpcObjectList? values)
    {
        if (values is null) yield break;

        foreach (var entry in values) yield return new KeyValuePair<string, NpcDto?>(entry.Name, entry.Value);
    }

    private sealed class ProjectFileDto
    {
        [JsonPropertyName("SliderPresets")]
        [JsonPropertyOrder(0)]
        public Dictionary<string, SliderPresetDto>? SliderPresets { get; set; }

        [JsonPropertyName("CustomMorphTargets")]
        [JsonPropertyOrder(1)]
        public Dictionary<string, MorphTargetDto>? CustomMorphTargets { get; set; }

        [JsonPropertyName("MorphedNPCs")]
        [JsonPropertyOrder(2)]
        public NamedNpcObjectList? MorphedNpCs { get; set; }

        [JsonPropertyName("CustomProfiles")]
        [JsonPropertyOrder(3)]
        [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
        // CustomProfiles is appended after legacy root fields so older readers can ignore it without field-order churn.
        public JsonElement? CustomProfiles { get; set; }

        [JsonPropertyName("AssignmentStrategy")]
        [JsonPropertyOrder(4)]
        [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
        public JsonElement? AssignmentStrategy { get; set; }
    }

    private sealed class AssignmentStrategyDto
    {
        [JsonPropertyName("schemaVersion")]
        [JsonPropertyOrder(0)]
        public int SchemaVersion { get; set; }

        [JsonPropertyName("kind")]
        [JsonPropertyOrder(1)]
        public string? Kind { get; set; }

        [JsonPropertyName("seed")]
        [JsonPropertyOrder(2)]
        [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
        public int? Seed { get; set; }

        [JsonPropertyName("rules")]
        [JsonPropertyOrder(3)]
        public List<AssignmentStrategyRuleDto>? Rules { get; set; }
    }

    private sealed class AssignmentStrategyRuleDto
    {
        [JsonPropertyName("name")]
        [JsonPropertyOrder(0)]
        public string? Name { get; set; }

        [JsonPropertyName("presetNames")]
        [JsonPropertyOrder(1)]
        public List<string>? PresetNames { get; set; }

        [JsonPropertyName("raceFilters")]
        [JsonPropertyOrder(2)]
        public List<string>? RaceFilters { get; set; }

        [JsonPropertyName("weight")]
        [JsonPropertyOrder(3)]
        public double Weight { get; set; }

        [JsonPropertyName("bucketName")]
        [JsonPropertyOrder(4)]
        [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
        public string? BucketName { get; set; }
    }

    private sealed class EmbeddedProfileDto
    {
        [JsonPropertyName("Version")]
        [JsonPropertyOrder(0)]
        public int? Version { get; set; }

        [JsonPropertyName("Name")]
        [JsonPropertyOrder(1)]
        public string? Name { get; set; }

        [JsonPropertyName("Game")]
        [JsonPropertyOrder(2)]
        public string? Game { get; set; }

        [JsonPropertyName("Defaults")]
        [JsonPropertyOrder(3)]
        public Dictionary<string, EmbeddedDefaultDto>? Defaults { get; set; }

        [JsonPropertyName("Multipliers")]
        [JsonPropertyOrder(4)]
        public Dictionary<string, float>? Multipliers { get; set; }

        [JsonPropertyName("Inverted")]
        [JsonPropertyOrder(5)]
        public List<string>? Inverted { get; set; }
    }

    private sealed class EmbeddedDefaultDto
    {
        [JsonPropertyName("valueSmall")]
        [JsonPropertyOrder(0)]
        public float? ValueSmall { get; set; }

        [JsonPropertyName("valueBig")]
        [JsonPropertyOrder(1)]
        public float? ValueBig { get; set; }
    }

    private sealed class SliderPresetDto
    {
        [JsonPropertyName("isUUNP")]
        [JsonPropertyOrder(0)]
        public bool? IsUunp { get; set; }

        [JsonPropertyName("Profile")]
        [JsonPropertyOrder(1)]
        public string? Profile { get; set; }

        [JsonPropertyName("SetSliders")]
        [JsonPropertyOrder(2)]
        public List<SetSliderDto>? SetSliders { get; set; }
    }

    private sealed class SetSliderDto
    {
        [JsonPropertyName("name")]
        [JsonPropertyOrder(0)]
        public string? Name { get; set; }

        [JsonPropertyName("enabled")]
        [JsonPropertyOrder(1)]
        public bool? Enabled { get; set; }

        [JsonPropertyName("valueSmall")]
        [JsonPropertyOrder(2)]
        public int? ValueSmall { get; set; }

        [JsonPropertyName("valueBig")]
        [JsonPropertyOrder(3)]
        public int? ValueBig { get; set; }

        [JsonPropertyName("pctMin")]
        [JsonPropertyOrder(4)]
        public int? PercentMin { get; set; }

        [JsonPropertyName("pctMax")]
        [JsonPropertyOrder(5)]
        public int? PercentMax { get; set; }
    }

    private sealed class MorphTargetDto
    {
        [JsonPropertyName("SliderPresets")]
        [JsonPropertyOrder(0)]
        public List<string>? SliderPresets { get; set; }
    }

    private sealed class NpcDto
    {
        [JsonPropertyName("Mod")]
        [JsonPropertyOrder(0)]
        public string? Mod { get; set; }

        [JsonPropertyName("EditorId")]
        [JsonPropertyOrder(1)]
        public string? EditorId { get; set; }

        [JsonPropertyName("Race")]
        [JsonPropertyOrder(2)]
        public string? Race { get; set; }

        [JsonPropertyName("FormId")]
        [JsonPropertyOrder(3)]
        public string? FormId { get; set; }

        [JsonPropertyName("SliderPresets")]
        [JsonPropertyOrder(4)]
        public List<string>? SliderPresets { get; set; }
    }

    private readonly struct NamedNpcObject(string name, NpcDto? value)
    {
        public string Name { get; } = name;

        public NpcDto? Value { get; } = value;
    }

    private sealed class NamedNpcObjectList(IEnumerable<NamedNpcObject> entries) : IReadOnlyList<NamedNpcObject>
    {
        private readonly NamedNpcObject[] entries =
            (entries ?? throw new ArgumentNullException(nameof(entries))).ToArray();

        public int Count => entries.Length;

        public NamedNpcObject this[int index] => entries[index];

        public IEnumerator<NamedNpcObject> GetEnumerator() => ((IEnumerable<NamedNpcObject>)entries).GetEnumerator();

        IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();
    }

    private sealed class NamedNpcObjectListJsonConverter : JsonConverter<NamedNpcObjectList>
    {
        public override NamedNpcObjectList Read(
            ref Utf8JsonReader reader,
            Type typeToConvert,
            JsonSerializerOptions options)
        {
            if (reader.TokenType == JsonTokenType.Null) return new NamedNpcObjectList(Array.Empty<NamedNpcObject>());

            if (reader.TokenType != JsonTokenType.StartObject)
                throw new JsonException("Expected MorphedNPCs to be a JSON object.");

            var entries = new List<NamedNpcObject>();
            while (reader.Read())
            {
                if (reader.TokenType == JsonTokenType.EndObject) return new NamedNpcObjectList(entries);

                if (reader.TokenType != JsonTokenType.PropertyName)
                    throw new JsonException("Expected NPC name property.");

                var name = reader.GetString() ?? string.Empty;
                if (!reader.Read()) throw new JsonException("Expected NPC object.");

                var value = JsonSerializer.Deserialize<NpcDto>(ref reader, options);
                entries.Add(new NamedNpcObject(name, value));
            }

            throw new JsonException("Expected end of MorphedNPCs object.");
        }

        public override void Write(
            Utf8JsonWriter writer,
            NamedNpcObjectList value,
            JsonSerializerOptions options)
        {
            writer.WriteStartObject();

            foreach (var entry in value)
            {
                writer.WritePropertyName(entry.Name);
                JsonSerializer.Serialize(writer, entry.Value, options);
            }

            writer.WriteEndObject();
        }
    }
}
