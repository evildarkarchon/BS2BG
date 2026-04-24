using System.Diagnostics.CodeAnalysis;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments", Justification = "Small expected sequences keep project fixture assertions readable.")]
public sealed class ProjectFileServiceTests
{
    [Fact]
    public void LoadV1ProjectDataIntoCorePreservesSupportedFieldsAndDropsStaleReferences()
    {
        var service = new ProjectFileService();

        var project = service.Load(ProjectFixturePath("v1-stale-project.jbs2bg"));

        Assert.False(project.IsDirty);
        Assert.Equal(new[] { "Alpha", "Beta Preset" }, project.SliderPresets.Select(preset => preset.Name));

        var alpha = project.FindSliderPreset("alpha");
        Assert.NotNull(alpha);
        Assert.Equal(ProjectProfileMapping.Fallout4Cbbe, alpha.ProfileName);
        Assert.False(alpha.IsUunp);
        Assert.Empty(alpha.SetSliders);

        var beta = project.FindSliderPreset("Beta Preset");
        Assert.NotNull(beta);
        Assert.Equal(ProjectProfileMapping.SkyrimUunp, beta.ProfileName);
        Assert.True(beta.IsUunp);

        var arms = Assert.Single(beta.SetSliders);
        Assert.Equal("Arms", arms.Name);
        Assert.True(arms.Enabled);
        Assert.Null(arms.ValueSmall);
        Assert.Equal(50, arms.ValueBig);
        Assert.Equal(25, arms.PercentMin);
        Assert.Equal(75, arms.PercentMax);

        Assert.Equal(
            new[] { "MissingKept", "ZedMissingOmitted" },
            beta.MissingDefaultSetSliders.Select(slider => slider.Name));
        var keptMissingDefault = beta.MissingDefaultSetSliders.Single(slider => slider.Name == "MissingKept");
        Assert.False(keptMissingDefault.Enabled);

        Assert.Equal(new[] { "TargetA", "TargetZ" }, project.CustomMorphTargets.Select(target => target.Name));
        Assert.Empty(project.CustomMorphTargets[0].SliderPresets);
        Assert.Equal(
            new[] { "Alpha", "Beta Preset" },
            project.CustomMorphTargets[1].SliderPresets.Select(preset => preset.Name));

        Assert.Equal(new[] { "Long Form", "No Assignments" }, project.MorphedNpcs.Select(npc => npc.Name));
        var npc = project.MorphedNpcs[0];
        Assert.Equal("Skyrim.esm", npc.Mod);
        Assert.Equal("HousecarlWhiterun", npc.EditorId);
        Assert.Equal("NordRace", npc.Race);
        Assert.Equal("A2C94", npc.FormId);
        Assert.Equal(new[] { "Beta Preset" }, npc.SliderPresets.Select(preset => preset.Name));

        Assert.Equal("Dawnguard.esm", project.MorphedNpcs[1].Mod);
        Assert.Equal("0", project.MorphedNpcs[1].FormId);
        Assert.Empty(project.MorphedNpcs[1].SliderPresets);
    }

    [Fact]
    public void SaveLoadedV1FixtureMatchesExpectedCompatibleOutput()
    {
        var service = new ProjectFileService();
        var project = service.Load(ProjectFixturePath("v1-stale-project.jbs2bg"));

        var actual = service.SaveToString(project);
        var expected = File.ReadAllText(ProjectFixturePath("v1-stale-project.expected.jbs2bg"));

        Assert.Equal(NormalizeJson(expected), NormalizeJson(actual));
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

        Assert.False(project.IsDirty);

        preset.ProfileName = ProjectProfileMapping.SkyrimUunp;

        Assert.True(project.IsDirty);
        project.MarkClean();

        var removed = project.RemoveSliderPreset("preset one");

        Assert.True(removed);
        Assert.Empty(target.SliderPresets);
        Assert.True(project.IsDirty);
    }

    private static string ProjectFixturePath(string fileName)
    {
        return Path.Combine(RepositoryRoot, "tests", "fixtures", "project-roundtrip", fileName);
    }

    private static string NormalizeNewlines(string value)
    {
        return value.Replace("\r\n", "\n", StringComparison.Ordinal)
            .Replace("\r", "\n", StringComparison.Ordinal);
    }

    private static string NormalizeJson(string value)
    {
        return NormalizeNewlines(value).TrimEnd('\n');
    }

    private static string RepositoryRoot
    {
        get
        {
            var directory = new DirectoryInfo(AppContext.BaseDirectory);
            while (directory is not null && !File.Exists(Path.Combine(directory.FullName, "PRD.md")))
            {
                directory = directory.Parent;
            }

            return directory?.FullName
                ?? throw new InvalidOperationException("Could not locate repository root.");
        }
    }
}
