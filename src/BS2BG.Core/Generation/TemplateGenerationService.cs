using ModelPreset = BS2BG.Core.Models.SliderPreset;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using FormatPreset = BS2BG.Core.Formatting.SliderPreset;
using FormatSetSlider = BS2BG.Core.Formatting.SetSlider;
using System.Diagnostics.CodeAnalysis;
using BS2BG.Core.Formatting;

namespace BS2BG.Core.Generation;

[SuppressMessage("Performance", "CA1822:Mark members as static", Justification = "Template generation is exposed as an injectable service surface.")]
public sealed class TemplateGenerationService
{
    public string PreviewTemplate(ModelPreset preset, TemplateProfile profile, bool omitRedundantSliders)
    {
        if (preset is null)
        {
            throw new ArgumentNullException(nameof(preset));
        }

        if (profile is null)
        {
            throw new ArgumentNullException(nameof(profile));
        }

        return SliderMathFormatter.FormatTemplateLine(
            ToFormattingPreset(preset),
            profile.SliderProfile,
            omitRedundantSliders);
    }

    public string GenerateTemplates(
        IEnumerable<ModelPreset> presets,
        TemplateProfileCatalog profileCatalog,
        bool omitRedundantSliders)
    {
        if (presets is null)
        {
            throw new ArgumentNullException(nameof(presets));
        }

        if (profileCatalog is null)
        {
            throw new ArgumentNullException(nameof(profileCatalog));
        }

        var lines = presets
            .OrderBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase)
            .Select(preset => PreviewTemplate(
                preset,
                profileCatalog.GetProfile(preset.ProfileName),
                omitRedundantSliders));

        return string.Join("\n", lines);
    }

    private static FormatPreset ToFormattingPreset(ModelPreset preset)
    {
        return new FormatPreset(
            preset.Name,
            preset.SetSliders
                .Concat(preset.MissingDefaultSetSliders)
                .Select(ToFormattingSetSlider));
    }

    private static FormatSetSlider ToFormattingSetSlider(ModelSetSlider slider)
    {
        return new FormatSetSlider(slider.Name)
        {
            Enabled = slider.Enabled,
            ValueSmall = slider.ValueSmall,
            ValueBig = slider.ValueBig,
            PercentMin = slider.PercentMin,
            PercentMax = slider.PercentMax,
        };
    }
}
