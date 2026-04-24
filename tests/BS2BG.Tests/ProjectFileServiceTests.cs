using System.Diagnostics.CodeAnalysis;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected sequences keep project fixture assertions readable.")]
public sealed class ProjectFileServiceTests
{
    private static string RepositoryRoot
    {
        get
        {
            var directory = new DirectoryInfo(AppContext.BaseDirectory);
            while (directory is not null && !File.Exists(Path.Combine(directory.FullName, "PRD.md")))
                directory = directory.Parent;

            return directory?.FullName
                   ?? throw new InvalidOperationException("Could not locate repository root.");
        }
    }

    [Fact]
    public void LoadV1ProjectDataIntoCorePreservesSupportedFieldsAndDropsStaleReferences()
    {
        var service = new ProjectFileService();

        var project = service.Load(ProjectFixturePath("v1-stale-project.jbs2bg"));

        project.IsDirty.Should().BeFalse();
        project.SliderPresets.Select(preset => preset.Name).Should().Equal(new[] { "Alpha", "Beta Preset" });

        var alpha = project.FindSliderPreset("alpha");
        alpha.Should().NotBeNull();
        alpha.ProfileName.Should().Be(ProjectProfileMapping.Fallout4Cbbe);
        alpha.IsUunp.Should().BeFalse();
        alpha.SetSliders.Should().BeEmpty();

        var beta = project.FindSliderPreset("Beta Preset");
        beta.Should().NotBeNull();
        beta.ProfileName.Should().Be(ProjectProfileMapping.SkyrimUunp);
        beta.IsUunp.Should().BeTrue();

        var arms = beta.SetSliders.Should().ContainSingle().Which;
        arms.Name.Should().Be("Arms");
        arms.Enabled.Should().BeTrue();
        arms.ValueSmall.Should().BeNull();
        arms.ValueBig.Should().Be(50);
        arms.PercentMin.Should().Be(25);
        arms.PercentMax.Should().Be(75);

        beta.MissingDefaultSetSliders.Select(slider => slider.Name).Should().Equal(new[] { "MissingKept", "ZedMissingOmitted" });
        var keptMissingDefault = beta.MissingDefaultSetSliders.Single(slider => slider.Name == "MissingKept");
        keptMissingDefault.Enabled.Should().BeFalse();

        project.CustomMorphTargets.Select(target => target.Name).Should().Equal(new[] { "TargetA", "TargetZ" });
        project.CustomMorphTargets[0].SliderPresets.Should().BeEmpty();
        project.CustomMorphTargets[1].SliderPresets.Select(preset => preset.Name).Should().Equal(new[] { "Alpha", "Beta Preset" });

        project.MorphedNpcs.Select(npc => npc.Name).Should().Equal(new[] { "Long Form", "No Assignments" });
        var npc = project.MorphedNpcs[0];
        npc.Mod.Should().Be("Skyrim.esm");
        npc.EditorId.Should().Be("HousecarlWhiterun");
        npc.Race.Should().Be("NordRace");
        npc.FormId.Should().Be("A2C94");
        npc.SliderPresets.Select(preset => preset.Name).Should().Equal(new[] { "Beta Preset" });

        project.MorphedNpcs[1].Mod.Should().Be("Dawnguard.esm");
        project.MorphedNpcs[1].FormId.Should().Be("0");
        project.MorphedNpcs[1].SliderPresets.Should().BeEmpty();
    }

    [Fact]
    public void SaveLoadedV1FixtureMatchesExpectedCompatibleOutput()
    {
        var service = new ProjectFileService();
        var project = service.Load(ProjectFixturePath("v1-stale-project.jbs2bg"));

        var actual = service.SaveToString(project);
        var expected = File.ReadAllText(ProjectFixturePath("v1-stale-project.expected.jbs2bg"));

        NormalizeJson(actual).Should().Be(NormalizeJson(expected));
    }

    [Fact]
    public void SavePreservesNpcsWithDuplicateDisplayNames()
    {
        var service = new ProjectFileService();
        var project = new ProjectModel();
        project.MorphedNpcs.Add(
            new Npc("Guard") { Mod = "Skyrim.esm", EditorId = "WhiterunGuard", FormId = "00012345" });
        project.MorphedNpcs.Add(new Npc("Guard")
        {
            Mod = "Dawnguard.esm", EditorId = "DawnguardGuard", FormId = "02012345"
        });

        var saved = service.SaveToString(project);
        var reloaded = service.LoadFromString(saved);

        reloaded.MorphedNpcs.Count.Should().Be(2);
        reloaded.MorphedNpcs.Select(npc => npc.EditorId).Should().Equal(new[] { "WhiterunGuard", "DawnguardGuard" });
    }

    [Fact]
    public void SaveReplacesExistingProjectAndRemovesTemporaryFile()
    {
        var directory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(directory);
        try
        {
            var path = Path.Combine(directory, "project.jbs2bg");
            File.WriteAllText(path, "old");
            var project = new ProjectModel();
            project.SliderPresets.Add(new SliderPreset("Alpha"));
            project.MarkDirty();
            var service = new ProjectFileService();

            service.Save(project, path);

            File.ReadAllText(path).Should().Contain("Alpha");
            Directory.EnumerateFiles(directory, "*.tmp").Should().BeEmpty();
            project.IsDirty.Should().BeFalse();
        }
        finally
        {
            Directory.Delete(directory, true);
        }
    }

    [Fact]
    public void LoadPreservesDuplicateMorphedNpcObjectMembers()
    {
        var service = new ProjectFileService();

        var project = service.LoadFromString(
            """
            {
              "SliderPresets": {},
              "CustomMorphTargets": {},
              "MorphedNPCs": {
                "Guard": {
                  "Mod": "Skyrim.esm",
                  "EditorId": "WhiterunGuard",
                  "Race": "NordRace",
                  "FormId": "00012345",
                  "SliderPresets": []
                },
                "Guard": {
                  "Mod": "Dawnguard.esm",
                  "EditorId": "DawnguardGuard",
                  "Race": "NordRace",
                  "FormId": "02012345",
                  "SliderPresets": []
                }
              }
            }
            """);

        project.MorphedNpcs.Count.Should().Be(2);
        project.MorphedNpcs.Select(npc => npc.EditorId).Should().Equal(new[] { "WhiterunGuard", "DawnguardGuard" });
    }

    [Fact]
    public void ProjectReferenceHelpersRemoveAssignmentsAndTriggerDirtyState()
    {
        var project = new ProjectModel();
        var preset = new SliderPreset("Preset.One");
        var target = new CustomMorphTarget("All|Female");

        project.SliderPresets.Add(preset);
        project.CustomMorphTargets.Add(target);
        target.AddSliderPreset(preset);
        project.MarkClean();

        project.IsDirty.Should().BeFalse();

        preset.ProfileName = ProjectProfileMapping.SkyrimUunp;

        project.IsDirty.Should().BeTrue();
        project.MarkClean();

        var removed = project.RemoveSliderPreset("preset one");

        removed.Should().BeTrue();
        target.SliderPresets.Should().BeEmpty();
        project.IsDirty.Should().BeTrue();
    }

    private static string ProjectFixturePath(string fileName) =>
        Path.Combine(RepositoryRoot, "tests", "fixtures", "project-roundtrip", fileName);

    private static string NormalizeNewlines(string value)
    {
        return value.Replace("\r\n", "\n", StringComparison.Ordinal)
            .Replace("\r", "\n", StringComparison.Ordinal);
    }

    private static string NormalizeJson(string value) => NormalizeNewlines(value).TrimEnd('\n');
}
