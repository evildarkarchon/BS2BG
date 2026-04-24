using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.App.Services;

public static class TemplateProfileCatalogFactory
{
    public static TemplateProfileCatalog CreateDefault()
    {
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, LoadOrCreateEmpty("settings.json")),
            new TemplateProfile(ProjectProfileMapping.SkyrimUunp, LoadOrCreateEmpty("settings_UUNP.json")),
            new TemplateProfile(ProjectProfileMapping.Fallout4Cbbe, LoadOrCreateEmpty("settings.json")),
        });
    }

    private static SliderProfile LoadOrCreateEmpty(string fileName)
    {
        var path = FindProfilePath(fileName);
        return path is null ? CreateEmptyProfile() : SliderProfileJsonService.Load(path);
    }

    private static SliderProfile CreateEmptyProfile()
    {
        return new SliderProfile(
            Array.Empty<SliderDefault>(),
            Array.Empty<SliderMultiplier>(),
            Array.Empty<string>());
    }

    private static string? FindProfilePath(string fileName)
    {
        foreach (var directory in CandidateDirectories())
        {
            var path = Path.Combine(directory.FullName, fileName);
            if (File.Exists(path))
            {
                return path;
            }
        }

        return null;
    }

    private static IEnumerable<DirectoryInfo> CandidateDirectories()
    {
        foreach (var path in new[] { AppContext.BaseDirectory, Environment.CurrentDirectory })
        {
            var directory = new DirectoryInfo(path);
            while (directory is not null)
            {
                yield return directory;

                if (File.Exists(Path.Combine(directory.FullName, "PRD.md")))
                {
                    break;
                }

                directory = directory.Parent;
            }
        }
    }
}
