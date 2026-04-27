using BS2BG.App.Services;
using BS2BG.Core.Formatting;
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
    /// Verifies legacy catalog construction wraps bundled profiles with read-only source metadata.
    /// </summary>
    [Fact]
    public void ConstructorWrapsBundledProfilesAsReadOnlyEntries()
    {
        var profile = new TemplateProfile("Custom Body", new SliderProfile([], [], []));

        var catalog = new TemplateProfileCatalog(new[] { profile });

        catalog.Entries.Should().ContainSingle();
        catalog.Entries[0].Name.Should().Be("Custom Body");
        catalog.Entries[0].TemplateProfile.Should().BeSameAs(profile);
        catalog.Entries[0].SourceKind.Should().Be(ProfileSourceKind.Bundled);
        catalog.Entries[0].FilePath.Should().BeNull();
        catalog.Entries[0].IsEditable.Should().BeFalse();
        catalog.ProfileNames.Should().Equal("Custom Body");
        catalog.GetProfile("custom body").Should().BeSameAs(profile);
    }

    /// <summary>
    /// Verifies catalog source metadata can describe editable local profiles without changing lookup behavior.
    /// </summary>
    [Fact]
    public void EntryConstructorPreservesLookupAndEditableMetadata()
    {
        var bundled = new TemplateProfile("Bundled Body", new SliderProfile([], [], []));
        var custom = new TemplateProfile("Local Body", new SliderProfile([], [], []));

        var catalog = new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", bundled, ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Local Body", custom, ProfileSourceKind.LocalCustom, "C:/profiles/local.json", true),
        });

        catalog.Profiles.Should().Equal(bundled, custom);
        catalog.ProfileNames.Should().Equal("Bundled Body", "Local Body");
        catalog.DefaultProfile.Should().BeSameAs(bundled);
        catalog.ContainsProfile("LOCAL BODY").Should().BeTrue();
        catalog.GetProfile("local body").Should().BeSameAs(custom);
        catalog.Entries[1].SourceKind.Should().Be(ProfileSourceKind.LocalCustom);
        catalog.Entries[1].FilePath.Should().Be("C:/profiles/local.json");
        catalog.Entries[1].IsEditable.Should().BeTrue();
    }

    /// <summary>
    /// Verifies custom entries cannot shadow bundled names through case-only differences.
    /// </summary>
    [Fact]
    public void EntryConstructorRejectsCaseInsensitiveDuplicateNames()
    {
        var first = new TemplateProfile("Body", new SliderProfile([], [], []));
        var second = new TemplateProfile("body", new SliderProfile([], [], []));

        var action = () => new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Body", first, ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("body", second, ProfileSourceKind.LocalCustom, "C:/profiles/body.json", true),
        });

        action.Should().Throw<ArgumentException>().WithMessage("*duplicate*Body*body*");
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
