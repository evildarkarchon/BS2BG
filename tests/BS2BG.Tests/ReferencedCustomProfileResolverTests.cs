using BS2BG.Core.Models;
using BS2BG.Core.Profiles;
using Xunit;

namespace BS2BG.Tests;

public sealed class ReferencedCustomProfileResolverTests
{
    /// <summary>
    /// Verifies case-insensitive duplicate project copies resolve deterministically without exposing project-owned instances.
    /// </summary>
    [Fact]
    public void ResolveUsesFirstProjectCopyAndReturnsDetachedSnapshot()
    {
        var project = TestProfiles.CreateProjectUsingProfile("COMMUNITY BODY");
        var first = TestProfiles.CreateProfile(
            "Community Body",
            ProfileSourceKind.EmbeddedProject,
            null,
            TestProfiles.CreateEmbeddedSliderProfile());
        var duplicate = TestProfiles.CreateProfile(
            "community body",
            ProfileSourceKind.EmbeddedProject,
            null,
            TestProfiles.CreateCommunitySliderProfile());
        project.CustomProfiles.Add(first);
        project.CustomProfiles.Add(duplicate);

        var resolution = ReferencedCustomProfileResolver.Resolve(project);

        resolution.ResolvedProfiles.Should().ContainSingle();
        ReferencedCustomProfileSnapshot resolved = resolution.ResolvedProfiles[0];
        resolved.Should().NotBeSameAs(first);
        resolved.Name.Should().Be("Community Body");
        resolved.SliderProfile.GetDefaultBig("Breasts").Should().Be(90);
        resolution.UnresolvedProfileNames.Should().BeEmpty();

        first.Name = "Changed Body";
        first.Game = "Fallout4";
        first.SliderProfile = TestProfiles.CreateCommunitySliderProfile();
        first.SourceKind = ProfileSourceKind.LocalCustom;
        first.FilePath = @"C:\Changed.json";
        resolved.Name.Should().Be("Community Body");
        resolved.Game.Should().Be("Skyrim");
        resolved.SliderProfile.GetDefaultBig("Breasts").Should().Be(90);
        resolved.SourceKind.Should().Be(ProfileSourceKind.EmbeddedProject);
        resolved.FilePath.Should().BeNull();
    }

    /// <summary>
    /// Verifies fallback candidates resolve by their internal names and unresolved references retain first-reference order.
    /// </summary>
    [Fact]
    public void ResolveUsesFallbackInternalNamesAndReportsUnresolvedReferencesInOrder()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Fallback", "Fallback Body"));
        project.SliderPresets.Add(new SliderPreset("Spoofed", "Spoofed Body"));
        project.SliderPresets.Add(new SliderPreset("Missing", "Missing Body"));
        project.SliderPresets.Add(new SliderPreset("Duplicate Reference", "fallback body"));
        var fallback = TestProfiles.CreateProfile("Fallback Body", ProfileSourceKind.LocalCustom);
        var mismatched = TestProfiles.CreateProfile("Different Body", ProfileSourceKind.LocalCustom);
        var availableProfiles = new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase)
        {
            ["Unrelated Index"] = fallback,
            ["Spoofed Body"] = mismatched,
        };

        var resolution = ReferencedCustomProfileResolver.Resolve(project, availableProfiles);

        resolution.ResolvedProfiles.Select(profile => profile.Name).Should().Equal("Fallback Body");
        resolution.ResolvedProfiles[0].Should().NotBeSameAs(fallback);
        resolution.UnresolvedProfileNames.Should().Equal("Spoofed Body", "Missing Body");
    }

    /// <summary>
    /// Verifies project copies take precedence over same-name fallbacks while resolved profiles retain first-reference order.
    /// </summary>
    [Fact]
    public void ResolvePrefersProjectCopiesAndPreservesFirstReferenceOrder()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("First", "Fallback Body"));
        project.SliderPresets.Add(new SliderPreset("Second", "Community Body"));
        var projectCopy = TestProfiles.CreateProfile(
            "Community Body",
            ProfileSourceKind.EmbeddedProject,
            null,
            TestProfiles.CreateEmbeddedSliderProfile());
        var fallbackDuplicate = TestProfiles.CreateProfile(
            "Community Body",
            ProfileSourceKind.LocalCustom,
            null,
            TestProfiles.CreateCommunitySliderProfile());
        var fallbackFirst = TestProfiles.CreateProfile("Fallback Body", ProfileSourceKind.LocalCustom);
        project.CustomProfiles.Add(projectCopy);
        var availableProfiles = new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase)
        {
            [fallbackDuplicate.Name] = fallbackDuplicate,
            [fallbackFirst.Name] = fallbackFirst,
        };

        var resolution = ReferencedCustomProfileResolver.Resolve(project, availableProfiles);

        resolution.ResolvedProfiles.Select(profile => profile.Name)
            .Should().Equal("Fallback Body", "Community Body");
        resolution.ResolvedProfiles[1].SliderProfile.GetDefaultBig("Breasts").Should().Be(90);
        resolution.UnresolvedProfileNames.Should().BeEmpty();
    }

    /// <summary>
    /// Verifies bundled references and ineligible or unreferenced definitions stay outside custom-profile resolution.
    /// </summary>
    [Fact]
    public void ResolveExcludesBundledAndUnreferencedProfiles()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Bundled", ProjectProfileMapping.SkyrimCbbe));
        project.SliderPresets.Add(new SliderPreset("Community", "Community Body"));
        project.CustomProfiles.Add(TestProfiles.CreateProfile(
            ProjectProfileMapping.SkyrimCbbe,
            ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile(
            "Community Body",
            ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile(
            "Unrelated Body",
            ProfileSourceKind.EmbeddedProject));
        var bundledSource = TestProfiles.CreateProfile("Community Body", ProfileSourceKind.Bundled);
        var availableProfiles = new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase)
        {
            [bundledSource.Name] = bundledSource,
        };

        var resolution = ReferencedCustomProfileResolver.Resolve(project, availableProfiles);

        resolution.ResolvedProfiles.Select(profile => profile.Name).Should().Equal("Community Body");
        resolution.UnresolvedProfileNames.Should().BeEmpty();
    }
}
