using System.Globalization;
using System.Text.Json;
using System.Xml.Linq;
using BS2BG.Core.Formatting;
using Xunit;

namespace BS2BG.Tests;

public sealed class SliderMathFormatterTests
{
    private static string RepositoryRoot
    {
        get
        {
            var directory = new DirectoryInfo(AppContext.BaseDirectory);
            while (directory is not null && !File.Exists(Path.Combine(directory.FullName, "PRD.md")))
                directory = directory.Parent;

            return directory?.FullName
                   ?? throw new InvalidOperationException("Could not locate repository root.");
        }
    }

    [Theory]
    [InlineData(0f, "0.0", "0")]
    [InlineData(1f, "1.0", "1")]
    [InlineData(0.2f, "0.2", "0.2")]
    [InlineData(0.6f, "0.6", "0.6")]
    [InlineData(0.75f, "0.75", "0.75")]
    [InlineData(1.25f, "1.25", "1.25")]
    [InlineData(-0.25f, "-0.25", "-0.25")]
    public void FloatFormattersPreserveJavaTextAndMinimalJsonDifferences(
        float value,
        string expectedText,
        string expectedJson)
    {
        JavaFloatFormatting.FormatForText(value).Should().Be(expectedText);
        JavaFloatFormatting.FormatForMinimalJsonNumber(value).Should().Be(expectedJson);
    }

    [Theory]
    [InlineData(0.005f, "0.01")]
    [InlineData(-0.005f, "-0.01")]
    [InlineData(1.234f, "1.23")]
    [InlineData(1.235f, "1.24")]
    public void RoundHalfUpToTwoDecimalsMatchesJavaBigDecimal(float value, string expected)
    {
        var rounded = JavaFloatFormatting.RoundHalfUpToTwoDecimals(value);

        JavaFloatFormatting.FormatForText(rounded).Should().Be(expected);
    }

    [Theory]
    [InlineData("AllCases", false,
        "AllCases = Ankles@0.0, Arms@0.0, Breasts@0.0, BreastsSmall@0.0, Butt@1.0, ButtCrack@0.0, ButtSmall@0.0, Legs@0.0, NippleDistance@0.0, NippleSize@0.0, ShoulderWidth@0.0, Waist@0.6")]
    [InlineData("AllCases", true, "AllCases = Arms@0.0, Butt@1.0, Legs@0.0, Waist@0.6")]
    [InlineData("Negatives", false,
        "Negatives = Ankles@0.0, Arms@0.0, Breasts@1.25, BreastsSmall@0.0, Butt@0.0, ButtCrack@0.0, ButtSmall@0.0, Legs@0.2, NippleDistance@0.0, NippleSize@0.0, ShoulderWidth@0.0, Waist@1.0")]
    [InlineData("Negatives", true, "Negatives = Arms@0.0, Breasts@1.25, Butt@0.0, Legs@0.2, Waist@1.0")]
    [InlineData("MissingDef", false,
        "MissingDef = Ankles@0.0, Arms@0.0, Breasts@0.0, BreastsSmall@0.0, Butt@0.0, ButtCrack@0.0, ButtSmall@0.0, Legs@0.0, NipBGone@1.0, NippleDistance@0.0, NippleSize@0.0, ShoulderWidth@0.0, Waist@1.0")]
    [InlineData("MissingDef", true, "MissingDef = Arms@0.0, Breasts@0.0, Butt@0.0, Legs@0.0, NipBGone@1.0, Waist@1.0")]
    public void MinimalFixtureTemplateLinesMatchJavaWalkthrough(string presetName, bool omitRedundant, string expected)
    {
        var profile = LoadProfile("settings.json");
        var preset = LoadPresets("minimal", "minimal.xml")
            .Single(preset => preset.Name == presetName);

        var actual = SliderMathFormatter.FormatTemplateLine(preset, profile, omitRedundant);

        actual.Should().Be(expected);
    }

    [Fact]
    public void TemplateLineAppliesPercentInterpolationBeforeMultiplier()
    {
        var profile = new SliderProfile(
            Array.Empty<SliderDefault>(),
            new[] { new SliderMultiplier("Scale", 2f) },
            Array.Empty<string>());
        var preset = new SliderPreset(
            "Scaled",
            new[] { new SetSlider("Scale") { ValueSmall = 0, ValueBig = 100, PercentMin = 25, PercentMax = 75 } });

        var actual = SliderMathFormatter.FormatTemplateLine(preset, profile, false);

        actual.Should().Be("Scaled = Scale@0.5:1.5");
    }

    [Theory]
    [InlineData("minimal", "minimal.xml", "settings.json")]
    [InlineData("skyrim-cbbe", "CBBE.xml", "settings.json")]
    [InlineData("fallout4-cbbe", "CBBE.xml", "settings.json")]
    [InlineData("skyrim-uunp", "UUNP-synthetic.xml", "settings.json")]
    public void TemplateFilesMatchJavaFixtures(string scenario, string xmlFileName, string profileFileName)
    {
        var profile = LoadProfile(profileFileName);
        var presets = LoadPresets(scenario, xmlFileName);

        AssertFixtureText(
            scenario,
            "templates.ini",
            FormatTemplates(presets, profile, false));
        AssertFixtureText(
            scenario,
            "templates-omit.ini",
            FormatTemplates(presets, profile, true));
    }

    [Theory]
    [InlineData("minimal", "minimal.xml", "settings.json")]
    [InlineData("skyrim-cbbe", "CBBE.xml", "settings.json")]
    [InlineData("fallout4-cbbe", "CBBE.xml", "settings.json")]
    [InlineData("skyrim-uunp", "UUNP-synthetic.xml", "settings.json")]
    public void BosJsonFilesMatchJavaFixtures(string scenario, string xmlFileName, string profileFileName)
    {
        var profile = LoadProfile(profileFileName);
        var presets = LoadPresets(scenario, xmlFileName)
            .ToDictionary(preset => preset.Name, StringComparer.OrdinalIgnoreCase);
        var expectedDirectory = Path.Combine(RepositoryRoot, "tests", "fixtures", "expected", scenario, "bos-json");

        foreach (var expectedFile in Directory.EnumerateFiles(expectedDirectory, "*.json").OrderBy(Path.GetFileName))
        {
            var presetName = Path.GetFileNameWithoutExtension(expectedFile);
            var actual = SliderMathFormatter.FormatBosJson(presets[presetName], profile);
            var expected = File.ReadAllText(expectedFile);

            NormalizeNewlines(actual).Should().Be(NormalizeNewlines(expected));
        }
    }

    [Fact]
    public void BosJsonUsesLfOnlyLineEndings()
    {
        var profile = new SliderProfile(
            Array.Empty<SliderDefault>(),
            Array.Empty<SliderMultiplier>(),
            Array.Empty<string>());
        var preset = new SliderPreset(
            "Body",
            new[] { new SetSlider("Scale") { ValueSmall = 0, ValueBig = 50 } });

        var actual = SliderMathFormatter.FormatBosJson(preset, profile);

        actual.Should().Be("{\n" +
                           "  \"string\": {\n" +
                           "    \"bodyname\": \"Body\",\n" +
                           "    \"slidername1\": \"Scale\"\n" +
                           "  },\n" +
                           "  \"int\": {\n" +
                           "    \"slidersnumber\": 1\n" +
                           "  },\n" +
                           "  \"float\": {\n" +
                           "    \"highvalue1\": 0.5,\n" +
                           "    \"lowvalue1\": 0\n" +
                           "  }\n" +
                           "}");
    }

    private static string FormatTemplates(IEnumerable<SliderPreset> presets, SliderProfile profile, bool omitRedundant)
    {
        var lines = presets
            .OrderBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase)
            .Select(preset => SliderMathFormatter.FormatTemplateLine(preset, profile, omitRedundant));

        return string.Join("\n", lines);
    }

    private static void AssertFixtureText(string scenario, string fileName, string actual)
    {
        var expectedPath = Path.Combine(RepositoryRoot, "tests", "fixtures", "expected", scenario, fileName);
        var expected = File.ReadAllText(expectedPath);

        NormalizeNewlines(actual).TrimEnd().Should().Be(NormalizeNewlines(expected).TrimEnd());
    }

    private static List<SliderPreset> LoadPresets(string scenario, string fileName)
    {
        var path = Path.Combine(RepositoryRoot, "tests", "fixtures", "inputs", scenario, fileName);
        var document = XDocument.Load(path);
        var presets = new List<SliderPreset>();

        foreach (var presetElement in document.Root!.Elements("Preset"))
        {
            var sliders = new List<SetSlider>();

            foreach (var sliderElement in presetElement.Elements("SetSlider"))
            {
                var name = RequiredAttribute(sliderElement, "name");
                var slider = sliders.FirstOrDefault(item =>
                    string.Equals(item.Name, name, StringComparison.OrdinalIgnoreCase));

                if (slider is null)
                {
                    slider = new SetSlider(name);
                    sliders.Add(slider);
                }

                var value = int.Parse(RequiredAttribute(sliderElement, "value"), CultureInfo.InvariantCulture);
                if (string.Equals(RequiredAttribute(sliderElement, "size"), "small",
                        StringComparison.OrdinalIgnoreCase))
                    slider.ValueSmall = value;
                else
                    slider.ValueBig = value;
            }

            presets.Add(new SliderPreset(RequiredAttribute(presetElement, "name"), sliders));
        }

        return presets;
    }

    private static SliderProfile LoadProfile(string fileName)
    {
        var path = Path.Combine(RepositoryRoot, "tests", "fixtures", "inputs", "profiles", fileName);
        using var document = JsonDocument.Parse(File.ReadAllText(path));
        var root = document.RootElement;

        var defaults = new List<SliderDefault>();
        foreach (var property in root.GetProperty("Defaults").EnumerateObject())
        {
            var value = property.Value;
            defaults.Add(new SliderDefault(
                property.Name,
                value.GetProperty("valueSmall").GetSingle(),
                value.GetProperty("valueBig").GetSingle()));
        }

        var multipliers = new List<SliderMultiplier>();
        foreach (var property in root.GetProperty("Multipliers").EnumerateObject())
            multipliers.Add(new SliderMultiplier(property.Name, property.Value.GetSingle()));

        var inverted = root.GetProperty("Inverted")
            .EnumerateArray()
            .Select(value => value.GetString()!)
            .ToArray();

        return new SliderProfile(defaults, multipliers, inverted);
    }

    private static string RequiredAttribute(XElement element, string name)
    {
        return element.Attribute(name)?.Value
               ?? throw new InvalidOperationException($"Missing '{name}' attribute on '{element.Name}'.");
    }

    private static string NormalizeNewlines(string value)
    {
        return value.Replace("\r\n", "\n", StringComparison.Ordinal)
            .Replace("\r", "\n", StringComparison.Ordinal);
    }
}
