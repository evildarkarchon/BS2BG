namespace BS2BG.Core.Import;

public sealed class BodySlideXmlImportDiagnostic
{
    public BodySlideXmlImportDiagnostic(string source, string message)
    {
        Source = source ?? string.Empty;
        Message = message ?? throw new ArgumentNullException(nameof(message));
    }

    public string Source { get; }

    public string Message { get; }
}
