using System.Diagnostics.CodeAnalysis;
using BS2BG.Core.Import;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected sequences keep parser assertions readable.")]
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

        result.Diagnostics.Should().BeEmpty();
        var preset = result.Presets.Should().ContainSingle().Which;
        preset.Name.Should().Be("CBBE Curvy (Outfit)");

        preset.SetSliders.Select(slider => slider.Name).Should().Equal("Arms", "Breasts");
        var arms = preset.SetSliders.Single(slider => slider.Name == "Arms");
        arms.ValueSmall.Should().Be(-25);
        arms.ValueBig.Should().BeNull();

        var breasts = preset.SetSliders.Single(slider => slider.Name == "Breasts");
        breasts.ValueSmall.Should().BeNull();
        breasts.ValueBig.Should().Be(75);
    }

    [Theory]
    [InlineData("foo|bar", "'|'")]
    [InlineData("foo=bar", "'='")]
    [InlineData("foo,bar", "','")]
    public void ParseStringSkipsPresetWithForbiddenCharacterAndEmitsDiagnostic(string presetName,
        string expectedDescription)
    {
        var xml = $"""
                   <SliderPresets>
                     <Preset name="{presetName}">
                       <SetSlider name="Scale" size="big" value="50"/>
                     </Preset>
                   </SliderPresets>
                   """;
        var parser = new BodySlideXmlParser();

        var result = parser.ParseString(xml, "sample.xml");

        result.Presets.Should().BeEmpty();
        var diagnostic = result.Diagnostics.Should().ContainSingle().Which;
        diagnostic.Source.Should().Be("sample.xml");
        diagnostic.Message.Should().Contain(presetName);
        diagnostic.Message.Should().Contain(expectedDescription);
    }

    [Fact]
    public void ParseStringMergesSparseSliderHalvesByName()
    {
        const string xml = """
                           <SliderPresets>
                             <Preset name="- Zeroed Sliders -">
                               <SetSlider name="Waist" size="small" value="10"/>
                               <SetSlider name="waist" size="big" value="40"/>
                             </Preset>
                           </SliderPresets>
                           """;
        var parser = new BodySlideXmlParser();

        var result = parser.ParseString(xml, "sample.xml");

        var preset = result.Presets.Should().ContainSingle().Which;
        var slider = preset.SetSliders.Should().ContainSingle().Which;
        slider.Name.Should().Be("Waist");
        slider.ValueSmall.Should().Be(10);
        slider.ValueBig.Should().Be(40);
    }
}
