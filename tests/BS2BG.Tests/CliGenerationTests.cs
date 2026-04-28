using System.Diagnostics;
using BS2BG.Core.Automation;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;

namespace BS2BG.Tests;

[CollectionDefinition(ConsoleCaptureCollectionName)]
public sealed class ConsoleCaptureCollection : ICollectionFixture<ConsoleCaptureFixture>
{
    public const string ConsoleCaptureCollectionName = "Console capture";
}

public sealed class ConsoleCaptureFixture;

[Collection(ConsoleCaptureCollection.ConsoleCaptureCollectionName)]
public sealed class CliGenerationTests
{
    private static readonly string[] ExpectedCliPublishEntryNames = ["BS2BG.Cli", "BS2BG.Cli.exe", "BS2BG.Cli.dll"];

    [Fact]
    public void CliProjectIsRegisteredAsDedicatedSolutionExecutable()
    {
        var repoRoot = FindRepoRoot();
        var projectPath = Path.Combine(repoRoot, "src", "BS2BG.Cli", "BS2BG.Cli.csproj");

        File.Exists(projectPath).Should().BeTrue("AUTO-01 requires a dedicated CLI project separate from the Avalonia app executable");

        var projectText = File.ReadAllText(projectPath);
        projectText.Should().Contain("<OutputType>Exe</OutputType>");
        projectText.Should().Contain("<TargetFramework>net10.0</TargetFramework>");
        projectText.Should().Contain("<PackageReference Include=\"System.CommandLine\"");
        projectText.Should().Contain("<ProjectReference Include=\"..\\BS2BG.Core\\BS2BG.Core.csproj\"");
        projectText.Should().NotContain("BS2BG.App");
        projectText.Should().NotContain("Avalonia");

        var solutionList = RunProcess(repoRoot, "dotnet", "sln BS2BG.sln list");
        solutionList.ExitCode.Should().Be(0, solutionList.StandardError);
        solutionList.StandardOutput.Should().Contain("src\\BS2BG.Cli\\BS2BG.Cli.csproj");
    }

    [Fact]
    public void MissingRequiredGenerateOptionsReturnUsageExitCodeAndHelpText()
    {
        var repoRoot = FindRepoRoot();
        var result = RunProcess(repoRoot, "dotnet", "run --project src/BS2BG.Cli/BS2BG.Cli.csproj -- generate");
        var combinedOutput = result.StandardOutput + result.StandardError;

        result.ExitCode.Should().Be(1);
        combinedOutput.Should().Contain("Usage:");
        combinedOutput.Should().Contain("--project");
        combinedOutput.Should().Contain("--output");
        combinedOutput.Should().Contain("--intent");
    }

    [Fact]
    public void GenerateCommandDeclaresExplicitOutputIntentValues()
    {
        var repoRoot = FindRepoRoot();
        var result = RunProcess(repoRoot, "dotnet", "run --project src/BS2BG.Cli/BS2BG.Cli.csproj -- generate --help");
        var combinedOutput = result.StandardOutput + result.StandardError;

        result.ExitCode.Should().Be(0, combinedOutput);
        combinedOutput.Should().Contain("bodygen");
        combinedOutput.Should().Contain("bos");
        combinedOutput.Should().Contain("all");
        combinedOutput.Should().Contain("--overwrite");
        combinedOutput.Should().Contain("--omit-redundant-sliders");
    }

    [Fact]
    public void BuildOutputCopiesBundledProfileAssetsBesideCliAssembly()
    {
        var repoRoot = FindRepoRoot();
        var build = RunProcess(repoRoot, "dotnet", "build src/BS2BG.Cli/BS2BG.Cli.csproj");
        build.ExitCode.Should().Be(0, build.StandardOutput + build.StandardError);

        var outputDirectory = Path.Combine(repoRoot, "src", "BS2BG.Cli", "bin", "Debug", "net10.0");
        AssertProfileAssetsExist(outputDirectory);
    }

    [Fact]
    public void PublishOutputCopiesBundledProfileAssetsAndApphostLayout()
    {
        var repoRoot = FindRepoRoot();
        var publishDirectory = Path.Combine(Path.GetTempPath(), "bs2bg-cli-publish-" + Guid.NewGuid().ToString("N"));

        try
        {
            var publish = RunProcess(repoRoot, "dotnet", $"publish src/BS2BG.Cli/BS2BG.Cli.csproj -c Release -o \"{publishDirectory}\"");
            publish.ExitCode.Should().Be(0, publish.StandardOutput + publish.StandardError);

            AssertProfileAssetsExist(publishDirectory);
            Directory.EnumerateFiles(publishDirectory, "BS2BG.Cli*", SearchOption.TopDirectoryOnly)
                .Select(Path.GetFileName)
                .Should().IntersectWith(ExpectedCliPublishEntryNames);
        }
        finally
        {
            if (Directory.Exists(publishDirectory)) Directory.Delete(publishDirectory, recursive: true);
        }
    }

    [Fact]
    public void CoreHeadlessGenerationContractsExposeStableAutomationEnums()
    {
        Enum.GetNames<OutputIntent>().Should().Equal("BodyGen", "BosJson", "All");

        Enum.GetValues<HeadlessGenerationExitCode>().Cast<int>().Should().Equal(0, 1, 2, 3, 4);
        Enum.GetNames<HeadlessGenerationExitCode>().Should()
            .Equal("Success", "UsageError", "ValidationBlocked", "OverwriteRefused", "IoFailure");
    }

    [Fact]
    public void HeadlessGenerationRequestCarriesOmitRedundantSlidersPreference()
    {
        var request = new HeadlessGenerationRequest(
            "project.jbs2bg",
            "out",
            OutputIntent.All,
            Overwrite: true,
            OmitRedundantSliders: true);

        request.ProjectPath.Should().Be("project.jbs2bg");
        request.OutputDirectory.Should().Be("out");
        request.Intent.Should().Be(OutputIntent.All);
        request.Overwrite.Should().BeTrue();
        request.OmitRedundantSliders.Should().BeTrue();
    }

    [Fact]
    public void HeadlessGenerationServiceReturnsValidationBlockedBeforeCreatingOutputs()
    {
        using var directory = new TemporaryDirectory();
        var projectPath = Path.Combine(directory.Path, "empty.jbs2bg");
        new ProjectFileService().Save(new ProjectModel(), projectPath);
        var outputDirectory = Path.Combine(directory.Path, "out");

        var result = CreateHeadlessService().Run(new HeadlessGenerationRequest(
            projectPath,
            outputDirectory,
            OutputIntent.All,
            Overwrite: false,
            OmitRedundantSliders: false));

        result.ExitCode.Should().Be(HeadlessGenerationExitCode.ValidationBlocked);
        result.ValidationReport.Should().NotBeNull();
        result.Message.Should().Contain("No presets available");
        Directory.Exists(outputDirectory).Should().BeFalse("validation blockers must stop before any output directory writes");
    }

    [Fact]
    public void HeadlessGenerationServiceWritesBodyGenThroughCoreWriter()
    {
        using var directory = new TemporaryDirectory();
        var projectPath = SaveProject(directory.Path, CreateProjectWithAssignedPreset());
        var outputDirectory = Path.Combine(directory.Path, "out");

        var result = CreateHeadlessService().Run(new HeadlessGenerationRequest(
            projectPath,
            outputDirectory,
            OutputIntent.BodyGen,
            Overwrite: false,
            OmitRedundantSliders: false));

        result.ExitCode.Should().Be(HeadlessGenerationExitCode.Success);
        result.WrittenFiles.Should().BeEquivalentTo(
            Path.Combine(outputDirectory, "templates.ini"),
            Path.Combine(outputDirectory, "morphs.ini"));
        File.ReadAllText(Path.Combine(outputDirectory, "templates.ini")).Should().Contain("Alpha=Breasts@");
        File.ReadAllText(Path.Combine(outputDirectory, "morphs.ini")).Should().Be("All|Female=Alpha");
    }

    [Fact]
    public void HeadlessGenerationServiceWritesBosJsonThroughCoreWriterAndPlannerNames()
    {
        using var directory = new TemporaryDirectory();
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset:One"));
        project.SliderPresets.Add(new SliderPreset("Preset?One"));
        var projectPath = SaveProject(directory.Path, project);
        var outputDirectory = Path.Combine(directory.Path, "out");

        var result = CreateHeadlessService().Run(new HeadlessGenerationRequest(
            projectPath,
            outputDirectory,
            OutputIntent.BosJson,
            Overwrite: false,
            OmitRedundantSliders: false));

        result.ExitCode.Should().Be(HeadlessGenerationExitCode.Success);
        result.WrittenFiles.Should().Equal(
            Path.Combine(outputDirectory, "Preset_One.json"),
            Path.Combine(outputDirectory, "Preset_One (2).json"));
    }

    [Fact]
    public void HeadlessGenerationServiceRefusesAnyExistingAllIntentTargetBeforeWriting()
    {
        using var directory = new TemporaryDirectory();
        var projectPath = SaveProject(directory.Path, CreateProjectWithAssignedPreset());
        var outputDirectory = Path.Combine(directory.Path, "out");
        Directory.CreateDirectory(outputDirectory);
        var existingBos = Path.Combine(outputDirectory, "Alpha.json");
        File.WriteAllText(existingBos, "ORIGINAL_BOS");

        var result = CreateHeadlessService().Run(new HeadlessGenerationRequest(
            projectPath,
            outputDirectory,
            OutputIntent.All,
            Overwrite: false,
            OmitRedundantSliders: false));

        result.ExitCode.Should().Be(HeadlessGenerationExitCode.OverwriteRefused);
        result.Message.Should().Contain("Target files already exist. Enable overwrite to replace them.");
        File.ReadAllText(existingBos).Should().Be("ORIGINAL_BOS");
        File.Exists(Path.Combine(outputDirectory, "templates.ini")).Should().BeFalse();
        File.Exists(Path.Combine(outputDirectory, "morphs.ini")).Should().BeFalse();
    }

    [Fact]
    public void HeadlessGenerationServiceReportsPartialLedgerWhenBosFailsAfterBodyGenSucceeds()
    {
        using var directory = new TemporaryDirectory();
        var projectPath = SaveProject(directory.Path, CreateProjectWithAssignedPreset());
        var outputDirectory = Path.Combine(directory.Path, "out");
        Directory.CreateDirectory(outputDirectory);
        Directory.CreateDirectory(Path.Combine(outputDirectory, "Alpha.json"));

        var result = CreateHeadlessService().Run(new HeadlessGenerationRequest(
            projectPath,
            outputDirectory,
            OutputIntent.All,
            Overwrite: true,
            OmitRedundantSliders: false));

        result.ExitCode.Should().Be(HeadlessGenerationExitCode.IoFailure);
        result.Message.Should().Contain("BodyGen artifacts remain present");
        File.Exists(Path.Combine(outputDirectory, "templates.ini")).Should().BeTrue();
        File.Exists(Path.Combine(outputDirectory, "morphs.ini")).Should().BeTrue();
        result.WriteLedger.Should().Contain(entry => entry.Path.EndsWith("templates.ini") && entry.Outcome == FileWriteOutcome.Written);
        result.WriteLedger.Should().Contain(entry => entry.Path.EndsWith("Alpha.json") && entry.Outcome == FileWriteOutcome.LeftUntouched);
    }

    [Fact]
    public void HeadlessGenerationServiceMapsUsageOverwriteAndIoFailuresToStableExitCodes()
    {
        using var directory = new TemporaryDirectory();
        var service = CreateHeadlessService();
        var malformedPath = Path.Combine(directory.Path, "bad.jbs2bg");
        File.WriteAllText(malformedPath, "{ not json");
        var outputFilePath = Path.Combine(directory.Path, "not-a-directory");
        File.WriteAllText(outputFilePath, string.Empty);
        var projectPath = SaveProject(directory.Path, CreateProjectWithAssignedPreset());
        var overwritePath = Path.Combine(directory.Path, "overwrite");
        Directory.CreateDirectory(overwritePath);
        File.WriteAllText(Path.Combine(overwritePath, "templates.ini"), "old");

        service.Run(new HeadlessGenerationRequest(Path.Combine(directory.Path, "missing.jbs2bg"), directory.Path, OutputIntent.All, false, false))
            .ExitCode.Should().Be(HeadlessGenerationExitCode.UsageError);
        service.Run(new HeadlessGenerationRequest(malformedPath, directory.Path, OutputIntent.All, false, false))
            .ExitCode.Should().Be(HeadlessGenerationExitCode.UsageError);
        service.Run(new HeadlessGenerationRequest(projectPath, overwritePath, OutputIntent.BodyGen, false, false))
            .ExitCode.Should().Be(HeadlessGenerationExitCode.OverwriteRefused);
        service.Run(new HeadlessGenerationRequest(projectPath, outputFilePath, OutputIntent.BodyGen, true, false))
            .ExitCode.Should().Be(HeadlessGenerationExitCode.IoFailure);
    }

    [Fact]
    public void ProgramMapsIntentTextIntoTypedCoreRequestWithoutDirectOutputWrites()
    {
        var repoRoot = FindRepoRoot();
        var programText = File.ReadAllText(Path.Combine(repoRoot, "src", "BS2BG.Cli", "Program.cs"));

        programText.Should().Contain("HeadlessGenerationRequest");
        programText.Should().Contain("OutputIntent.BodyGen");
        programText.Should().Contain("OutputIntent.BosJson");
        programText.Should().Contain("OutputIntent.All");
        programText.Should().Contain("omitRedundantSlidersOption");
        programText.Should().NotContain("File.WriteAllText");
        programText.Should().NotContain("templates.ini");
        programText.Should().NotContain("morphs.ini");
    }

    private static HeadlessGenerationService CreateHeadlessService() => new(
        new ProjectFileService(),
        new TemplateGenerationService(),
        new MorphGenerationService(),
        new BodyGenIniExportWriter(),
        new BosJsonExportWriter(new TemplateGenerationService()),
        new BosJsonExportPlanner(),
        CreateCatalog());

    private static TemplateProfileCatalog CreateCatalog() => new(new[]
    {
        new TemplateProfile(
            ProjectProfileMapping.SkyrimCbbe,
            new SliderProfile(
                new[] { new SliderDefault("Breasts", 0.2f, 1f) },
                Array.Empty<SliderMultiplier>(),
                Array.Empty<string>()))
    });

    private static ProjectModel CreateProjectWithAssignedPreset()
    {
        var project = new ProjectModel();
        var preset = new SliderPreset("Alpha");
        preset.AddSetSlider(new ModelSetSlider("Breasts"));
        project.SliderPresets.Add(preset);
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(preset);
        project.CustomMorphTargets.Add(target);
        return project;
    }

    private static string SaveProject(string directory, ProjectModel project)
    {
        var path = Path.Combine(directory, "project-" + Guid.NewGuid().ToString("N") + ".jbs2bg");
        new ProjectFileService().Save(project, path);
        return path;
    }

    private static void AssertProfileAssetsExist(string directory)
    {
        File.Exists(Path.Combine(directory, "settings.json")).Should().BeTrue();
        File.Exists(Path.Combine(directory, "settings_UUNP.json")).Should().BeTrue();
        File.Exists(Path.Combine(directory, "settings_FO4_CBBE.json")).Should().BeTrue();
    }

    private static ProcessResult RunProcess(string workingDirectory, string fileName, string arguments)
    {
        using var process = new Process
        {
            StartInfo = new ProcessStartInfo(fileName, arguments)
            {
                WorkingDirectory = workingDirectory,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
            },
        };

        process.Start();
        var standardOutput = process.StandardOutput.ReadToEnd();
        var standardError = process.StandardError.ReadToEnd();
        process.WaitForExit();

        return new ProcessResult(process.ExitCode, standardOutput, standardError);
    }

    private static string FindRepoRoot()
    {
        var directory = AppContext.BaseDirectory;
        while (directory is not null)
        {
            if (File.Exists(Path.Combine(directory, "BS2BG.sln"))) return directory;

            directory = Directory.GetParent(directory)?.FullName;
        }

        throw new DirectoryNotFoundException("Could not find repository root.");
    }

    private sealed record ProcessResult(int ExitCode, string StandardOutput, string StandardError);

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
