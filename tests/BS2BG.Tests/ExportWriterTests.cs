using System.Text;
using BS2BG.Core.Export;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;
using Xunit;

namespace BS2BG.Tests;

public sealed class ExportWriterTests
{
    [Fact]
    public void OutputArtifactCommitterRemovesTempFilesOnSuccess()
    {
        using var directory = new TemporaryDirectory();
        var committer = new OutputArtifactCommitter();

        committer.Commit(directory.Path, BodyGenGroup("templates", "morphs"));

        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(
            Path.Combine(directory.Path, "templates.ini"),
            Path.Combine(directory.Path, "morphs.ini"));
    }

    [Fact]
    public void OutputArtifactCommitterAtomicallyReplacesExistingBodyGenGroup()
    {
        using var directory = new TemporaryDirectory();
        var templatesPath = Path.Combine(directory.Path, "templates.ini");
        var morphsPath = Path.Combine(directory.Path, "morphs.ini");
        File.WriteAllText(templatesPath, "OLD_TEMPLATES");
        File.WriteAllText(morphsPath, "OLD_MORPHS");
        var committer = new OutputArtifactCommitter();

        committer.Commit(directory.Path, BodyGenGroup("Alpha=1.0", "All|Female=Alpha"));

        File.ReadAllText(templatesPath).Should().Be("Alpha=1.0");
        File.ReadAllText(morphsPath).Should().Be("All|Female=Alpha");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(templatesPath, morphsPath);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicPairLeavesTargetsUntouchedOnPhase1Failure()
    {
        using var directory = new TemporaryDirectory();
        var firstPath = Path.Combine(directory.Path, "first.txt");
        var secondPath = Path.Combine(directory.Path, "missing-subdir", "second.txt");
        File.WriteAllText(firstPath, "ORIGINAL_FIRST");

        var act = () => AtomicFileWriter.WriteAtomicPair(
            firstPath,
            "NEW_FIRST",
            secondPath,
            "NEW_SECOND",
            Encoding.UTF8);

        act.Should().Throw<DirectoryNotFoundException>();
        File.ReadAllText(firstPath).Should().Be("ORIGINAL_FIRST");
        File.Exists(secondPath).Should().BeFalse();
        Directory.GetFiles(directory.Path).Should().ContainSingle().Which.Should().Be(firstPath);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicReplacesExistingTarget()
    {
        using var directory = new TemporaryDirectory();
        var path = Path.Combine(directory.Path, "data.txt");
        File.WriteAllText(path, "ORIGINAL");

        AtomicFileWriter.WriteAtomic(path, "REPLACED", Encoding.UTF8);

        File.ReadAllText(path).Should().Be("REPLACED");
        Directory.GetFiles(directory.Path).Should().ContainSingle().Which.Should().Be(path);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicPairRestoresFirstTargetWhenSecondCommitFails()
    {
        using var directory = new TemporaryDirectory();
        var firstPath = Path.Combine(directory.Path, "first.txt");
        var secondPath = Path.Combine(directory.Path, "second.txt");
        File.WriteAllText(firstPath, "ORIGINAL_FIRST");
        File.WriteAllText(secondPath, "ORIGINAL_SECOND");

        using (new FileStream(secondPath, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => AtomicFileWriter.WriteAtomicPair(
                firstPath,
                "NEW_FIRST",
                secondPath,
                "NEW_SECOND",
                Encoding.UTF8);

            act.Should().Throw<IOException>();
        }

        File.ReadAllText(firstPath).Should().Be("ORIGINAL_FIRST");
        File.ReadAllText(secondPath).Should().Be("ORIGINAL_SECOND");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(firstPath, secondPath);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicBatchRollsBackPreviouslyCommittedTargets()
    {
        using var directory = new TemporaryDirectory();
        var path1 = Path.Combine(directory.Path, "a.txt");
        var path2 = Path.Combine(directory.Path, "b.txt");
        var path3 = Path.Combine(directory.Path, "c.txt");
        File.WriteAllText(path1, "OLD_1");
        File.WriteAllText(path2, "OLD_2");
        File.WriteAllText(path3, "OLD_3");

        using (new FileStream(path3, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => AtomicFileWriter.WriteAtomicBatch(
                new[] { (path1, "NEW_1"), (path2, "NEW_2"), (path3, "NEW_3") },
                Encoding.UTF8);

            act.Should().Throw<IOException>();
        }

        File.ReadAllText(path1).Should().Be("OLD_1");
        File.ReadAllText(path2).Should().Be("OLD_2");
        File.ReadAllText(path3).Should().Be("OLD_3");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(path1, path2, path3);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicBatchDeletesNewlyCreatedTargetsOnLaterFailure()
    {
        using var directory = new TemporaryDirectory();
        var path1 = Path.Combine(directory.Path, "new1.txt");
        var path2 = Path.Combine(directory.Path, "new2.txt");
        var path3 = Path.Combine(directory.Path, "existing.txt");
        File.WriteAllText(path3, "OLD_3");

        using (new FileStream(path3, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => AtomicFileWriter.WriteAtomicBatch(
                new[] { (path1, "NEW_1"), (path2, "NEW_2"), (path3, "NEW_3") },
                Encoding.UTF8);

            act.Should().Throw<IOException>();
        }

        File.Exists(path1).Should().BeFalse();
        File.Exists(path2).Should().BeFalse();
        File.ReadAllText(path3).Should().Be("OLD_3");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(path3);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicBatchRejectsDuplicatePaths()
    {
        using var directory = new TemporaryDirectory();
        var path = Path.Combine(directory.Path, "dup.txt");

        var act = () => AtomicFileWriter.WriteAtomicBatch(
            new[] { (path, "ONE"), (path.ToUpperInvariant(), "TWO") },
            Encoding.UTF8);

        act.Should().Throw<ArgumentException>();
        File.Exists(path).Should().BeFalse();
        Directory.GetFiles(directory.Path).Should().BeEmpty();
    }

    [Fact]
    public void OutputArtifactCommitterRollsBackLockedBosTargetMidBatch()
    {
        using var directory = new TemporaryDirectory();
        var committer = new OutputArtifactCommitter();
        var path1 = Path.Combine(directory.Path, "Preset1.json");
        var path2 = Path.Combine(directory.Path, "Preset2.json");
        var path3 = Path.Combine(directory.Path, "Preset3.json");
        File.WriteAllText(path1, "OLD_1");
        File.WriteAllText(path2, "OLD_2");
        File.WriteAllText(path3, "OLD_3");

        var group = BosGroup("Preset1.json", "Preset2.json", "Preset3.json");

        using (new FileStream(path2, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => committer.Commit(directory.Path, group);
            act.Should().Throw<IOException>();
        }

        File.ReadAllText(path1).Should().Be("OLD_1");
        File.ReadAllText(path2).Should().Be("OLD_2");
        File.ReadAllText(path3).Should().Be("OLD_3");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(path1, path2, path3);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicBatchDeletesTempFilesWhenWritePhaseFails()
    {
        using var directory = new TemporaryDirectory();
        var path1 = Path.Combine(directory.Path, "a.txt");
        var path2 = Path.Combine(directory.Path, "b.txt");

        var act = () => AtomicFileWriter.WriteAtomicBatch(
            new[] { (path1, "ONE"), (path2, "TWO") },
            new FaultingEncoding());

        act.Should().Throw<IOException>();

        File.Exists(path1).Should().BeFalse();
        File.Exists(path2).Should().BeFalse();
        Directory.GetFiles(directory.Path).Should().BeEmpty();
    }

    [Fact]
    public void OutputArtifactCommitterPropagatesAtomicWriteExceptionLedgerWhenCommitFails()
    {
        using var directory = new TemporaryDirectory();
        var templatesPath = Path.Combine(directory.Path, "templates.ini");
        var morphsPath = Path.Combine(directory.Path, "morphs.ini");
        File.WriteAllText(templatesPath, "OLD_TEMPLATES");
        File.WriteAllText(morphsPath, "OLD_MORPHS");
        var committer = new OutputArtifactCommitter();

        using (new FileStream(morphsPath, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => committer.Commit(directory.Path, BodyGenGroup("NEW_TEMPLATES", "NEW_MORPHS"));

            var exception = act.Should().Throw<AtomicWriteException>().Which;
            exception.Entries.Should().Contain(entry => entry.Path == templatesPath && entry.Outcome == FileWriteOutcome.Restored);
            exception.Entries.Should().Contain(entry => entry.Path == morphsPath && entry.Outcome == FileWriteOutcome.LeftUntouched);
        }
    }

    [Fact]
    public void ProjectFileServiceWriteAtomicPropagatesAtomicWriteExceptionLedgerWhenCommitFails()
    {
        using var directory = new TemporaryDirectory();
        var projectPath = Path.Combine(directory.Path, "project.jbs2bg");
        File.WriteAllText(projectPath, "OLD_PROJECT");
        var service = new ProjectFileService();

        using (new FileStream(projectPath, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => service.Save(new ProjectModel(), projectPath);

            var exception = act.Should().Throw<AtomicWriteException>().Which;
            exception.Entries.Should().ContainSingle()
                .Which.Should().Match<FileWriteLedgerEntry>(entry =>
                    entry.Path == projectPath && entry.Outcome == FileWriteOutcome.LeftUntouched);
        }

        File.ReadAllText(projectPath).Should().Be("OLD_PROJECT");
    }

    private static OutputArtifactCommitGroup BodyGenGroup(string templates, string morphs) => new(
        OutputArtifactGroupKind.BodyGen,
        new[]
        {
            Artifact(OutputArtifactRole.BodyGenTemplates, "templates.ini", templates),
            Artifact(OutputArtifactRole.BodyGenMorphs, "morphs.ini", morphs),
        });

    private static OutputArtifactCommitGroup BosGroup(params string[] paths) => new(
        OutputArtifactGroupKind.BosJson,
        paths.Select(path => Artifact(OutputArtifactRole.BosPresetJson, path, "{}")));

    private static OutputArtifact Artifact(OutputArtifactRole role, string path, string content) =>
        new(role, path, new UTF8Encoding(false).GetBytes(content));

    private sealed class FaultingEncoding : Encoding
    {
        private static readonly Encoding Inner = UTF8;

        public override int GetByteCount(char[] chars, int index, int count) =>
            Inner.GetByteCount(chars, index, count);

        public override int GetBytes(char[] chars, int charIndex, int charCount, byte[] bytes, int byteIndex) =>
            throw new IOException("simulated mid-write failure");

        public override int GetCharCount(byte[] bytes, int index, int count) =>
            Inner.GetCharCount(bytes, index, count);

        public override int GetChars(byte[] bytes, int byteIndex, int byteCount, char[] chars, int charIndex) =>
            Inner.GetChars(bytes, byteIndex, byteCount, chars, charIndex);

        public override int GetMaxByteCount(int charCount) => Inner.GetMaxByteCount(charCount);

        public override int GetMaxCharCount(int byteCount) => Inner.GetMaxCharCount(byteCount);
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose() => Directory.Delete(Path, true);
    }
}
