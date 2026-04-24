using Avalonia.Controls;
using Avalonia.Platform.Storage;

namespace BS2BG.App.Services;

public sealed class WindowBodySlideXmlFilePicker : IBodySlideXmlFilePicker
{
    private static readonly string[] XmlPatterns = { "*.xml" };
    private static readonly string[] XmlMimeTypes = { "application/xml", "text/xml" };
    private TopLevel? owner;

    public async Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken)
    {
        if (owner?.StorageProvider.CanOpen != true) return Array.Empty<string>();

        var files = await owner.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Add BodySlide XML Presets",
            AllowMultiple = true,
            FileTypeFilter = new[]
            {
                new FilePickerFileType("BodySlide XML") { Patterns = XmlPatterns, MimeTypes = XmlMimeTypes }
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
