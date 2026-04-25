using System.Text;

namespace BS2BG.Core.IO;

public static class AtomicFileWriter
{
    public static void WriteAtomic(string targetPath, string content, Encoding encoding)
    {
        if (targetPath is null) throw new ArgumentNullException(nameof(targetPath));

        if (content is null) throw new ArgumentNullException(nameof(content));

        if (encoding is null) throw new ArgumentNullException(nameof(encoding));

        var fullPath = Path.GetFullPath(targetPath);
        var tempPath = CreateTempPath(fullPath);
        var replaced = false;
        try
        {
            File.WriteAllText(tempPath, content, encoding);
            ReplaceWithTempFile(tempPath, fullPath);
            replaced = true;
        }
        finally
        {
            if (!replaced) TryDeleteTempFile(tempPath);
        }
    }

    public static void WriteAtomicPair(
        string firstPath,
        string firstContent,
        string secondPath,
        string secondContent,
        Encoding encoding)
    {
        if (firstPath is null) throw new ArgumentNullException(nameof(firstPath));

        if (firstContent is null) throw new ArgumentNullException(nameof(firstContent));

        if (secondPath is null) throw new ArgumentNullException(nameof(secondPath));

        if (secondContent is null) throw new ArgumentNullException(nameof(secondContent));

        if (encoding is null) throw new ArgumentNullException(nameof(encoding));

        var firstTarget = Path.GetFullPath(firstPath);
        var secondTarget = Path.GetFullPath(secondPath);
        var firstTemp = CreateTempPath(firstTarget);
        string? secondTemp = null;
        var firstReplaced = false;
        var secondReplaced = false;
        try
        {
            File.WriteAllText(firstTemp, firstContent, encoding);

            secondTemp = CreateTempPath(secondTarget);
            File.WriteAllText(secondTemp, secondContent, encoding);

            ReplaceWithTempFile(firstTemp, firstTarget);
            firstReplaced = true;

            ReplaceWithTempFile(secondTemp, secondTarget);
            secondReplaced = true;
        }
        finally
        {
            if (!firstReplaced) TryDeleteTempFile(firstTemp);

            if (secondTemp is not null && !secondReplaced) TryDeleteTempFile(secondTemp);
        }
    }

    private static string CreateTempPath(string targetPath)
    {
        var directory = Path.GetDirectoryName(targetPath)
                        ?? throw new InvalidOperationException("Path must include a directory.");
        var fileName = Path.GetFileName(targetPath);
        return Path.Combine(directory, "." + fileName + "." + Guid.NewGuid().ToString("N") + ".tmp");
    }

    private static void ReplaceWithTempFile(string tempPath, string targetPath)
    {
        if (File.Exists(targetPath))
        {
            File.Replace(tempPath, targetPath, null);
            return;
        }

        File.Move(tempPath, targetPath);
    }

    private static void TryDeleteTempFile(string tempPath)
    {
        try
        {
            if (File.Exists(tempPath)) File.Delete(tempPath);
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }
}
