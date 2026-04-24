using System.Collections;
using System.Diagnostics.CodeAnalysis;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using BS2BG.Core.Models;

namespace BS2BG.Core.Serialization;

[SuppressMessage("Performance", "CA1822:Mark members as static",
    Justification = "Project file I/O is exposed as an injectable service surface.")]
public sealed class ProjectFileService
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

        var dto = JsonSerializer.Deserialize<ProjectFileDto>(json, JsonOptions)
                  ?? new ProjectFileDto();
        var project = new ProjectModel();

        foreach (var (presetName, presetDto) in Enumerate(dto.SliderPresets))
            project.SliderPresets.Add(ToModel(presetName, presetDto));

        project.SortPresets();

        foreach (var (targetName, targetDto) in Enumerate(dto.CustomMorphTargets))
            project.CustomMorphTargets.Add(ToModel(targetName, targetDto, project));

        project.SortCustomMorphTargets();

        foreach (var (npcName, npcDto) in Enumerate(dto.MorphedNpCs))
            project.MorphedNpcs.Add(ToModel(npcName, npcDto, project));

        project.MarkClean();
        return project;
    }

    public void Save(ProjectModel project, string path)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));

        if (path is null) throw new ArgumentNullException(nameof(path));

        var targetPath = Path.GetFullPath(path);
        var tempPath = CreateTempPath(targetPath);
        try
        {
            File.WriteAllText(tempPath, SaveToString(project), Utf8NoBom);
            ReplaceWithTempFile(tempPath, targetPath);
            tempPath = null;
        }
        finally
        {
            if (tempPath is not null) TryDeleteTempFile(tempPath);
        }

        project.MarkClean();
    }

    public string SaveToString(ProjectModel project)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));

        return JsonSerializer.Serialize(ToDto(project), JsonOptions);
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
                PercentMin = sliderDto.PercentMin ?? 0,
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

    private static ProjectFileDto ToDto(ProjectModel project)
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
                .Select(npc => new NamedNpcObject(npc.Name, ToDto(npc))))
        };
    }

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

    private static string CreateTempPath(string targetPath)
    {
        var directory = Path.GetDirectoryName(targetPath)
                        ?? throw new InvalidOperationException("Project path must include a directory.");
        var fileName = Path.GetFileName(targetPath);
        return Path.Combine(directory, "." + fileName + "." + Guid.NewGuid().ToString("N") + ".tmp");
    }

    private static void ReplaceWithTempFile(string tempPath, string targetPath)
    {
        if (File.Exists(targetPath))
        {
            File.Replace(tempPath, targetPath, null);
            return;
        }

        File.Move(tempPath, targetPath);
    }

    private static void TryDeleteTempFile(string tempPath)
    {
        try
        {
            if (File.Exists(tempPath)) File.Delete(tempPath);
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }

    private static IEnumerable<KeyValuePair<string, TValue>> Enumerate<TValue>(
        Dictionary<string, TValue>? values) =>
        values ?? Enumerable.Empty<KeyValuePair<string, TValue>>();

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
