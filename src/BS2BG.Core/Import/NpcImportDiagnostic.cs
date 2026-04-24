namespace BS2BG.Core.Import;

public sealed class NpcImportDiagnostic
{
    public NpcImportDiagnostic(int lineNumber, string message)
    {
        LineNumber = lineNumber;
        Message = message ?? throw new ArgumentNullException(nameof(message));
    }

    public int LineNumber { get; }

    public string Message { get; }
}
