using System.CommandLine;
using BS2BG.Core.Automation;

namespace BS2BG.Cli;

internal static class Program
{
    /// <summary>
    /// Parses command-line input and returns System.CommandLine's script-friendly exit code.
    /// </summary>
    public static int Main(string[] args) => CreateRootCommand().Parse(args).Invoke();

    /// <summary>
    /// Creates the CLI command tree with the explicit generation options required by the automation contract.
    /// </summary>
    internal static RootCommand CreateRootCommand()
    {
        var projectOption = new Option<FileInfo>("--project")
        {
            Description = "Path to the .jbs2bg project file to generate from.",
            Required = true,
        };

        var outputOption = new Option<DirectoryInfo>("--output")
        {
            Description = "Directory where generated output files will be written.",
            Required = true,
        };

        var intentOption = new Option<string>("--intent")
        {
            Description = "Output intent to generate: bodygen, bos, or all.",
            Required = true,
        };
        intentOption.AcceptOnlyFromAmong("bodygen", "bos", "all");

        var overwriteOption = new Option<bool>("--overwrite")
        {
            Description = "Allow generation to replace existing output files.",
        };

        var omitRedundantSlidersOption = new Option<bool>("--omit-redundant-sliders")
        {
            Description = "Omit redundant slider values to match the GUI preference.",
        };

        var generateCommand = new Command("generate", "Generate BS2BG outputs from a saved project.")
        {
            projectOption,
            outputOption,
            intentOption,
            overwriteOption,
            omitRedundantSlidersOption,
        };

        generateCommand.SetAction(parseResult =>
        {
            _ = new HeadlessGenerationRequest(
                parseResult.GetValue(projectOption)!.FullName,
                parseResult.GetValue(outputOption)!.FullName,
                ParseOutputIntent(parseResult.GetValue(intentOption)),
                parseResult.GetValue(overwriteOption),
                parseResult.GetValue(omitRedundantSlidersOption));

            return (int)HeadlessGenerationExitCode.Success;
        });

        var rootCommand = new RootCommand("bs2bg automation CLI");
        rootCommand.Subcommands.Add(generateCommand);
        return rootCommand;
    }

    /// <summary>
    /// Converts the parser-constrained intent token into the Core automation enum used by downstream services.
    /// </summary>
    private static OutputIntent ParseOutputIntent(string? intent) => intent switch
    {
        "bodygen" => OutputIntent.BodyGen,
        "bos" => OutputIntent.BosJson,
        "all" => OutputIntent.All,
        _ => OutputIntent.All,
    };
}
