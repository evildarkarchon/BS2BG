using BS2BG.App.Services;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class NpcImageLookupServiceTests
{
    [Fact]
    public void FindsNpcSpecificImageBeforeNameOnlyFallback()
    {
        using var directory = new TemporaryDirectory();
        directory.WriteImage("images", "Lydia.png");
        var specific = directory.WriteImage("images", "Lydia (HousecarlWhiterun).png");
        var service = new NpcImageLookupService(directory.Path);
        var npc = new Npc("Lydia") { EditorId = "HousecarlWhiterun" };

        var actual = service.FindImagePath(npc);

        actual.Should().Be(specific);
    }

    [Fact]
    public void FallsBackToNameOnlyImageAndSupportsDocumentedExtensionOrder()
    {
        using var directory = new TemporaryDirectory();
        var jpg = directory.WriteImage("images", "Serana.jpg");
        directory.WriteImage("images", "Serana.jpeg");
        var service = new NpcImageLookupService(directory.Path);

        var actual = service.FindImagePath(new Npc("Serana") { EditorId = "DLC1Serana" });

        actual.Should().Be(jpg);
    }

    [Fact]
    public void ReturnsNullWhenNoImageExists()
    {
        using var directory = new TemporaryDirectory();
        var service = new NpcImageLookupService(directory.Path);

        service.FindImagePath(new Npc("Missing") { EditorId = "Nope" }).Should().BeNull();
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

        public string WriteImage(string relativeDirectory, string fileName)
        {
            var directory = System.IO.Path.Combine(Path, relativeDirectory);
            Directory.CreateDirectory(directory);
            var filePath = System.IO.Path.Combine(directory, fileName);
            File.WriteAllBytes(filePath, new byte[] { 0x42 });
            return filePath;
        }
    }
}
