using Avalonia.Controls;
using BS2BG.Core.Models;

namespace BS2BG.App.Services;

public sealed class WindowNoPresetNotificationService : INoPresetNotificationService
{
    private Window? owner;
    private Window? window;
    private ListBox? customTargetsList;
    private ListBox? npcsList;

    public void Attach(Window owner)
    {
        ArgumentNullException.ThrowIfNull(owner);

        this.owner = owner;
    }

    public void ShowTargetsWithoutPresets(IReadOnlyList<MorphTargetBase> targets)
    {
        ArgumentNullException.ThrowIfNull(targets);

        if (targets.Count == 0)
        {
            return;
        }

        EnsureWindow();
        customTargetsList!.ItemsSource = targets
            .OfType<CustomMorphTarget>()
            .Select(target => target.Name)
            .ToArray();
        npcsList!.ItemsSource = targets
            .OfType<Npc>()
            .Select(npc => npc.Name + " | " + npc.Mod + " | " + npc.EditorId + " | " + npc.FormId)
            .ToArray();

        if (!window!.IsVisible)
        {
            if (owner is null)
            {
                window.Show();
            }
            else
            {
                window.Show(owner);
            }
        }

        window.Activate();
    }

    private void EnsureWindow()
    {
        if (window is not null)
        {
            return;
        }

        customTargetsList = new ListBox();
        npcsList = new ListBox();
        window = new Window
        {
            Title = "Warning: Targets with no presets were found!",
            Width = 500,
            Height = 450,
            MinWidth = 500,
            MinHeight = 450,
            CanResize = true,
            Topmost = true,
            Content = new Grid
            {
                RowDefinitions = new RowDefinitions("Auto,*,*"),
                Margin = new Avalonia.Thickness(12),
                Children =
                {
                    new TextBlock
                    {
                        Text = "The following targets don't have assigned presets.",
                        TextWrapping = Avalonia.Media.TextWrapping.Wrap,
                        Margin = new Avalonia.Thickness(0, 0, 0, 8),
                    },
                    customTargetsList,
                    npcsList,
                },
            },
        };

        Grid.SetRow(customTargetsList, 1);
        Grid.SetRow(npcsList, 2);
    }
}
