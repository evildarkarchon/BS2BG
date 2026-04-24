using System.Text;

namespace BS2BG.Core.Formatting;

public static class SliderMathFormatter
{
    public static string FormatTemplateLine(SliderPreset preset, SliderProfile profile, bool omitRedundantSliders)
    {
        if (preset is null)
        {
            throw new ArgumentNullException(nameof(preset));
        }

        if (profile is null)
        {
            throw new ArgumentNullException(nameof(profile));
        }

        var values = GetEnabledAndDefaultSliders(preset, profile)
            .Where(slider => !omitRedundantSliders || !IsRedundant(slider, profile))
            .Select(slider => FormatTemplateValue(slider, profile));

        return preset.Name + "=" + string.Join(", ", values);
    }

    public static string FormatBosJson(SliderPreset preset, SliderProfile profile)
    {
        if (preset is null)
        {
            throw new ArgumentNullException(nameof(preset));
        }

        if (profile is null)
        {
            throw new ArgumentNullException(nameof(profile));
        }

        var sliders = GetEnabledAndDefaultSliders(preset, profile)
            .Where(slider => !IsRedundant(slider, profile))
            .Select(slider => CreateBosSlider(slider, profile))
            .ToArray();

        return WriteBosJson(preset.Name, sliders);
    }

    private static string FormatTemplateValue(SetSlider slider, SliderProfile profile)
    {
        var small = ResolveSmall(slider, profile) * 0.01f;
        var big = ResolveBig(slider, profile) * 0.01f;

        if (profile.IsInverted(slider.Name))
        {
            small = 1f - small;
            big = 1f - big;
        }

        var diff = big - small;
        var min = small + (diff * (slider.PercentMin * 0.01f));
        var max = small + (diff * (slider.PercentMax * 0.01f));
        var multiplier = profile.GetMultiplier(slider.Name);

        min = JavaFloatFormatting.RoundHalfUpToTwoDecimals(min * multiplier);
        max = JavaFloatFormatting.RoundHalfUpToTwoDecimals(max * multiplier);

        return min != max
            ? slider.Name + "@" + JavaFloatFormatting.FormatForText(min) + ":" + JavaFloatFormatting.FormatForText(max)
            : slider.Name + "@" + JavaFloatFormatting.FormatForText(max);
    }

    private static BosSlider CreateBosSlider(SetSlider slider, SliderProfile profile)
    {
        var high = ResolveBig(slider, profile) * 0.01f;
        var low = ResolveSmall(slider, profile) * 0.01f;

        if (profile.IsInverted(slider.Name))
        {
            high = 1f - high;
            low = 1f - low;
        }

        var multiplier = profile.GetMultiplier(slider.Name);
        high = JavaFloatFormatting.RoundHalfUpToTwoDecimals(high * multiplier);
        low = JavaFloatFormatting.RoundHalfUpToTwoDecimals(low * multiplier);

        return new BosSlider(slider.Name, high, low);
    }

    private static bool IsRedundant(SetSlider slider, SliderProfile profile)
    {
        var small = ResolveSmall(slider, profile);
        var big = ResolveBig(slider, profile);

        return profile.IsInverted(slider.Name)
            ? small == 100 && small == big
            : small == 0 && small == big;
    }

    private static SetSlider[] GetEnabledAndDefaultSliders(SliderPreset preset, SliderProfile profile)
    {
        var userSliderNames = new HashSet<string>(
            preset.Sliders.Select(slider => slider.Name),
            StringComparer.OrdinalIgnoreCase);
        var sliders = new List<SetSlider>();

        sliders.AddRange(preset.Sliders.Where(slider => slider.Enabled));

        foreach (var defaultValue in profile.Defaults)
        {
            if (!userSliderNames.Contains(defaultValue.Name))
            {
                sliders.Add(new SetSlider(defaultValue.Name));
            }
        }

        return sliders
            .OrderBy(slider => slider.Name, StringComparer.OrdinalIgnoreCase)
            .ToArray();
    }

    private static int ResolveSmall(SetSlider slider, SliderProfile profile)
    {
        return slider.ValueSmall ?? profile.GetDefaultSmall(slider.Name);
    }

    private static int ResolveBig(SetSlider slider, SliderProfile profile)
    {
        return slider.ValueBig ?? profile.GetDefaultBig(slider.Name);
    }

    private static string WriteBosJson(string bodyName, IReadOnlyList<BosSlider> sliders)
    {
        var builder = new StringBuilder();
        builder.AppendLine("{");
        builder.AppendLine("  \"string\": {");
        AppendStringProperty(builder, "bodyname", bodyName, trailingComma: sliders.Count > 0, indent: 4);

        for (var index = 0; index < sliders.Count; index++)
        {
            AppendStringProperty(
                builder,
                "slidername" + (index + 1).ToString(System.Globalization.CultureInfo.InvariantCulture),
                sliders[index].Name,
                trailingComma: index < sliders.Count - 1,
                indent: 4);
        }

        builder.AppendLine("  },");
        builder.AppendLine("  \"int\": {");
        AppendNumberProperty(
            builder,
            "slidersnumber",
            sliders.Count.ToString(System.Globalization.CultureInfo.InvariantCulture),
            trailingComma: false,
            indent: 4);
        builder.AppendLine("  },");
        builder.AppendLine("  \"float\": {");

        for (var index = 0; index < sliders.Count; index++)
        {
            AppendNumberProperty(
                builder,
                "highvalue" + (index + 1).ToString(System.Globalization.CultureInfo.InvariantCulture),
                JavaFloatFormatting.FormatForMinimalJsonNumber(sliders[index].High),
                trailingComma: true,
                indent: 4);
        }

        for (var index = 0; index < sliders.Count; index++)
        {
            AppendNumberProperty(
                builder,
                "lowvalue" + (index + 1).ToString(System.Globalization.CultureInfo.InvariantCulture),
                JavaFloatFormatting.FormatForMinimalJsonNumber(sliders[index].Low),
                trailingComma: index < sliders.Count - 1,
                indent: 4);
        }

        builder.AppendLine("  }");
        builder.Append('}');

        return builder.ToString();
    }

    private static void AppendStringProperty(
        StringBuilder builder,
        string name,
        string value,
        bool trailingComma,
        int indent)
    {
        AppendIndent(builder, indent);
        builder.Append('"');
        builder.Append(EscapeJsonString(name));
        builder.Append("\": \"");
        builder.Append(EscapeJsonString(value));
        builder.Append('"');
        AppendCommaAndLine(builder, trailingComma);
    }

    private static void AppendNumberProperty(
        StringBuilder builder,
        string name,
        string value,
        bool trailingComma,
        int indent)
    {
        AppendIndent(builder, indent);
        builder.Append('"');
        builder.Append(EscapeJsonString(name));
        builder.Append("\": ");
        builder.Append(value);
        AppendCommaAndLine(builder, trailingComma);
    }

    private static void AppendIndent(StringBuilder builder, int indent)
    {
        builder.Append(' ', indent);
    }

    private static void AppendCommaAndLine(StringBuilder builder, bool trailingComma)
    {
        if (trailingComma)
        {
            builder.Append(',');
        }

        builder.AppendLine();
    }

    private static string EscapeJsonString(string value)
    {
        var builder = new StringBuilder(value.Length);

        foreach (var character in value)
        {
            switch (character)
            {
                case '"':
                    builder.Append("\\\"");
                    break;
                case '\\':
                    builder.Append("\\\\");
                    break;
                case '\b':
                    builder.Append("\\b");
                    break;
                case '\f':
                    builder.Append("\\f");
                    break;
                case '\n':
                    builder.Append("\\n");
                    break;
                case '\r':
                    builder.Append("\\r");
                    break;
                case '\t':
                    builder.Append("\\t");
                    break;
                default:
                    if (character < ' ')
                    {
                        builder.Append("\\u");
                        builder.Append(((int)character).ToString("x4", System.Globalization.CultureInfo.InvariantCulture));
                    }
                    else
                    {
                        builder.Append(character);
                    }

                    break;
            }
        }

        return builder.ToString();
    }

    private sealed class BosSlider
    {
        public BosSlider(string name, float high, float low)
        {
            Name = name;
            High = high;
            Low = low;
        }

        public string Name { get; }

        public float High { get; }

        public float Low { get; }
    }
}
