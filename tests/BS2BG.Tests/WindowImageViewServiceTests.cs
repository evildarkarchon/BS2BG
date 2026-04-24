using System.Reflection;
using Avalonia.Controls;
using Avalonia.Headless.XUnit;
using BS2BG.Core.Models;
using BS2BG.App.Services;
using Xunit;

namespace BS2BG.Tests;

public sealed class WindowImageViewServiceTests
{
    [AvaloniaFact]
    public void CreateBitmapReturnsNullForInvalidImageFile()
    {
        using var directory = new TemporaryDirectory();
        var imagePath = directory.WriteBytes("corrupt.png", new byte[] { 0x89, 0x50, 0x4E, 0x47 });

        var bitmap = InvokeCreateBitmap(imagePath);

        bitmap.Should().BeNull();
        (bitmap as IDisposable)?.Dispose();
    }

    [AvaloniaFact]
    public void CreateBitmapLoadsSupportedImageFile()
    {
        using var directory = new TemporaryDirectory();
        var imagePath = directory.WriteBytes(
            "pixel.png",
            Convert.FromBase64String(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="));

        var bitmap = InvokeCreateBitmap(imagePath);

        bitmap.Should().NotBeNull();
        (bitmap as IDisposable)?.Dispose();
    }

    [AvaloniaFact]
    public void ShowImageRecreatesWindowAfterClose()
    {
        using var directory = new TemporaryDirectory();
        var imagePath = directory.WriteBytes(
            "pixel.png",
            Convert.FromBase64String(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="));
        var service = new WindowImageViewService();

        service.ShowImage(new Npc("Lydia"), imagePath);

        var firstWindow = GetWindow(service);
        firstWindow.Should().NotBeNull();
        firstWindow!.Title.Should().Be("Lydia");
        firstWindow.IsVisible.Should().BeTrue();

        firstWindow.Close();
        GetWindow(service).Should().BeNull();

        service.ShowImage(new Npc("Serana"), imagePath);

        var secondWindow = GetWindow(service);
        secondWindow.Should().NotBeNull();
        secondWindow.Should().NotBeSameAs(firstWindow);
        secondWindow!.Title.Should().Be("Serana");
        secondWindow.IsVisible.Should().BeTrue();
        secondWindow.Close();
    }

    private static object? InvokeCreateBitmap(string imagePath)
    {
        var createBitmap = typeof(WindowImageViewService).GetMethod(
                               "CreateBitmap",
                               BindingFlags.NonPublic | BindingFlags.Static)
                           ?? throw new InvalidOperationException("CreateBitmap was not found.");
        return createBitmap.Invoke(null, new object?[] { imagePath });
    }

    private static Window? GetWindow(WindowImageViewService service)
    {
        var field = typeof(WindowImageViewService).GetField(
                        "window",
                        BindingFlags.Instance | BindingFlags.NonPublic)
                    ?? throw new InvalidOperationException("window field was not found.");
        return field.GetValue(service) as Window;
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose() => Directory.Delete(Path, true);

        public string WriteBytes(string fileName, byte[] bytes)
        {
            var filePath = System.IO.Path.Combine(Path, fileName);
            File.WriteAllBytes(filePath, bytes);
            return filePath;
        }
    }
}
