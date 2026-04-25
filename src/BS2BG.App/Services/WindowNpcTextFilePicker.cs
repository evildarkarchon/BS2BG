using Avalonia.Controls;
using Avalonia.Platform.Storage;

namespace BS2BG.App.Services;

public sealed class WindowNpcTextFilePicker : INpcTextFilePicker
{
    private static readonly string[] TextPatterns = { "*.txt" };
    private static readonly string[] TextMimeTypes = { "text/plain" };
    private TopLevel? owner;

    public async Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken)
    {
        if (owner?.StorageProvider.CanOpen != true) return Array.Empty<string>();

        var files = await owner.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Add NPC Text File",
            AllowMultiple = true,
            FileTypeFilter = new[]
            {
                new FilePickerFileType("NPC text") { Patterns = TextPatterns, MimeTypes = TextMimeTypes }
            }
        });

        cancellationToken.ThrowIfCancellationRequested();

        return files
            .Where(file => file.Path.IsFile)
            .Select(file => file.Path.LocalPath)
            .ToArray();
    }

    public void Attach(TopLevel topLevel) => owner = topLevel ?? throw new ArgumentNullException(nameof(topLevel));
}
