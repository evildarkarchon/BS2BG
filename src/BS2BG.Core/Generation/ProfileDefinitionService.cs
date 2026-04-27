using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using BS2BG.Core.Formatting;
using BS2BG.Core.Models;

namespace BS2BG.Core.Generation;

/// <summary>
/// Validates and exports standalone custom profile JSON before any untrusted profile definition can enter the catalog.
/// </summary>
public sealed class ProfileDefinitionService
{
    private const int SupportedVersion = 1;

    /// <summary>
    /// Parses profile JSON with explicit token validation and returns diagnostics instead of throwing for ordinary malformed user input.
    /// </summary>
    /// <param name="json">Candidate profile JSON containing Version, Name, Game, Defaults, Multipliers, and Inverted metadata.</param>
    /// <param name="context">Validation context containing existing names and the source metadata to apply when validation succeeds.</param>
    /// <returns>A validation result whose profile is non-null only when no blocker diagnostics were found.</returns>
    public ProfileValidationResult ValidateProfileJson(string json, ProfileValidationContext context)
    {
        if (json is null) throw new ArgumentNullException(nameof(json));
        if (context is null) throw new ArgumentNullException(nameof(context));

        try
        {
            using var document = JsonDocument.Parse(json);
            return ValidateRoot(document.RootElement, context);
        }
        catch (JsonException exception)
        {
            return Invalid(Blocker("InvalidJson", $"Profile JSON is malformed: {exception.Message}", null, null));
        }
    }

    /// <summary>
    /// Exports a validated custom profile definition to deterministic standalone JSON for local profile storage and sharing.
    /// </summary>
    /// <param name="profile">Profile definition to export.</param>
    /// <returns>UTF-8-compatible JSON text with LF newlines, deterministic table ordering, and no trailing newline.</returns>
    public string ExportProfileJson(CustomProfileDefinition profile)
    {
        if (profile is null) throw new ArgumentNullException(nameof(profile));

        using var stream = new MemoryStream();
        using (var writer = new Utf8JsonWriter(stream, new JsonWriterOptions
        {
            Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
            Indented = true,
        }))
        {
            writer.WriteStartObject();
            writer.WriteNumber("Version", SupportedVersion);
            writer.WriteString("Name", profile.Name);
            writer.WriteString("Game", profile.Game ?? string.Empty);
            WriteDefaults(writer, profile.SliderProfile.Defaults);
            WriteMultipliers(writer, profile.SliderProfile.Multipliers);
            WriteInverted(writer, profile.SliderProfile.InvertedNames);
            writer.WriteEndObject();
        }

        // Utf8JsonWriter uses the current environment's newline for indented output, so normalize custom profile exports to LF for byte-stable sharing.
        return Encoding.UTF8.GetString(stream.ToArray()).Replace("\r\n", "\n", StringComparison.Ordinal);
    }

    private static ProfileValidationResult ValidateRoot(JsonElement root, ProfileValidationContext context)
    {
        var diagnostics = new List<ProfileValidationDiagnostic>();
        if (root.ValueKind != JsonValueKind.Object)
        {
            diagnostics.Add(Blocker("InvalidRoot", "Profile JSON root must be an object.", null, null));
            return Invalid(diagnostics);
        }

        CheckDuplicateProperties(root, null, diagnostics);
        RejectUnknownRootProperties(root, diagnostics);
        ValidateVersion(root, diagnostics);

        var name = ReadRequiredName(root, diagnostics);
        if (name is not null && context.ExistingProfileNames.Contains(name, StringComparer.OrdinalIgnoreCase))
        {
            diagnostics.Add(Blocker("DuplicateProfileName", $"Profile name '{name}' conflicts with an existing profile.", null, name));
        }

        var game = ReadOptionalString(root, "Game", diagnostics) ?? string.Empty;
        var defaults = ReadDefaults(root, diagnostics);
        var multipliers = ReadMultipliers(root, diagnostics);
        var inverted = ReadInverted(root, diagnostics);

        if (diagnostics.Any(diagnostic => diagnostic.Severity == ProfileValidationSeverity.Blocker))
        {
            return Invalid(diagnostics);
        }

        return new ProfileValidationResult(
            new CustomProfileDefinition(
                name!,
                game,
                new SliderProfile(defaults, multipliers, inverted),
                context.SourceKind,
                context.FilePath),
            diagnostics);
    }

    private static void RejectUnknownRootProperties(JsonElement root, List<ProfileValidationDiagnostic> diagnostics)
    {
        var allowed = new HashSet<string>(StringComparer.Ordinal)
        {
            "Version",
            "Name",
            "Game",
            "Defaults",
            "Multipliers",
            "Inverted",
        };

        foreach (var property in root.EnumerateObject())
        {
            if (!allowed.Contains(property.Name))
            {
                diagnostics.Add(Blocker("UnknownProperty", $"Unknown root property '{property.Name}' is not part of profile schema version 1.", null, property.Name));
            }
        }
    }

    private static void ValidateVersion(JsonElement root, List<ProfileValidationDiagnostic> diagnostics)
    {
        if (!root.TryGetProperty("Version", out var versionElement)) return;

        if (versionElement.ValueKind != JsonValueKind.Number || !versionElement.TryGetInt32(out var version))
        {
            diagnostics.Add(Blocker("InvalidVersion", "Profile Version must be the integer 1 when present.", null, null));
            return;
        }

        if (version != SupportedVersion)
        {
            diagnostics.Add(Blocker("UnsupportedVersion", $"Profile Version {version} is not supported; only Version 1 can be imported.", null, null));
        }
    }

    private static string? ReadRequiredName(JsonElement root, List<ProfileValidationDiagnostic> diagnostics)
    {
        if (!root.TryGetProperty("Name", out var nameElement))
        {
            diagnostics.Add(Blocker("MissingName", "Profile JSON must include a nonblank Name property.", null, null));
            return null;
        }

        if (nameElement.ValueKind != JsonValueKind.String)
        {
            diagnostics.Add(Blocker("InvalidName", "Profile Name must be a nonblank string.", null, null));
            return null;
        }

        var name = nameElement.GetString();
        if (string.IsNullOrWhiteSpace(name))
        {
            diagnostics.Add(Blocker("BlankProfileName", "Profile Name must not be blank.", null, null));
            return null;
        }

        return name;
    }

    private static string? ReadOptionalString(JsonElement root, string propertyName, List<ProfileValidationDiagnostic> diagnostics)
    {
        if (!root.TryGetProperty(propertyName, out var element)) return null;
        if (element.ValueKind == JsonValueKind.Null) return string.Empty;
        if (element.ValueKind == JsonValueKind.String) return element.GetString() ?? string.Empty;

        diagnostics.Add(Blocker("InvalidMetadata", $"Profile {propertyName} must be a string when present.", null, propertyName));
        return null;
    }

    private static IReadOnlyList<SliderDefault> ReadDefaults(JsonElement root, List<ProfileValidationDiagnostic> diagnostics)
    {
        const string table = "Defaults";
        if (!TryGetObjectTable(root, table, diagnostics, out var defaultsElement)) return Array.Empty<SliderDefault>();

        CheckDuplicateProperties(defaultsElement, table, diagnostics);
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var defaults = new List<SliderDefault>();
        foreach (var property in defaultsElement.EnumerateObject())
        {
            if (!ValidateSliderName(property.Name, table, seen, diagnostics)) continue;
            if (property.Value.ValueKind != JsonValueKind.Object)
            {
                diagnostics.Add(Blocker("InvalidDefaultEntry", "Defaults entries must be objects with valueSmall and valueBig numeric properties.", table, property.Name));
                continue;
            }

            CheckDuplicateProperties(property.Value, table, diagnostics);
            var hasSmall = TryReadNumberProperty(property.Value, "valueSmall", table, property.Name, diagnostics, out var valueSmall);
            var hasBig = TryReadNumberProperty(property.Value, "valueBig", table, property.Name, diagnostics, out var valueBig);
            if (hasSmall && hasBig)
            {
                defaults.Add(new SliderDefault(property.Name, valueSmall, valueBig));
            }
        }

        return defaults;
    }

    private static IReadOnlyList<SliderMultiplier> ReadMultipliers(JsonElement root, List<ProfileValidationDiagnostic> diagnostics)
    {
        const string table = "Multipliers";
        if (!TryGetObjectTable(root, table, diagnostics, out var multipliersElement)) return Array.Empty<SliderMultiplier>();

        CheckDuplicateProperties(multipliersElement, table, diagnostics);
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var multipliers = new List<SliderMultiplier>();
        foreach (var property in multipliersElement.EnumerateObject())
        {
            if (!ValidateSliderName(property.Name, table, seen, diagnostics)) continue;
            if (TryReadNumber(property.Value, table, property.Name, diagnostics, out var value))
            {
                multipliers.Add(new SliderMultiplier(property.Name, value));
            }
        }

        return multipliers;
    }

    private static IReadOnlyList<string> ReadInverted(JsonElement root, List<ProfileValidationDiagnostic> diagnostics)
    {
        const string table = "Inverted";
        if (!root.TryGetProperty(table, out var invertedElement)) return Array.Empty<string>();
        if (invertedElement.ValueKind != JsonValueKind.Array)
        {
            diagnostics.Add(Blocker("InvalidTable", "Inverted must be an array of nonblank strings.", table, null));
            return Array.Empty<string>();
        }

        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var inverted = new List<string>();
        foreach (var item in invertedElement.EnumerateArray())
        {
            if (item.ValueKind != JsonValueKind.String)
            {
                diagnostics.Add(Blocker("InvalidSliderName", "Inverted entries must be strings.", table, null));
                continue;
            }

            var name = item.GetString() ?? string.Empty;
            if (!ValidateSliderName(name, table, seen, diagnostics)) continue;
            inverted.Add(name);
        }

        return inverted;
    }

    private static bool TryGetObjectTable(JsonElement root, string table, List<ProfileValidationDiagnostic> diagnostics, out JsonElement tableElement)
    {
        if (!root.TryGetProperty(table, out tableElement)) return false;
        if (tableElement.ValueKind == JsonValueKind.Object) return true;

        diagnostics.Add(Blocker("InvalidTable", $"{table} must be a JSON object.", table, null));
        return false;
    }

    private static bool ValidateSliderName(string name, string table, HashSet<string> seen, List<ProfileValidationDiagnostic> diagnostics)
    {
        if (string.IsNullOrWhiteSpace(name))
        {
            diagnostics.Add(Blocker("BlankSliderName", $"{table} contains a blank slider name.", table, name));
            return false;
        }

        if (!seen.Add(name))
        {
            diagnostics.Add(Blocker("DuplicateSliderName", $"{table} contains duplicate slider name '{name}'.", table, name));
            return false;
        }

        return true;
    }

    private static bool TryReadNumberProperty(JsonElement parent, string propertyName, string table, string sliderName, List<ProfileValidationDiagnostic> diagnostics, out float value)
    {
        value = 0f;
        if (!parent.TryGetProperty(propertyName, out var element))
        {
            diagnostics.Add(Blocker("MissingNumber", $"Defaults entry '{sliderName}' must include {propertyName}.", table, sliderName));
            return false;
        }

        return TryReadNumber(element, table, sliderName, diagnostics, out value);
    }

    private static bool TryReadNumber(JsonElement element, string table, string sliderName, List<ProfileValidationDiagnostic> diagnostics, out float value)
    {
        value = 0f;
        if (element.ValueKind != JsonValueKind.Number)
        {
            diagnostics.Add(Blocker("InvalidNumber", $"{table} value for '{sliderName}' must be numeric.", table, sliderName));
            return false;
        }

        if (!element.TryGetSingle(out value))
        {
            diagnostics.Add(Blocker("NonFiniteNumber", $"{table} value for '{sliderName}' must be a finite single-precision number.", table, sliderName));
            return false;
        }

        if (float.IsNaN(value) || float.IsInfinity(value))
        {
            diagnostics.Add(Blocker("NonFiniteNumber", $"{table} value for '{sliderName}' must be finite.", table, sliderName));
            return false;
        }

        return true;
    }

    private static void CheckDuplicateProperties(JsonElement element, string? table, List<ProfileValidationDiagnostic> diagnostics)
    {
        if (element.ValueKind != JsonValueKind.Object) return;

        var seen = new HashSet<string>(StringComparer.Ordinal);
        foreach (var property in element.EnumerateObject())
        {
            if (!seen.Add(property.Name))
            {
                diagnostics.Add(Blocker("DuplicateProperty", $"Duplicate JSON property '{property.Name}' is ambiguous and is not allowed.", table, property.Name));
            }
        }
    }

    private static void WriteDefaults(Utf8JsonWriter writer, IEnumerable<SliderDefault> defaults)
    {
        writer.WritePropertyName("Defaults");
        writer.WriteStartObject();
        foreach (var item in defaults.OrderBy(value => value.Name, StringComparer.OrdinalIgnoreCase))
        {
            writer.WritePropertyName(item.Name);
            writer.WriteStartObject();
            writer.WriteNumber("valueSmall", item.ValueSmall);
            writer.WriteNumber("valueBig", item.ValueBig);
            writer.WriteEndObject();
        }

        writer.WriteEndObject();
    }

    private static void WriteMultipliers(Utf8JsonWriter writer, IEnumerable<SliderMultiplier> multipliers)
    {
        writer.WritePropertyName("Multipliers");
        writer.WriteStartObject();
        foreach (var item in multipliers.OrderBy(value => value.Name, StringComparer.OrdinalIgnoreCase))
        {
            writer.WriteNumber(item.Name, item.Value);
        }

        writer.WriteEndObject();
    }

    private static void WriteInverted(Utf8JsonWriter writer, IEnumerable<string> inverted)
    {
        writer.WritePropertyName("Inverted");
        writer.WriteStartArray();
        foreach (var item in inverted.OrderBy(value => value, StringComparer.OrdinalIgnoreCase))
        {
            writer.WriteStringValue(item);
        }

        writer.WriteEndArray();
    }

    private static ProfileValidationDiagnostic Blocker(string code, string message, string? table, string? sliderName) =>
        new(ProfileValidationSeverity.Blocker, code, message, table, sliderName);

    private static ProfileValidationResult Invalid(params ProfileValidationDiagnostic[] diagnostics) =>
        Invalid((IEnumerable<ProfileValidationDiagnostic>)diagnostics);

    private static ProfileValidationResult Invalid(IEnumerable<ProfileValidationDiagnostic> diagnostics) =>
        new(null, diagnostics);
}
