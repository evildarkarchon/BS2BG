using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;
using System.Text;
using BS2BG.Core.Models;

namespace BS2BG.Core.Import;

[SuppressMessage("Performance", "CA1822:Mark members as static", Justification = "NPC import is exposed as an injectable service surface.")]
public sealed class NpcTextParser
{
    private static readonly Encoding StrictUtf8 = new UTF8Encoding(
        encoderShouldEmitUTF8Identifier: false,
        throwOnInvalidBytes: true);

    static NpcTextParser()
    {
        Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
    }

    public NpcImportResult ParseFile(string path)
    {
        if (path is null)
        {
            throw new ArgumentNullException(nameof(path));
        }

        return ParseBytes(File.ReadAllBytes(path));
    }

    public NpcImportResult ParseText(string text)
    {
        if (text is null)
        {
            throw new ArgumentNullException(nameof(text));
        }

        return ParseDecodedText(text, usedFallbackEncoding: false, encodingName: "UTF-16");
    }

    private static NpcImportResult ParseBytes(byte[] bytes)
    {
        if (TryDecodeBom(bytes, out var text, out var encodingName))
        {
            return ParseDecodedText(text, usedFallbackEncoding: false, encodingName);
        }

        try
        {
            text = StrictUtf8.GetString(bytes);
            return ParseDecodedText(text, usedFallbackEncoding: false, StrictUtf8.WebName);
        }
        catch (DecoderFallbackException)
        {
            var fallbackEncoding = GetFallbackEncoding();
            text = fallbackEncoding.GetString(bytes);
            return ParseDecodedText(text, usedFallbackEncoding: true, fallbackEncoding.WebName);
        }
    }

    private static NpcImportResult ParseDecodedText(
        string text,
        bool usedFallbackEncoding,
        string encodingName)
    {
        var npcs = new List<Npc>();
        var diagnostics = new List<NpcImportDiagnostic>();
        var seen = new HashSet<NpcKey>();
        var normalizedText = text.Replace("\r\n", "\n", StringComparison.Ordinal)
            .Replace('\r', '\n');
        var lines = normalizedText.Split('\n');

        for (var index = 0; index < lines.Length; index++)
        {
            var lineNumber = index + 1;
            var line = lines[index].Trim();
            if (line.Length == 0)
            {
                continue;
            }

            var parts = line.Split('|');
            if (parts.Length < 5)
            {
                diagnostics.Add(new NpcImportDiagnostic(
                    lineNumber,
                    "NPC row must contain Mod|Name|EditorID|Race|FormID."));
                continue;
            }

            var mod = parts[0].Trim();
            var name = parts[1].Trim();
            var editorId = parts[2].Trim();
            var race = TrimRace(parts[3]);
            var formId = parts[4].Trim();
            if (name.Length == 0)
            {
                name = "Unnamed (" + editorId + ")";
            }

            var key = new NpcKey(mod, editorId);
            if (!seen.Add(key))
            {
                continue;
            }

            npcs.Add(new Npc(name)
            {
                Mod = mod,
                EditorId = editorId,
                Race = race,
                FormId = formId,
            });
        }

        return new NpcImportResult(npcs, diagnostics, usedFallbackEncoding, encodingName);
    }

    private static bool TryDecodeBom(byte[] bytes, out string text, out string encodingName)
    {
        foreach (var candidate in BomEncoding.Candidates)
        {
            if (!StartsWith(bytes, candidate.Preamble))
            {
                continue;
            }

            text = candidate.Encoding.GetString(
                bytes,
                candidate.Preamble.Length,
                bytes.Length - candidate.Preamble.Length);
            encodingName = candidate.Encoding.WebName;
            return true;
        }

        text = string.Empty;
        encodingName = string.Empty;
        return false;
    }

    private static bool StartsWith(byte[] bytes, byte[] prefix)
    {
        if (bytes.Length < prefix.Length)
        {
            return false;
        }

        for (var index = 0; index < prefix.Length; index++)
        {
            if (bytes[index] != prefix[index])
            {
                return false;
            }
        }

        return true;
    }

    private static Encoding GetFallbackEncoding()
    {
        if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            var defaultEncoding = Encoding.GetEncoding(0);
            return defaultEncoding.CodePage == Encoding.UTF8.CodePage
                ? Encoding.GetEncoding(1252)
                : defaultEncoding;
        }

        return Encoding.GetEncoding(1252);
    }

    private static string TrimRace(string value)
    {
        var race = value.Trim();
        var quoteIndex = race.IndexOf('"', StringComparison.Ordinal);
        if (quoteIndex >= 0)
        {
            race = race.Substring(0, quoteIndex).Trim();
        }

        return race;
    }

    private readonly struct NpcKey : IEquatable<NpcKey>
    {
        private readonly string mod;
        private readonly string editorId;

        public NpcKey(string mod, string editorId)
        {
            this.mod = mod;
            this.editorId = editorId;
        }

        public bool Equals(NpcKey other)
        {
            return string.Equals(mod, other.mod, StringComparison.OrdinalIgnoreCase)
                && string.Equals(editorId, other.editorId, StringComparison.OrdinalIgnoreCase);
        }

        public override bool Equals(object? obj)
        {
            return obj is NpcKey other && Equals(other);
        }

        public override int GetHashCode()
        {
            return HashCode.Combine(
                StringComparer.OrdinalIgnoreCase.GetHashCode(mod),
                StringComparer.OrdinalIgnoreCase.GetHashCode(editorId));
        }
    }

    private sealed class BomEncoding
    {
        public static readonly BomEncoding[] Candidates =
        {
            new(new byte[] { 0xEF, 0xBB, 0xBF }, StrictUtf8),
            new(new byte[] { 0xFF, 0xFE, 0x00, 0x00 }, new UTF32Encoding(bigEndian: false, byteOrderMark: true, throwOnInvalidCharacters: true)),
            new(new byte[] { 0x00, 0x00, 0xFE, 0xFF }, new UTF32Encoding(bigEndian: true, byteOrderMark: true, throwOnInvalidCharacters: true)),
            new(new byte[] { 0xFF, 0xFE }, Encoding.Unicode),
            new(new byte[] { 0xFE, 0xFF }, Encoding.BigEndianUnicode),
        };

        private BomEncoding(byte[] preamble, Encoding encoding)
        {
            Preamble = preamble;
            Encoding = encoding;
        }

        public byte[] Preamble { get; }

        public Encoding Encoding { get; }
    }
}
