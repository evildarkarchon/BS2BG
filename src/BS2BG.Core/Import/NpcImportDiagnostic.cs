namespace BS2BG.Core.Import;

public sealed class NpcImportDiagnostic(int lineNumber, string message)
{
    public int LineNumber { get; } = lineNumber;

    public string Message { get; } = message ?? throw new ArgumentNullException(nameof(message));
}
