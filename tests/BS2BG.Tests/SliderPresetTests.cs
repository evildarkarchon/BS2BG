using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class SliderPresetTests
{
    private static readonly string[] MissingDefaultSliderNames = { "DefaultOne" };


    [Fact]
    public void ConstructorAcceptsPlainName()
    {
        var preset = new SliderPreset("Alpha");

        preset.Name.Should().Be("Alpha");
    }

    [Fact]
    public void ConstructorReplacesDotsWithSpaces()
    {
        var preset = new SliderPreset("Foo.Bar");

        preset.Name.Should().Be("Foo Bar");
    }

    [Theory]
    [InlineData("foo|bar", "'|'")]
    [InlineData("foo=bar", "'='")]
    [InlineData("foo,bar", "','")]
    [InlineData("foo\rbar", "carriage return")]
    [InlineData("foo\nbar", "line feed")]
    public void ConstructorThrowsForForbiddenCharacter(string name, string expectedDescription)
    {
        var act = () => new SliderPreset(name);

        act.Should().Throw<ArgumentException>()
            .Which.Message.Should().Contain(expectedDescription);
    }

    [Fact]
    public void NameSetterThrowsForForbiddenCharacter()
    {
        var preset = new SliderPreset("Alpha");

        var act = () => preset.Name = "foo|bar";

        act.Should().Throw<ArgumentException>()
            .Which.Message.Should().Contain("'|'");
    }

    [Fact]
    public void TryValidateNameAcceptsPlainName()
    {
        var ok = SliderPreset.TryValidateName("Alpha", out var error);

        ok.Should().BeTrue();
        error.Should().BeEmpty();
    }

    [Theory]
    [InlineData("foo|bar", "'|'")]
    [InlineData("foo=bar", "'='")]
    [InlineData("foo,bar", "','")]
    [InlineData("foo\rbar", "carriage return")]
    [InlineData("foo\nbar", "line feed")]
    public void TryValidateNameRejectsForbiddenCharacter(string name, string expectedDescription)
    {
        var ok = SliderPreset.TryValidateName(name, out var error);

        ok.Should().BeFalse();
        error.Should().Contain(expectedDescription);
    }

    [Fact]
    public void TryValidateNameTreatsNullAsEmpty()
    {
        var ok = SliderPreset.TryValidateName(null!, out var error);

        ok.Should().BeTrue();
        error.Should().BeEmpty();
    }

    [Fact]
    public void CloneCreatesIndependentCopy()
    {
        var original = new SliderPreset("Alpha");
        var slider = new SetSlider("Test")
        {
            ValueSmall = 0,
            ValueBig = 50,
            PercentMin = 75,
            PercentMax = 80,
            Enabled = false
        };
        original.AddSetSlider(slider);

        var clone = original.Clone();

        clone.Should().NotBeSameAs(original);
        clone.Name.Should().Be("Alpha");
        clone.ProfileName.Should().Be(original.ProfileName);
        clone.SetSliders.Should().HaveCount(1);
        clone.SetSliders[0].Should().NotBeSameAs(slider);
        clone.SetSliders[0].Name.Should().Be("Test");
        clone.SetSliders[0].ValueSmall.Should().Be(0);
        clone.SetSliders[0].ValueBig.Should().Be(50);
        clone.SetSliders[0].PercentMin.Should().Be(75);
        clone.SetSliders[0].PercentMax.Should().Be(80);
        clone.SetSliders[0].Enabled.Should().BeFalse();

        slider.ValueBig = 999;
        clone.SetSliders[0].ValueBig.Should().Be(50);
    }

    [Fact]
    public void CloneWithNewNameOverridesOnlyTheName()
    {
        var original = new SliderPreset("Alpha");
        original.AddSetSlider(new SetSlider("Slider1") { ValueSmall = 0, ValueBig = 10 });

        var clone = original.Clone("Beta");

        clone.Name.Should().Be("Beta");
        clone.ProfileName.Should().Be(original.ProfileName);
        clone.SetSliders.Should().ContainSingle().Which.Name.Should().Be("Slider1");
    }

    [Fact]
    public void ClonePreservesMissingDefaultSliders()
    {
        var original = new SliderPreset("Alpha");
        original.RefreshMissingDefaultSetSliders(MissingDefaultSliderNames);

        var clone = original.Clone();

        clone.MissingDefaultSetSliders.Should().ContainSingle()
            .Which.Name.Should().Be("DefaultOne");
        clone.MissingDefaultSetSliders[0].ValueSmall.Should().BeNull();
        clone.MissingDefaultSetSliders[0].ValueBig.Should().BeNull();
    }
}
