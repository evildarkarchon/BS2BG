using BS2BG.Core.Diagnostics;

namespace BS2BG.App.ViewModels.Workflow;

/// <summary>
/// Presents a single file that an export command would create or overwrite before disk writes occur.
/// </summary>
public sealed class ExportPreviewViewModel
{
    /// <summary>
    /// Creates binding-ready export preview state from a Core preview file.
    /// </summary>
    /// <param name="kind">User-facing export category, such as BodyGen or BoS JSON.</param>
    /// <param name="file">Core preview facts for the target file.</param>
    public ExportPreviewViewModel(string kind, ExportPreviewFile file)
    {
        Kind = kind ?? throw new ArgumentNullException(nameof(kind));
        if (file is null) throw new ArgumentNullException(nameof(file));

        TargetPath = file.Path;
        IsOverwrite = file.WillOverwrite;
        EffectLabel = file.WillOverwrite ? "Overwrite" : "Create";
        SnippetLines = file.SnippetLines.ToArray();
    }

    public string Kind { get; }

    public string TargetPath { get; }

    public string EffectLabel { get; }

    public bool IsOverwrite { get; }

    public IReadOnlyList<string> SnippetLines { get; }
}
