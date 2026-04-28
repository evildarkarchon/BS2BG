using System.Text.RegularExpressions;

namespace BS2BG.Core.Bundling;

/// <summary>
/// Centralizes archive entry normalization and private local path scrubbing for portable support bundles.
/// </summary>
public static class BundlePathScrubber
{
    private static readonly Regex DriveRootPattern = new(@"[A-Za-z]:", RegexOptions.Compiled);
    private static readonly Regex UncPrefixPattern = new(@"\\\\[^\s\\/]+\\[^\s\\/]+", RegexOptions.Compiled);

    /// <summary>
    /// Converts a bundle entry path to a deterministic forward-slash relative name and rejects unsafe segments.
    /// </summary>
    /// <param name="relativePath">Candidate archive entry path.</param>
    /// <returns>Normalized bundle-relative entry path.</returns>
    /// <exception cref="ArgumentException">Thrown when the path is rooted, empty, or contains traversal segments.</exception>
    public static string NormalizeEntryPath(string relativePath)
    {
        if (relativePath is null) throw new ArgumentNullException(nameof(relativePath));
        if (Path.IsPathRooted(relativePath)) throw new ArgumentException("Bundle entry paths must be relative.", nameof(relativePath));

        var normalized = relativePath.Replace('\\', '/');
        if (normalized.Length == 0) throw new ArgumentException("Bundle entry paths must not be empty.", nameof(relativePath));
        if (normalized.StartsWith('/')) throw new ArgumentException("Bundle entry paths must be relative.", nameof(relativePath));

        var segments = normalized.Split('/');
        if (segments.Any(segment => segment.Length == 0 || segment == ".."))
            throw new ArgumentException("Bundle entry paths must not contain empty or traversal segments.", nameof(relativePath));

        return string.Join("/", segments);
    }

    /// <summary>
    /// Detects whether text contains private-path markers that should never appear in a bundle manifest or report.
    /// </summary>
    /// <param name="text">Text to scan.</param>
    /// <returns><see langword="true"/> when drive roots, UNC paths, backslashes, or the current user name are present.</returns>
    public static bool IsPrivatePathLeak(string text)
    {
        if (string.IsNullOrEmpty(text)) return false;

        // Literal text such as "C:" may be a false positive, but preventing private path disclosure is higher risk than over-reporting.
        if (DriveRootPattern.IsMatch(text) || UncPrefixPattern.IsMatch(text) || text.Contains('\\')) return true;

        var userName = Environment.UserName;
        return !string.IsNullOrWhiteSpace(userName)
               && text.Contains(userName, StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>
    /// Replaces known private roots and general rooted path markers with a stable placeholder for support artifacts.
    /// </summary>
    /// <param name="text">Report or manifest text to scrub.</param>
    /// <param name="privateRoots">Caller-provided local roots such as project, import, or export directories.</param>
    /// <returns>Text with private roots redacted.</returns>
    public static string Scrub(string text, IEnumerable<string> privateRoots)
    {
        if (text is null) throw new ArgumentNullException(nameof(text));

        var scrubbed = text;
        foreach (var root in privateRoots ?? Enumerable.Empty<string>())
        {
            if (string.IsNullOrWhiteSpace(root)) continue;

            scrubbed = scrubbed.Replace(root, "[redacted-path]", StringComparison.OrdinalIgnoreCase);
            scrubbed = scrubbed.Replace(root.Replace('\\', '/'), "[redacted-path]", StringComparison.OrdinalIgnoreCase);
        }

        var userName = Environment.UserName;
        if (!string.IsNullOrWhiteSpace(userName))
            scrubbed = scrubbed.Replace(userName, "[redacted-user]", StringComparison.OrdinalIgnoreCase);

        return scrubbed;
    }
}
