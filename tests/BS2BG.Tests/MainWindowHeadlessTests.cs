using Avalonia.Automation;
using Avalonia.Controls;
using Avalonia.Headless.XUnit;
using Avalonia.LogicalTree;
using BS2BG.App;
using BS2BG.App.Views;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace BS2BG.Tests;

public sealed class MainWindowHeadlessTests
{
    [AvaloniaFact]
    public void ProfilesWorkspaceTabAndRequiredControlsArePresent()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();
        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        var tabHeaders = window.GetLogicalDescendants()
            .OfType<TabItem>()
            .Select(tab => tab.Header?.ToString())
            .ToArray();

        tabHeaders.Should().Contain("Profiles");
        window.FindControl<Button>("ImportProfileButton")?.Content.Should().Be("Import Profile");
        AutomationProperties.GetName(window.FindControl<Button>("ImportProfileButton")!)
            .Should().Be("Import Profile");
        window.FindControl<Button>("ManageProfilesButton")?.Command.Should()
            .BeSameAs(window.ViewModel?.Templates.ManageProfilesCommand);
    }

    [AvaloniaFact]
    public void ProfilesWorkspaceShowsMissingAndRejectedGroupsEvenWhenEmpty()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();
        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        var copy = string.Join("\n", window.GetLogicalDescendants()
            .OfType<TextBlock>()
            .Select(block => block.Text)
            .Where(text => !string.IsNullOrWhiteSpace(text)));

        copy.Should().Contain("Missing references");
        copy.Should().Contain("Rejected profile files");
        window.FindControl<ItemsControl>("MissingProfileReferencesList").Should().NotBeNull();
        window.FindControl<ItemsControl>("RejectedProfileFilesList").Should().NotBeNull();
    }

    [AvaloniaFact]
    public void ProfilesWorkspaceSourceRowsExposeSelectableLists()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();
        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        window.FindControl<ListBox>("BundledProfilesList").Should().NotBeNull();
        window.FindControl<ListBox>("CustomProfilesList").Should().NotBeNull();
        window.FindControl<ListBox>("EmbeddedProjectProfilesList").Should().NotBeNull();
        window.FindControl<ListBox>("MissingProfileReferencesList").Should().NotBeNull();
    }

    [AvaloniaFact]
    public void ProfilesWorkspaceEditorExposesTableAuthoringButtons()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();
        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        var buttonAutomationNames = window.GetLogicalDescendants()
            .OfType<Button>()
            .Select(AutomationProperties.GetName)
            .Where(name => !string.IsNullOrWhiteSpace(name))
            .ToArray();

        buttonAutomationNames.Should().Contain("Add default slider");
        buttonAutomationNames.Should().Contain("Remove default slider");
        buttonAutomationNames.Should().Contain("Add multiplier slider");
        buttonAutomationNames.Should().Contain("Remove multiplier slider");
        buttonAutomationNames.Should().Contain("Add inverted slider");
        buttonAutomationNames.Should().Contain("Remove inverted slider");
    }
}
