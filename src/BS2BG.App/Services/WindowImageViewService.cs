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

        return new Bitmap(imagePath);
    }
}
