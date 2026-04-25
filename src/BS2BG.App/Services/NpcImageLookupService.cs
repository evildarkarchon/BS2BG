using BS2BG.Core.Models;

namespace BS2BG.App.Services;

public sealed class NpcImageLookupService : INpcImageLookupService
{
    private static readonly string[] SupportedExtensions = { ".jpg", ".jpeg", ".png", ".bmp" };

    private static readonly StringComparison PathComparison =
        OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal;

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

        var imagesDirectory = EnsureTrailingDirectorySeparator(
            Path.GetFullPath(Path.Combine(workingDirectory, "images")));

        foreach (var fileName in EnumerateCandidateFileNames(npc))
        {
            var candidate = TryGetContainedCandidatePath(imagesDirectory, fileName);
            if (candidate is null) continue;

            if (File.Exists(candidate)) return candidate;
        }

        return null;
    }

    private static IEnumerable<string> EnumerateCandidateFileNames(Npc npc)
    {
        if (TryCreateSpecificFileStem(npc, out var specificStem))
            foreach (var extension in SupportedExtensions)
                yield return specificStem + extension;

        if (TryCreateNameOnlyFileStem(npc, out var nameOnlyStem))
            foreach (var extension in SupportedExtensions)
                yield return nameOnlyStem + extension;
    }

    private static bool TryCreateSpecificFileStem(Npc npc, out string? fileStem)
    {
        fileStem = null;
        if (!IsSafeCandidateComponent(npc.Name) || !IsSafeCandidateComponent(npc.EditorId)) return false;

        fileStem = npc.Name + " (" + npc.EditorId + ")";
        return true;
    }

    private static bool TryCreateNameOnlyFileStem(Npc npc, out string? fileStem)
    {
        fileStem = null;
        if (!IsSafeCandidateComponent(npc.Name)) return false;

        fileStem = npc.Name;
        return true;
    }

    private static bool IsSafeCandidateComponent(string value)
    {
        return !Path.IsPathRooted(value)
               && value.IndexOf(Path.DirectorySeparatorChar) < 0
               && value.IndexOf(Path.AltDirectorySeparatorChar) < 0
               && value.IndexOf(Path.VolumeSeparatorChar) < 0;
    }

    private static string? TryGetContainedCandidatePath(string imagesDirectory, string fileName)
    {
        var candidatePath = Path.GetFullPath(Path.Combine(imagesDirectory, fileName));
        return candidatePath.StartsWith(imagesDirectory, PathComparison)
            ? candidatePath
            : null;
    }

    private static string EnsureTrailingDirectorySeparator(string path)
    {
        return Path.EndsInDirectorySeparator(path)
            ? path
            : path + Path.DirectorySeparatorChar;
    }
}
