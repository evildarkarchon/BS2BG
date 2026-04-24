using BS2BG.Core.Models;

namespace BS2BG.App.Services;

public sealed class NpcImageLookupService : INpcImageLookupService
{
    private static readonly string[] SupportedExtensions =
    {
        ".jpg",
        ".jpeg",
        ".png",
        ".bmp",
    };

    private readonly string workingDirectory;

    public NpcImageLookupService()
        : this(Directory.GetCurrentDirectory())
    {
    }

    public NpcImageLookupService(string workingDirectory)
    {
        ArgumentNullException.ThrowIfNull(workingDirectory);

        this.workingDirectory = workingDirectory;
    }

    public string? FindImagePath(Npc npc)
    {
        ArgumentNullException.ThrowIfNull(npc);

        var imagesDirectory = Path.Combine(workingDirectory, "images");
        foreach (var extension in SupportedExtensions)
        {
            var candidate = Path.Combine(imagesDirectory, npc.Name + " (" + npc.EditorId + ")" + extension);
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        foreach (var extension in SupportedExtensions)
        {
            var candidate = Path.Combine(imagesDirectory, npc.Name + extension);
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        return null;
    }
}
