using System.Diagnostics;
using BS2BG.Core.Automation;
using Xunit;

namespace BS2BG.Tests;

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
}
