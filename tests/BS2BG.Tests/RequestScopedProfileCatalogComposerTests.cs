using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;
using Xunit;

namespace BS2BG.Tests;

public sealed class RequestScopedProfileCatalogComposerTests
{
    [Fact]
    public void BuildForProjectIncludesBundledAndReferencedEmbeddedProfilesInFirstSeenOrder()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Beta", "Embedded Body"));
        project.SliderPresets.Add(new SliderPreset("Alpha", "Community Body"));
        project.SliderPresets.Add(new SliderPreset("Gamma", "embedded body"));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Community Body", ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Embedded Body", ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Unrelated Body", ProfileSourceKind.EmbeddedProject));
        var composer = new RequestScopedProfileCatalogComposer(TestProfiles.CreateBundledOnlyCatalog());

        var catalog = composer.BuildForProject(project);

        catalog.Entries.Select(entry => entry.Name).Should().Equal(
            ProjectProfileMapping.SkyrimCbbe,
            "Embedded Body",
            "Community Body");
        catalog.Entries.Should().OnlyContain(entry => entry.SourceKind == ProfileSourceKind.Bundled || !entry.IsEditable);
    }

    [Fact]
    public void ResolveReferencedCustomProfilesSkipsBundledBlankAndUnreferencedProfileNames()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Default", ProjectProfileMapping.SkyrimCbbe));
        project.SliderPresets.Add(new SliderPreset("Blank", " "));
        project.SliderPresets.Add(new SliderPreset("Fallout", ProjectProfileMapping.Fallout4Cbbe));
        project.SliderPresets.Add(new SliderPreset("Community", "Community Body"));
        project.CustomProfiles.Add(TestProfiles.CreateProfile(ProjectProfileMapping.SkyrimCbbe, ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Community Body", ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Unrelated Body", ProfileSourceKind.EmbeddedProject));
        var composer = new RequestScopedProfileCatalogComposer(TestProfiles.CreateBundledOnlyCatalog());

        var profiles = composer.ResolveReferencedCustomProfiles(project);

        profiles.Select(profile => profile.Name).Should().ContainSingle().Which.Should().Be("Community Body");
    }

    [Fact]
    public void BuildForProjectUsesFirstEligibleProjectDuplicateDefinitionCaseInsensitively()
    {
        var project = TestProfiles.CreateProjectUsingProfile("Community Body");
        var firstProjectProfile = TestProfiles.CreateProfile(
            "Community Body",
            ProfileSourceKind.EmbeddedProject,
            null,
            TestProfiles.CreateEmbeddedSliderProfile());
        var duplicateProjectProfile = TestProfiles.CreateProfile(
            "community body",
            ProfileSourceKind.EmbeddedProject,
            null,
            TestProfiles.CreateCommunitySliderProfile());
        project.CustomProfiles.Add(firstProjectProfile);
        project.CustomProfiles.Add(duplicateProjectProfile);
        var composer = new RequestScopedProfileCatalogComposer(TestProfiles.CreateBundledOnlyCatalog());

        var profiles = composer.ResolveReferencedCustomProfiles(project);
        var catalog = composer.BuildForProject(project);

        profiles.Should().ContainSingle().Which.Should().BeSameAs(firstProjectProfile);
        catalog.Entries.Select(entry => entry.Name).Should().Equal(
            ProjectProfileMapping.SkyrimCbbe,
            "Community Body");
        catalog.GetProfile("Community Body").SliderProfile.GetDefaultBig("Breasts").Should().Be(90);
    }

    [Fact]
    public void ResolveReferencedCustomProfilesPrefersProjectDefinitionOverSaveContextDuplicate()
    {
        var project = TestProfiles.CreateProjectUsingProfile("Community Body");
        var projectProfile = TestProfiles.CreateProfile(
            "Community Body",
            ProfileSourceKind.EmbeddedProject,
            null,
            TestProfiles.CreateEmbeddedSliderProfile());
        var contextProfile = TestProfiles.CreateProfile(
            "Community Body",
            ProfileSourceKind.LocalCustom,
            @"C:\Users\Example\Profiles\Community Body.json",
            TestProfiles.CreateCommunitySliderProfile());
        project.CustomProfiles.Add(projectProfile);
        var saveContext = new ProjectSaveContext(new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase)
        {
            [contextProfile.Name] = contextProfile,
        });
        var composer = new RequestScopedProfileCatalogComposer(TestProfiles.CreateBundledOnlyCatalog());

        var profiles = composer.ResolveReferencedCustomProfiles(project, saveContext);

        profiles.Should().ContainSingle().Which.Should().BeSameAs(projectProfile);
    }

    [Fact]
    public void ResolveReferencedCustomProfilesFallsBackToSaveContextWhenProjectDefinitionIsAbsent()
    {
        var project = TestProfiles.CreateProjectUsingProfile("Community Body");
        var contextProfile = TestProfiles.CreateProfile(
            "Community Body",
            ProfileSourceKind.LocalCustom,
            @"C:\Users\Example\Profiles\Community Body.json",
            TestProfiles.CreateCommunitySliderProfile());
        var saveContext = new ProjectSaveContext(new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase)
        {
            [contextProfile.Name] = contextProfile,
            ["Unrelated Body"] = TestProfiles.CreateProfile("Unrelated Body", ProfileSourceKind.LocalCustom),
        });
        var composer = new RequestScopedProfileCatalogComposer(TestProfiles.CreateBundledOnlyCatalog());

        var profiles = composer.ResolveReferencedCustomProfiles(project, saveContext);

        profiles.Should().ContainSingle().Which.Should().BeSameAs(contextProfile);
    }
}
