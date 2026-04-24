using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using BS2BG.Core.Models;

namespace BS2BG.App.Services;

public sealed class WindowImageViewService : IImageViewService
{
    private Image? imageControl;
    private Window? owner;
    private Window? window;

    public void ShowImage(Npc npc, string? imagePath)
    {
        ArgumentNullException.ThrowIfNull(npc);

        EnsureWindow();
        window!.Title = npc.Name;
        imageControl!.Source = CreateBitmap(imagePath);

        if (!window.IsVisible)
        {
            if (owner is null)
                window.Show();
            else
                window.Show(owner);
        }

        window.Activate();
    }

    public void Attach(Window owner)
    {
        ArgumentNullException.ThrowIfNull(owner);

        this.owner = owner;
    }

    private void EnsureWindow()
    {
        if (window is not null) return;

        imageControl = new Image { Stretch = Stretch.None };
        window = new Window
        {
            Width = 290,
            Height = 256,
            MinWidth = 290,
            MinHeight = 256,
            CanResize = true,
            Topmost = true,
            Content = new ScrollViewer
            {
                HorizontalScrollBarVisibility = ScrollBarVisibility.Auto,
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                Content = imageControl
            }
        };
    }

    private static Bitmap? CreateBitmap(string? imagePath)
    {
        if (string.IsNullOrWhiteSpace(imagePath) || !File.Exists(imagePath)) return null;

        try
        {
            using var stream = File.OpenRead(imagePath);
            if (!HasSupportedImageSignature(stream)) return null;

            stream.Position = 0;
            return new Bitmap(stream);
        }
        catch (Exception exception) when (
            exception is IOException
            or UnauthorizedAccessException
            or ArgumentException
            or NotSupportedException)
        {
            return null;
        }
    }

    private static bool HasSupportedImageSignature(Stream stream)
    {
        Span<byte> header = stackalloc byte[12];
        var read = stream.Read(header);
        var bytes = header[..read];

        return bytes.StartsWith(stackalloc byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A })
               || bytes.StartsWith(stackalloc byte[] { 0xFF, 0xD8, 0xFF })
               || bytes.StartsWith("GIF87a"u8)
               || bytes.StartsWith("GIF89a"u8)
               || bytes.StartsWith("BM"u8)
               || (bytes.StartsWith("RIFF"u8) && read >= 12 && bytes[8..12].SequenceEqual("WEBP"u8));
    }
}
