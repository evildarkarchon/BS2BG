using BS2BG.Core.Automation;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.Serialization;
using Xunit;

namespace BS2BG.Tests;

public sealed class OutputArtifactPlannerTests
{
    [Fact]
    public void PlanningObservesCancellationBeforeGeneratingArtifacts()
    {
        var planner = new OutputArtifactPlanner(
            new TemplateGenerationService(),
            new MorphGenerationService());
        var input = new OutputArtifactPlanningInput(
            new BS2BG.Core.Models.ProjectModel(),
            TestProfiles.CreateBundledOnlyCatalog(),
            OutputIntent.BodyGen,
            omitRedundantSliders: false);

        var act = () => planner.Plan(input, new CancellationToken(canceled: true));

        act.Should().Throw<OperationCanceledException>();
    }

    [Fact]
    public void AllOutputPlanMatchesJavaGoldenArtifactsByteForByte()
    {
        var project = new ProjectFileService().Load(FixturePath("expected", "minimal", "project.jbs2bg"));
        var catalog = new TemplateProfileCatalogFactory(RepositoryRoot).Create();
        var planner = new OutputArtifactPlanner(
            new TemplateGenerationService(),
            new MorphGenerationService());

        var plan = planner.Plan(new OutputArtifactPlanningInput(
            project,
            catalog,
            OutputIntent.All,
            omitRedundantSliders: false), TestContext.Current.CancellationToken);

        plan.Groups.Select(group => group.Kind).Should().Equal(
            OutputArtifactGroupKind.BodyGen,
            OutputArtifactGroupKind.BosJson);

        var bodyGen = plan.GetRequiredGroup(OutputArtifactGroupKind.BodyGen);
        bodyGen.Artifacts.Select(artifact => (artifact.Role, artifact.RelativePath)).Should().Equal(
            (OutputArtifactRole.BodyGenTemplates, "templates.ini"),
            (OutputArtifactRole.BodyGenMorphs, "morphs.ini"));
        AssertGoldenBytes(bodyGen.Artifacts[0], FixturePath("expected", "minimal", "templates.ini"));
        AssertGoldenBytes(bodyGen.Artifacts[1], FixturePath("expected", "minimal", "morphs.ini"));

        var bosJson = plan.GetRequiredGroup(OutputArtifactGroupKind.BosJson);
        var expectedBosPaths = Directory
            .EnumerateFiles(FixturePath("expected", "minimal", "bos-json"), "*.json")
            .OrderBy(Path.GetFileName, StringComparer.OrdinalIgnoreCase)
            .ToArray();
        bosJson.Artifacts.Select(artifact => artifact.Role).Should().OnlyContain(
            role => role == OutputArtifactRole.BosPresetJson);
        bosJson.Artifacts.Select(artifact => artifact.RelativePath).Should().Equal(
            expectedBosPaths.Select(Path.GetFileName));

        foreach (var expectedPath in expectedBosPaths)
        {
            var artifact = bosJson.Artifacts.Single(candidate =>
                string.Equals(candidate.RelativePath, Path.GetFileName(expectedPath), StringComparison.Ordinal));
            AssertGoldenBytes(artifact, expectedPath);
        }
    }

    [Fact]
    public void BosPlanOwnsSortingSanitizationDeduplicationAndOriginalBodyNames()
    {
        var project = new BS2BG.Core.Models.ProjectModel();
        foreach (var name in new[] { "Preset?One", "CON", "Preset:One", "COM1" })
            project.SliderPresets.Add(new BS2BG.Core.Models.SliderPreset(name));
        var planner = new OutputArtifactPlanner(
            new TemplateGenerationService(),
            new MorphGenerationService());

        var plan = planner.Plan(new OutputArtifactPlanningInput(
            project,
            TestProfiles.CreateBundledOnlyCatalog(),
            OutputIntent.BosJson,
            omitRedundantSliders: false), TestContext.Current.CancellationToken);

        var artifacts = plan.GetRequiredGroup(OutputArtifactGroupKind.BosJson).Artifacts;
        artifacts.Select(artifact => artifact.RelativePath).Should().Equal(
            "COM1_.json",
            "CON_.json",
            "Preset_One.json",
            "Preset_One (2).json");
        artifacts.Select(artifact => System.Text.Encoding.UTF8.GetString(artifact.CopyContent())).Should().SatisfyRespectively(
            content => content.Should().Contain("\"bodyname\": \"COM1\""),
            content => content.Should().Contain("\"bodyname\": \"CON\""),
            content => content.Should().Contain("\"bodyname\": \"Preset:One\""),
            content => content.Should().Contain("\"bodyname\": \"Preset?One\""));
    }

    [Fact]
    public void BodyGenCommitGroupRejectsMissingOrDuplicateSemanticRoles()
    {
        var templates = new OutputArtifact(OutputArtifactRole.BodyGenTemplates, "templates.ini", Array.Empty<byte>());
        var secondTemplates = new OutputArtifact(OutputArtifactRole.BodyGenTemplates, "other.ini", Array.Empty<byte>());

        var missingMorphs = () => new OutputArtifactCommitGroup(OutputArtifactGroupKind.BodyGen, new[] { templates });
        var duplicateTemplates = () => new OutputArtifactCommitGroup(
            OutputArtifactGroupKind.BodyGen,
            new[] { templates, secondTemplates });

        missingMorphs.Should().Throw<ArgumentException>();
        duplicateTemplates.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void OutputPlanRejectsGroupsThatDoNotMatchIntentOrCommitOrder()
    {
        var bodyGen = new OutputArtifactCommitGroup(OutputArtifactGroupKind.BodyGen, new[]
        {
            new OutputArtifact(OutputArtifactRole.BodyGenTemplates, "templates.ini", Array.Empty<byte>()),
            new OutputArtifact(OutputArtifactRole.BodyGenMorphs, "morphs.ini", Array.Empty<byte>()),
        });
        var bos = new OutputArtifactCommitGroup(OutputArtifactGroupKind.BosJson, new[]
        {
            new OutputArtifact(OutputArtifactRole.BosPresetJson, "Alpha.json", Array.Empty<byte>()),
        });

        var wrongIntent = () => new OutputArtifactPlan(OutputIntent.BodyGen, new[] { bos });
        var wrongOrder = () => new OutputArtifactPlan(OutputIntent.All, new[] { bos, bodyGen });

        wrongIntent.Should().Throw<ArgumentException>();
        wrongOrder.Should().Throw<ArgumentException>();
    }

    private static void AssertGoldenBytes(OutputArtifact artifact, string expectedPath) =>
        artifact.CopyContent().Should().Equal(File.ReadAllBytes(expectedPath));

    private static string FixturePath(params string[] segments) =>
        Path.Combine(new[] { RepositoryRoot, "tests", "fixtures" }.Concat(segments).ToArray());

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
}
