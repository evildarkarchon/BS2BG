using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.App.Services;

public static class TemplateProfileCatalogFactory
{
    public static TemplateProfileCatalog CreateDefault() => CreateDefault(CandidateDirectories());

    public static TemplateProfileCatalog CreateDefault(IEnumerable<string> searchDirectories)
    {
        ArgumentNullException.ThrowIfNull(searchDirectories);

        return CreateDefault(searchDirectories.Select(directory => new DirectoryInfo(directory)));
    }

    private static TemplateProfileCatalog CreateDefault(IEnumerable<DirectoryInfo> searchDirectories)
    {
        var directories = searchDirectories.ToArray();

        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe,
                LoadRequiredProfile("settings.json", directories)),
            new TemplateProfile(ProjectProfileMapping.SkyrimUunp,
                LoadRequiredProfile("settings_UUNP.json", directories)),
            new TemplateProfile(ProjectProfileMapping.Fallout4Cbbe,
                LoadRequiredProfile("settings.json", directories))
        });
    }

    private static SliderProfile LoadRequiredProfile(string fileName, IEnumerable<DirectoryInfo> searchDirectories)
    {
        var directories = searchDirectories.ToArray();
        var path = FindProfilePath(fileName, directories);
        if (path is null) throw new FileNotFoundException(CreateMissingProfileMessage(fileName, directories), fileName);

        return SliderProfileJsonService.Load(path);
    }

    private static string? FindProfilePath(string fileName, IEnumerable<DirectoryInfo> searchDirectories)
    {
        foreach (var directory in searchDirectories)
        {
            var path = Path.Combine(directory.FullName, fileName);
            if (File.Exists(path)) return path;
        }

        return null;
    }

    private static string CreateMissingProfileMessage(string fileName, IEnumerable<DirectoryInfo> searchDirectories)
    {
        var searchedDirectories = string.Join(
            Environment.NewLine,
            searchDirectories
                .Select(directory => directory.FullName)
                .Distinct(StringComparer.OrdinalIgnoreCase));

        return "Required slider profile '" + fileName + "' was not found."
               + Environment.NewLine
               + "Searched directories:"
               + Environment.NewLine
               + searchedDirectories;
    }

    private static IEnumerable<DirectoryInfo> CandidateDirectories()
    {
        yield return new DirectoryInfo(AppContext.BaseDirectory);
    }
}
