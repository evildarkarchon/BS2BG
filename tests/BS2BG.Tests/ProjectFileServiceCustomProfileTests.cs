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

    [Fact]
    public void Save_NoCustomProfiles_ProducesByteIdenticalOutputToV1Format()
    {
        var service = new ProjectFileService();
        var project = new ProjectModel();
        project.SliderPresets.Add(new BS2BG.Core.Models.SliderPreset("Alpha", "Community Body"));

        var saved = service.SaveToString(project);

        saved.Should().Be(
            """
            {
              "SliderPresets": {
                "Alpha": {
                  "isUUNP": false,
                  "Profile": "Community Body",
                  "SetSliders": []
                }
              },
              "CustomMorphTargets": {},
              "MorphedNPCs": {}
            }
            """);
    }

    [Fact]
    public void SaveEmbedsOnlyReferencedCustomProfilesFromProjectAndSaveContextInNameOrder()
    {
        var service = new ProjectFileService();
        var project = new ProjectModel();
        project.SliderPresets.Add(new BS2BG.Core.Models.SliderPreset("Alpha", "Zeta Body"));
        project.SliderPresets.Add(new BS2BG.Core.Models.SliderPreset("Beta", "Context Body"));
        project.CustomProfiles.Add(CreateProfile("Zeta Body"));
        project.CustomProfiles.Add(CreateProfile("Unrelated Body"));
        var saveContext = new ProjectSaveContext(new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase)
        {
            ["Context Body"] = CreateProfile("Context Body"),
            ["Other Body"] = CreateProfile("Other Body"),
        });

        var saved = service.SaveToString(project, saveContext);

        saved.Should().Contain("\"CustomProfiles\": [");
        saved.Should().Contain("\"Name\": \"Context Body\"");
        saved.Should().Contain("\"Name\": \"Zeta Body\"");
        saved.Should().NotContain("Unrelated Body");
        saved.Should().NotContain("Other Body");
        saved.IndexOf("\"MorphedNPCs\"", StringComparison.Ordinal)
            .Should().BeLessThan(saved.IndexOf("\"CustomProfiles\"", StringComparison.Ordinal));
        saved.IndexOf("\"Name\": \"Context Body\"", StringComparison.Ordinal)
            .Should().BeLessThan(saved.IndexOf("\"Name\": \"Zeta Body\"", StringComparison.Ordinal));
    }

    [Fact]
    public void LoadWithDiagnosticsHydratesCustomProfilesAndPreservesPresetProfileNames()
    {
        var service = new ProjectFileService();

        var result = service.LoadWithDiagnosticsFromString(
            """
            {
              "SliderPresets": {
                "Alpha": {
                  "isUUNP": false,
                  "Profile": "Community Body",
                  "SetSliders": []
                }
              },
              "CustomMorphTargets": {},
              "MorphedNPCs": {},
              "CustomProfiles": [
                {
                  "Version": 1,
                  "Name": "Community Body",
                  "Game": "Skyrim",
                  "Defaults": { "Scale": { "valueSmall": 0, "valueBig": 1 } },
                  "Multipliers": { "Scale": 2 },
                  "Inverted": ["Scale"]
                }
              ]
            }
            """);

        result.Diagnostics.Should().BeEmpty();
        result.Project.CustomProfiles.Should().ContainSingle(profile => profile.Name == "Community Body");
        result.Project.SliderPresets.Should().ContainSingle().Which.ProfileName.Should().Be("Community Body");
    }

    [Fact]
    public void LoadWithDiagnosticsReportsMalformedEmbeddedProfileAndKeepsLegacyProjectData()
    {
        var service = new ProjectFileService();

        var result = service.LoadWithDiagnosticsFromString(
            """
            {
              "SliderPresets": {
                "Alpha": {
                  "isUUNP": true,
                  "Profile": "Broken Body",
                  "SetSliders": []
                }
              },
              "CustomMorphTargets": {},
              "MorphedNPCs": {},
              "CustomProfiles": [
                {
                  "Version": 1,
                  "Name": "Broken Body",
                  "Defaults": { "Scale": { "valueSmall": "bad", "valueBig": 1 } },
                  "Multipliers": {},
                  "Inverted": []
                }
              ]
            }
            """);

        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "EmbeddedProfileInvalid");
        result.Project.CustomProfiles.Should().BeEmpty();
        result.Project.SliderPresets.Should().ContainSingle().Which.ProfileName.Should().Be("Broken Body");
    }

    [Fact]
    public void LoadWithDiagnosticsReportsBundledNameCollisionAndDuplicateEmbeddedName()
    {
        var service = new ProjectFileService();

        var result = service.LoadWithDiagnosticsFromString(
            """
            {
              "SliderPresets": {},
              "CustomMorphTargets": {},
              "MorphedNPCs": {},
              "CustomProfiles": [
                {
                  "Version": 1,
                  "Name": "CBBE",
                  "Game": "Skyrim",
                  "Defaults": {},
                  "Multipliers": {},
                  "Inverted": []
                },
                {
                  "Version": 1,
                  "Name": "Community Body",
                  "Game": "Skyrim",
                  "Defaults": {},
                  "Multipliers": {},
                  "Inverted": []
                },
                {
                  "Version": 1,
                  "Name": "community body",
                  "Game": "Skyrim",
                  "Defaults": {},
                  "Multipliers": {},
                  "Inverted": []
                }
              ]
            }
            """);

        result.Diagnostics.Select(diagnostic => diagnostic.Code).Should().Contain(new[]
        {
            "EmbeddedProfileBundledNameCollision",
            "EmbeddedProfileDuplicateName",
        });
        result.Project.CustomProfiles.Select(profile => profile.Name).Should().Equal("Community Body");
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
