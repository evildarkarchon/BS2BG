using System.Diagnostics.CodeAnalysis;
using BS2BG.Core.Import;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments", Justification = "Small expected sequences keep parser assertions readable.")]
public sealed class BodySlideXmlParserTests
{
    [Theory]
    [InlineData("")]
    [InlineData("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")]
    public void ParseStringReadsPresetsWithOrWithoutXmlDeclaration(string declaration)
    {
        var xml = declaration + """
            <SliderPresets>
              <Preset name="CBBE Curvy (Outfit)" set="Ignored">
                <Group name="Ignored"/>
                <SetSlider name="Breasts" size="big" value="75" ignored="true"/>
                <SetSlider name="Arms" size="small" value="-25"/>
                <Unsupported name="Ignored"/>
              </Preset>
            </SliderPresets>
            """;
        var parser = new BodySlideXmlParser();

        var result = parser.ParseString(xml, "sample.xml");

        Assert.Empty(result.Diagnostics);
        var preset = Assert.Single(result.Presets);
        Assert.Equal("CBBE Curvy (Outfit)", preset.Name);

        Assert.Equal(new[] { "Arms", "Breasts" }, preset.SetSliders.Select(slider => slider.Name));
        var arms = preset.SetSliders.Single(slider => slider.Name == "Arms");
        Assert.Equal(-25, arms.ValueSmall);
        Assert.Null(arms.ValueBig);

        var breasts = preset.SetSliders.Single(slider => slider.Name == "Breasts");
        Assert.Null(breasts.ValueSmall);
        Assert.Equal(75, breasts.ValueBig);
    }

    [Fact]
    public void ParseStringMergesSparseSliderHalvesByName()
    {
        const string Xml = """
            <SliderPresets>
              <Preset name="- Zeroed Sliders -">
                <SetSlider name="Waist" size="small" value="10"/>
                <SetSlider name="waist" size="big" value="40"/>
              </Preset>
            </SliderPresets>
            """;
        var parser = new BodySlideXmlParser();

        var result = parser.ParseString(Xml, "sample.xml");

        var preset = Assert.Single(result.Presets);
        var slider = Assert.Single(preset.SetSliders);
        Assert.Equal("Waist", slider.Name);
        Assert.Equal(10, slider.ValueSmall);
        Assert.Equal(40, slider.ValueBig);
    }
}
