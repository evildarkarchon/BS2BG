using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class TemplateProfileCatalogTests
{
    [Fact]
    public void GetProfileFallsBackForUnbundledProfileNameWithoutChangingDefaultProfile()
    {
        var catalog = CreateCatalog();

        var profile = catalog.GetProfile("Community CBBE");

        profile.Should().BeSameAs(catalog.DefaultProfile);
        profile.Name.Should().Be(ProjectProfileMapping.SkyrimCbbe);
    }

    [Theory]
    [InlineData("Community CBBE", false)]
    [InlineData("Skyrim CBBE", true)]
    [InlineData("skyrim cbbe", true)]
    [InlineData("Skyrim UUNP", true)]
    [InlineData(null, false)]
    [InlineData("   ", false)]
    public void ContainsProfileReportsOnlyBundledProfileNames(string? profileName, bool expected)
    {
        var catalog = CreateCatalog();

        catalog.ContainsProfile(profileName).Should().Be(expected);
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        var emptyProfile = new SliderProfile(
            Array.Empty<SliderDefault>(),
            Array.Empty<SliderMultiplier>(),
            Array.Empty<string>());

        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, emptyProfile),
            new TemplateProfile(ProjectProfileMapping.SkyrimUunp, emptyProfile)
        });
    }
}
