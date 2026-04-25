using BS2BG.Core.Formatting;
using FluentAssertions;
using Xunit;

namespace BS2BG.Tests;

public sealed class SliderProfileTests
{
    [Theory]
    [InlineData(0f, 0)]
    [InlineData(1f, 100)]
    [InlineData(0.5f, 50)]
    [InlineData(0.2f, 20)]
    [InlineData(0.29f, 29)]
    [InlineData(0.58f, 58)]
    [InlineData(0.01f, 1)]
    [InlineData(0.99f, 99)]
    public void GetDefaultSmallRoundsProfileDefaultsToPercentages(float value, int expected)
    {
        var profile = CreateProfile(new SliderDefault("Scale", value, 0f));

        var actual = profile.GetDefaultSmall("Scale");

        actual.Should().Be(expected);
    }

    [Theory]
    [InlineData(0f, 0)]
    [InlineData(1f, 100)]
    [InlineData(0.5f, 50)]
    [InlineData(0.2f, 20)]
    [InlineData(0.29f, 29)]
    [InlineData(0.58f, 58)]
    [InlineData(0.01f, 1)]
    [InlineData(0.99f, 99)]
    public void GetDefaultBigRoundsProfileDefaultsToPercentages(float value, int expected)
    {
        var profile = CreateProfile(new SliderDefault("Scale", 0f, value));

        var actual = profile.GetDefaultBig("Scale");

        actual.Should().Be(expected);
    }

    [Fact]
    public void UnknownSliderNameReturnsZeroDefaults()
    {
        var profile = CreateProfile(new SliderDefault("Scale", 0.29f, 0.58f));

        profile.GetDefaultSmall("Unknown").Should().Be(0);
        profile.GetDefaultBig("Unknown").Should().Be(0);
    }

    private static SliderProfile CreateProfile(params SliderDefault[] defaults) =>
        new(defaults, Array.Empty<SliderMultiplier>(), Array.Empty<string>());
}
