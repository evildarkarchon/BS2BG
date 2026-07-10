using System.Text;
using BS2BG.Core.Automation;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using Xunit;

namespace BS2BG.Tests;

public sealed class OutputArtifactConsumerTests
{
    [Fact]
    public void PreflightObservesCancellationBeforeInspectingArtifacts()
    {
        using var directory = new TemporaryDirectory();
        var group = new OutputArtifactCommitGroup(OutputArtifactGroupKind.BodyGen, new[]
        {
            Artifact(OutputArtifactRole.BodyGenTemplates, "templates.ini", "templates"),
            Artifact(OutputArtifactRole.BodyGenMorphs, "morphs.ini", "morphs"),
        });

        var act = () => new OutputArtifactPreflight().Preview(
            directory.Path,
            group,
            new CancellationToken(canceled: true));

        act.Should().Throw<OperationCanceledException>();
        Directory.EnumerateFileSystemEntries(directory.Path).Should().BeEmpty();
    }

    [Fact]
    public void PreflightProjectsPathsOverwriteStateAndSnippetsWithoutWriting()
    {
        using var directory = new TemporaryDirectory();
        var templatesPath = Path.Combine(directory.Path, "templates.ini");
        File.WriteAllText(templatesPath, "existing");
        var group = new OutputArtifactCommitGroup(OutputArtifactGroupKind.BodyGen, new[]
        {
            Artifact(OutputArtifactRole.BodyGenTemplates, "templates.ini", "Alpha=Scale@1.0\r\nBeta=Scale@0.5"),
            Artifact(OutputArtifactRole.BodyGenMorphs, "morphs.ini", "All|Female=Alpha"),
        });
        var plan = new OutputArtifactPlan(OutputIntent.BodyGen, new[] { group });

        var preview = new OutputArtifactPreflight().Preview(
            directory.Path,
            plan.GetRequiredGroup(OutputArtifactGroupKind.BodyGen),
            TestContext.Current.CancellationToken);

        preview.HasBatchRisk.Should().BeTrue();
        preview.Files.Select(file => (file.Path, file.WillOverwrite)).Should().Equal(
            (templatesPath, true),
            (Path.Combine(directory.Path, "morphs.ini"), false));
        preview.Files[0].SnippetLines.Should().Equal("Alpha=Scale@1.0", "Beta=Scale@0.5");
        File.ReadAllText(templatesPath).Should().Be("existing");
        File.Exists(Path.Combine(directory.Path, "morphs.ini")).Should().BeFalse();
    }

    [Fact]
    public void CommitterWritesTheFrozenBytesAndReturnsResolvedPaths()
    {
        using var directory = new TemporaryDirectory();
        var sourceBytes = new UTF8Encoding(false).GetBytes("Alpha=Scale@1.0\r\n");
        var templates = new OutputArtifact(OutputArtifactRole.BodyGenTemplates, "templates.ini", sourceBytes);
        var exposedCopy = templates.CopyContent();
        sourceBytes[0] = (byte)'X';
        exposedCopy[1] = (byte)'Y';
        var morphs = Artifact(OutputArtifactRole.BodyGenMorphs, "morphs.ini", "All|Female=Alpha");
        var group = new OutputArtifactCommitGroup(OutputArtifactGroupKind.BodyGen, new[] { templates, morphs });

        var result = new OutputArtifactCommitter().Commit(directory.Path, group);

        result.WrittenFiles.Should().Equal(
            Path.Combine(directory.Path, "templates.ini"),
            Path.Combine(directory.Path, "morphs.ini"));
        File.ReadAllBytes(result.WrittenFiles[0]).Should().Equal(new UTF8Encoding(false).GetBytes("Alpha=Scale@1.0\r\n"));
        File.ReadAllBytes(result.WrittenFiles[1]).Should().Equal(new UTF8Encoding(false).GetBytes("All|Female=Alpha"));
    }

    private static OutputArtifact Artifact(OutputArtifactRole role, string relativePath, string content) =>
        new(role, relativePath, new UTF8Encoding(false).GetBytes(content));

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "bs2bg-artifact-tests-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose() => Directory.Delete(Path, true);
    }
}
