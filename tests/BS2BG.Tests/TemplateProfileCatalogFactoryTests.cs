using BS2BG.App.Services;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class TemplateProfileCatalogFactoryTests
{
    /// <summary>
    /// Verifies the bundled catalog exposes the three supported display names in UI order.
    /// </summary>
    [Fact]
    public void CreateDefaultExposesBundledProfileNamesInDisplayOrder()
    {
        var catalog = TemplateProfileCatalogFactory.CreateDefault();

        catalog.ProfileNames.Should().Equal(
            ProjectProfileMapping.SkyrimCbbe,
            ProjectProfileMapping.SkyrimUunp,
            ProjectProfileMapping.Fallout4Cbbe);
    }

    /// <summary>
    /// Verifies Fallout 4 CBBE loads FO4-only slider defaults instead of reusing Skyrim tables.
    /// </summary>
    [Fact]
    public void Fallout4CbbeProfileLoadsFo4OnlyDefaults()
    {
        var catalog = TemplateProfileCatalogFactory.CreateDefault();
        var profile = catalog.GetProfile(ProjectProfileMapping.Fallout4Cbbe).SliderProfile;

        profile.GetDefaultSmall("BreastCenterBig").Should().Be(100);
        profile.GetDefaultBig("BreastCenterBig").Should().Be(100);
        profile.GetDefaultSmall("ButtNew").Should().Be(100);
        profile.GetDefaultBig("ButtNew").Should().Be(100);
        profile.GetDefaultSmall("ShoulderTweak").Should().Be(100);
        profile.GetDefaultBig("ShoulderTweak").Should().Be(100);
        profile.GetDefaultSmall("HipBack").Should().Be(100);
        profile.GetDefaultBig("HipBack").Should().Be(100);
        profile.GetDefaultSmall("ChubbyWaist").Should().Be(100);
        profile.GetDefaultBig("ChubbyWaist").Should().Be(100);
    }

    /// <summary>
    /// Verifies the Fallout 4 CBBE seed profile leaves inversion empty and multipliers neutral.
    /// </summary>
    [Fact]
    public void Fallout4CbbeProfileUsesEmptyInvertedListAndNeutralMultipliers()
    {
        var catalog = TemplateProfileCatalogFactory.CreateDefault();
        var profile = catalog.GetProfile(ProjectProfileMapping.Fallout4Cbbe).SliderProfile;

        profile.IsInverted("Ankles").Should().BeFalse();
        profile.IsInverted("BreastCenterBig").Should().BeFalse();
        profile.IsInverted("ButtNew").Should().BeFalse();
        profile.GetMultiplier("Ankles").Should().Be(1.0f);
        profile.GetMultiplier("BreastCenterBig").Should().Be(1.0f);
        profile.GetMultiplier("ButtNew").Should().Be(1.0f);
        profile.GetMultiplier("ShoulderTweak").Should().Be(1.0f);
        profile.GetMultiplier("ChubbyWaist").Should().Be(1.0f);
    }

    /// <summary>
    /// Verifies the distinct FO4-only sliders are absent from both bundled Skyrim profiles.
    /// </summary>
    [Fact]
    public void Fallout4CbbeProfileDoesNotShareFo4OnlyDefaultsWithSkyrimProfiles()
    {
        var catalog = TemplateProfileCatalogFactory.CreateDefault();

        AssertMissingFo4OnlyDefault(catalog, ProjectProfileMapping.SkyrimCbbe, "BreastCenterBig");
        AssertMissingFo4OnlyDefault(catalog, ProjectProfileMapping.SkyrimCbbe, "ButtNew");
        AssertMissingFo4OnlyDefault(catalog, ProjectProfileMapping.SkyrimUunp, "BreastCenterBig");
        AssertMissingFo4OnlyDefault(catalog, ProjectProfileMapping.SkyrimUunp, "ButtNew");
    }

    private static void AssertMissingFo4OnlyDefault(
        TemplateProfileCatalog catalog,
        string profileName,
        string sliderName)
    {
        var profile = catalog.GetProfile(profileName).SliderProfile;

        profile.GetDefaultSmall(sliderName).Should().Be(0);
        profile.GetDefaultBig(sliderName).Should().Be(0);
    }
}
