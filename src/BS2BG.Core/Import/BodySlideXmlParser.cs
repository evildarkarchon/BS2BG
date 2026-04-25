using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using System.Xml;
using System.Xml.Linq;
using BS2BG.Core.Models;

namespace BS2BG.Core.Import;

[SuppressMessage("Performance", "CA1822:Mark members as static",
    Justification = "Parser is exposed as an injectable service surface.")]
public sealed class BodySlideXmlParser
{
    public BodySlideXmlImportResult ParseFile(string path)
    {
        if (path is null) throw new ArgumentNullException(nameof(path));

        try
        {
            return ParseDocument(XDocument.Load(path), path);
        }
        catch (Exception ex) when (ex is IOException || ex is UnauthorizedAccessException || ex is XmlException)
        {
            return new BodySlideXmlImportResult(
                Array.Empty<SliderPreset>(),
                new[] { new BodySlideXmlImportDiagnostic(path, ex.Message) });
        }
    }

    public BodySlideXmlImportResult ParseFiles(IEnumerable<string> paths)
    {
        if (paths is null) throw new ArgumentNullException(nameof(paths));

        var presets = new List<SliderPreset>();
        var diagnostics = new List<BodySlideXmlImportDiagnostic>();

        foreach (var path in paths)
        {
            var result = ParseFile(path);
            presets.AddRange(result.Presets);
            diagnostics.AddRange(result.Diagnostics);
        }

        return new BodySlideXmlImportResult(presets, diagnostics);
    }

    public BodySlideXmlImportResult ParseString(string xml, string source)
    {
        if (xml is null) throw new ArgumentNullException(nameof(xml));

        try
        {
            return ParseDocument(XDocument.Parse(xml), source ?? string.Empty);
        }
        catch (XmlException ex)
        {
            return new BodySlideXmlImportResult(
                Array.Empty<SliderPreset>(),
                new[] { new BodySlideXmlImportDiagnostic(source ?? string.Empty, ex.Message) });
        }
    }

    private static BodySlideXmlImportResult ParseDocument(XDocument document, string source)
    {
        var root = document.Root;
        if (root is null || !NameEquals(root, "SliderPresets"))
            return new BodySlideXmlImportResult(
                Array.Empty<SliderPreset>(),
                new[] { new BodySlideXmlImportDiagnostic(source, "Root element is not SliderPresets.") });

        var presets = new List<SliderPreset>();
        var diagnostics = new List<BodySlideXmlImportDiagnostic>();

        foreach (var presetElement in root.Elements().Where(element => NameEquals(element, "Preset")))
        {
            var presetName = presetElement.Attribute("name")?.Value;
            if (string.IsNullOrWhiteSpace(presetName))
            {
                diagnostics.Add(new BodySlideXmlImportDiagnostic(source, "Skipped Preset without a name."));
                continue;
            }

            if (!SliderPreset.TryValidateName(presetName, out var nameError))
            {
                diagnostics.Add(new BodySlideXmlImportDiagnostic(
                    source,
                    "Skipped Preset '" + presetName + "': " + nameError));
                continue;
            }

            var preset = new SliderPreset(presetName);
            var sliders = new List<SetSlider>();

            foreach (var sliderElement in presetElement.Elements().Where(element => NameEquals(element, "SetSlider")))
                AddSetSlider(sliders, sliderElement, source, diagnostics);

            foreach (var slider in sliders) preset.AddSetSlider(slider);

            presets.Add(preset);
        }

        return new BodySlideXmlImportResult(presets, diagnostics);
    }

    private static void AddSetSlider(
        List<SetSlider> sliders,
        XElement sliderElement,
        string source,
        List<BodySlideXmlImportDiagnostic> diagnostics)
    {
        var sliderName = sliderElement.Attribute("name")?.Value;
        if (string.IsNullOrWhiteSpace(sliderName))
        {
            diagnostics.Add(new BodySlideXmlImportDiagnostic(source, "Skipped SetSlider without a name."));
            return;
        }

        var valueText = sliderElement.Attribute("value")?.Value;
        if (!int.TryParse(valueText, NumberStyles.Integer, CultureInfo.InvariantCulture, out var value))
        {
            diagnostics.Add(new BodySlideXmlImportDiagnostic(
                source,
                "Skipped SetSlider '" + sliderName + "' with invalid value."));
            return;
        }

        var slider = sliders.FirstOrDefault(item => string.Equals(
            item.Name,
            sliderName,
            StringComparison.OrdinalIgnoreCase));
        if (slider is null)
        {
            slider = new SetSlider(sliderName);
            sliders.Add(slider);
        }

        var size = sliderElement.Attribute("size")?.Value;
        if (string.Equals(size, "small", StringComparison.OrdinalIgnoreCase))
            slider.ValueSmall = value;
        else
            slider.ValueBig = value;
    }

    private static bool NameEquals(XElement element, string name) =>
        string.Equals(element.Name.LocalName, name, StringComparison.OrdinalIgnoreCase);
}
