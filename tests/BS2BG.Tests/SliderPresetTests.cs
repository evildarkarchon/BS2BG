using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class SliderPresetTests
{
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
}
