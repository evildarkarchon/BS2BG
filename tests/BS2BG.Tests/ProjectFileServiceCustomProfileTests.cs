using BS2BG.Core.Formatting;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;
using Xunit;

namespace BS2BG.Tests;

public sealed class ProjectFileServiceCustomProfileTests
{
    [Fact]
    public void LoadProjectWithoutCustomProfilesLeavesEmbeddedProfilesEmptyAndPreservesLegacyFields()
    {
        var service = new ProjectFileService();

        var project = service.LoadFromString(
            """
            {
              "SliderPresets": {
                "Alpha": {
                  "isUUNP": true,
                  "Profile": "Community Body",
                  "SetSliders": []
                }
              },
              "CustomMorphTargets": {},
              "MorphedNPCs": {}
            }
            """);

        project.CustomProfiles.Should().BeEmpty();
        var preset = project.SliderPresets.Should().ContainSingle().Which;
        preset.IsUunp.Should().BeFalse();
        preset.ProfileName.Should().Be("Community Body");
    }

    [Fact]
    public void CustomProfilesMutationsParticipateInProjectDirtyTrackingAndReplaceWithClonesDefinitions()
    {
        var source = new ProjectModel();
        source.CustomProfiles.Add(CreateProfile("Community Body"));

        var project = new ProjectModel();
        project.ReplaceWith(source);

        project.IsDirty.Should().BeFalse();
        project.CustomProfiles.Should().ContainSingle(profile => profile.Name == "Community Body");
        project.CustomProfiles[0].Should().NotBeSameAs(source.CustomProfiles[0]);

        project.CustomProfiles[0].Name = "Renamed Body";

        project.IsDirty.Should().BeTrue();
    }

    private static CustomProfileDefinition CreateProfile(string name) =>
        new(
            name,
            "Skyrim",
            new SliderProfile(
                new[] { new SliderDefault("Scale", 0f, 1f) },
                new[] { new SliderMultiplier("Scale", 2f) },
                new[] { "Scale" }),
            ProfileSourceKind.EmbeddedProject,
            null);
}
