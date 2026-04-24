using System.Text.Json;
using BS2BG.Core.Formatting;

namespace BS2BG.Core.Generation;

public static class SliderProfileJsonService
{
    public static SliderProfile Load(string path)
    {
        if (path is null) throw new ArgumentNullException(nameof(path));

        return LoadFromString(File.ReadAllText(path));
    }

    public static SliderProfile LoadFromString(string json)
    {
        if (json is null) throw new ArgumentNullException(nameof(json));

        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;
        return new SliderProfile(
            ReadDefaults(root),
            ReadMultipliers(root),
            ReadInverted(root));
    }

    private static IEnumerable<SliderDefault> ReadDefaults(JsonElement root)
    {
        if (!root.TryGetProperty("Defaults", out var defaultsElement)) yield break;

        foreach (var property in defaultsElement.EnumerateObject())
            yield return new SliderDefault(
                property.Name,
                property.Value.GetProperty("valueSmall").GetSingle(),
                property.Value.GetProperty("valueBig").GetSingle());
    }

    private static IEnumerable<SliderMultiplier> ReadMultipliers(JsonElement root)
    {
        if (!root.TryGetProperty("Multipliers", out var multipliersElement)) yield break;

        foreach (var property in multipliersElement.EnumerateObject())
            yield return new SliderMultiplier(property.Name, property.Value.GetSingle());
    }

    private static IEnumerable<string> ReadInverted(JsonElement root)
    {
        if (!root.TryGetProperty("Inverted", out var invertedElement)) yield break;

        foreach (var value in invertedElement.EnumerateArray())
        {
            var name = value.GetString();
            if (!string.IsNullOrWhiteSpace(name)) yield return name;
        }
    }
}
