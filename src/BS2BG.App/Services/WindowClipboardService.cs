using Avalonia.Controls;
using Avalonia.Input.Platform;

namespace BS2BG.App.Services;

public sealed class WindowClipboardService : IClipboardService
{
    private TopLevel? owner;

    public void Attach(TopLevel topLevel)
    {
        owner = topLevel ?? throw new ArgumentNullException(nameof(topLevel));
    }

    public async Task SetTextAsync(string text, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();

        if (owner?.Clipboard is null)
        {
            return;
        }

        await owner.Clipboard.SetTextAsync(text);
    }
}
