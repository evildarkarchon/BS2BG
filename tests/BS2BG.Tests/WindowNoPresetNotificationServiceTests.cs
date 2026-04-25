using System.Reflection;
using Avalonia.Controls;
using Avalonia.Headless.XUnit;
using BS2BG.App.Services;
using BS2BG.Core.Models;

namespace BS2BG.Tests;

public sealed class WindowNoPresetNotificationServiceTests
{
    [AvaloniaFact]
    public void ShowTargetsWithoutPresetsRecreatesWindowAfterClose()
    {
        var service = new WindowNoPresetNotificationService();

        service.ShowTargetsWithoutPresets(new MorphTargetBase[] { new CustomMorphTarget("CBBE") });

        var firstWindow = GetWindow(service);
        firstWindow.Should().NotBeNull();
        firstWindow!.IsVisible.Should().BeTrue();

        firstWindow.Close();
        GetWindow(service).Should().BeNull();

        service.ShowTargetsWithoutPresets(new MorphTargetBase[] { new CustomMorphTarget("3BA") });

        var secondWindow = GetWindow(service);
        secondWindow.Should().NotBeNull();
        secondWindow.Should().NotBeSameAs(firstWindow);
        secondWindow!.IsVisible.Should().BeTrue();
        secondWindow.Close();
    }

    private static Window? GetWindow(WindowNoPresetNotificationService service)
    {
        var field = typeof(WindowNoPresetNotificationService).GetField(
                        "window",
                        BindingFlags.Instance | BindingFlags.NonPublic)
                    ?? throw new InvalidOperationException("window field was not found.");
        return field.GetValue(service) as Window;
    }
}
