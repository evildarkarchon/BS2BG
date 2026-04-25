namespace BS2BG.Core.Import;

public sealed class BodySlideXmlImportDiagnostic(string source, string message)
{
    public string Source { get; } = source ?? string.Empty;

    public string Message { get; } = message ?? throw new ArgumentNullException(nameof(message));
}
