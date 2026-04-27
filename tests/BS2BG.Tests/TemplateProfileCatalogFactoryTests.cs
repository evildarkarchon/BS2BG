using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;
using SliderPreset = BS2BG.Core.Models.SliderPreset;

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

    /// <summary>
    /// Verifies instance catalog composition appends valid local custom profiles after bundled entries.
    /// </summary>
    [Fact]
    public void InstanceFactoryAppendsLocalCustomProfilesAfterBundledEntries()
    {
        var custom = CreateCustomProfile("Local Body", "C:/profiles/local.json");
        var factory = new TemplateProfileCatalogFactory(new StubUserProfileStore([custom]));

        var result = factory.Create();

        result.Catalog.ProfileNames.Should().Contain("Local Body");
        result.Catalog.Entries.Take(3).Should().OnlyContain(entry => entry.SourceKind == ProfileSourceKind.Bundled);
        result.Catalog.Entries.Last().SourceKind.Should().Be(ProfileSourceKind.LocalCustom);
        result.Catalog.Entries.Last().IsEditable.Should().BeTrue();
    }

    /// <summary>
    /// Verifies bundled-name collisions are passed to discovery so local custom files cannot replace bundled math.
    /// </summary>
    [Fact]
    public void InstanceFactorySkipsBundledNameDuplicatesWithoutReplacingBundledProfile()
    {
        var custom = CreateCustomProfile(ProjectProfileMapping.SkyrimCbbe, "C:/profiles/cbbe.json");
        var factory = new TemplateProfileCatalogFactory(new StubUserProfileStore([custom]));

        var result = factory.Create();

        result.Catalog.Entries.Where(entry => string.Equals(entry.Name, ProjectProfileMapping.SkyrimCbbe, StringComparison.OrdinalIgnoreCase))
            .Should().ContainSingle()
            .Which.SourceKind.Should().Be(ProfileSourceKind.Bundled);
        result.DiscoveryDiagnostics.Should().Contain(diagnostic => diagnostic.Code == "DuplicateProfileName");
    }

    /// <summary>
    /// Verifies existing ViewModels observe refreshes through the catalog service rather than a stale singleton catalog.
    /// </summary>
    [Fact]
    public void CatalogServiceRefreshPublishesProfilesForExistingTemplatesPreview()
    {
        var store = new StubUserProfileStore([]);
        var service = new TemplateProfileCatalogService(new TemplateProfileCatalogFactory(store));
        var project = new ProjectModel();
        var templates = new TemplatesViewModel(
            project,
            new BS2BG.Core.Import.BodySlideXmlParser(),
            new TemplateGenerationService(),
            service,
            new EmptyBodySlideXmlFilePicker(),
            new EmptyClipboardService());
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Local Body" });
        templates.SelectedPreset = project.SliderPresets[0];
        store.Profiles = [CreateCustomProfile("Local Body", "C:/profiles/local.json")];

        service.Refresh();
        templates.SelectedProfileName = "Local Body";
        templates.GenerateTemplates();

        templates.ProfileNames.Should().Contain("Local Body");
        templates.GeneratedTemplateText.Should().Contain("Preset");
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

    private static CustomProfileDefinition CreateCustomProfile(string name, string filePath) => new(
        name,
        "Skyrim",
        new SliderProfile([new SliderDefault("CustomSlider", 0f, 1f)], [], []),
        ProfileSourceKind.LocalCustom,
        filePath);

    private sealed class StubUserProfileStore(IReadOnlyList<CustomProfileDefinition> profiles) : IUserProfileStore
    {
        public IReadOnlyList<CustomProfileDefinition> Profiles { get; set; } = profiles;

        public UserProfileDiscoveryResult DiscoverProfiles() => DiscoverProfiles(Array.Empty<string>());

        public UserProfileDiscoveryResult DiscoverProfiles(IEnumerable<string> existingProfileNames)
        {
            var existing = new HashSet<string>(existingProfileNames, StringComparer.OrdinalIgnoreCase);
            var accepted = new List<CustomProfileDefinition>();
            var diagnostics = new List<ProfileValidationDiagnostic>();
            foreach (var profile in Profiles)
            {
                if (!existing.Add(profile.Name))
                {
                    diagnostics.Add(new ProfileValidationDiagnostic(
                        ProfileValidationSeverity.Blocker,
                        "DuplicateProfileName",
                        "Duplicate profile name.",
                        null,
                        profile.Name));
                    continue;
                }

                accepted.Add(profile);
            }

            return new UserProfileDiscoveryResult(accepted, diagnostics);
        }

        public UserProfileSaveResult SaveProfile(CustomProfileDefinition profile) =>
            new(false, null, Array.Empty<ProfileValidationDiagnostic>());

        public UserProfileDeleteResult DeleteProfile(CustomProfileDefinition profile) =>
            new(false, null, Array.Empty<ProfileValidationDiagnostic>());

        public string GetDefaultProfileDirectory() => string.Empty;
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
    }

    private sealed class EmptyClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken) => Task.CompletedTask;
    }
}
