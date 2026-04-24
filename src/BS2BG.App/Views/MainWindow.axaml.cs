using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Models;
using Avalonia.Controls;
using Avalonia.Input;
using ReactiveUI.Avalonia;

namespace BS2BG.App.Views;

public partial class MainWindow : ReactiveWindow<MainWindowViewModel>
{
    public MainWindow()
        : this(new MainWindowViewModel())
    {
    }

    public MainWindow(MainWindowViewModel viewModel)
        : this(viewModel, null, null, null)
    {
    }

    public MainWindow(
        MainWindowViewModel viewModel,
        WindowBodySlideXmlFilePicker? filePicker,
        WindowNpcTextFilePicker? npcTextFilePicker,
        WindowClipboardService? clipboardService,
        WindowImageViewService? imageViewService = null,
        WindowNoPresetNotificationService? noPresetNotificationService = null,
        WindowFileDialogService? fileDialogService = null,
        WindowAppDialogService? dialogService = null)
    {
        InitializeComponent();

        ViewModel = viewModel;
        DataContext = viewModel;
        Title = viewModel.Title;
        viewModel.PropertyChanged += (_, args) =>
        {
            if (args.PropertyName == nameof(MainWindowViewModel.Title))
            {
                Title = viewModel.Title;
            }
            else if (args.PropertyName == nameof(MainWindowViewModel.ShouldFocusGlobalSearch)
                && viewModel.ShouldFocusGlobalSearch)
            {
                this.FindControl<TextBox>("GlobalSearchBox")?.Focus();
                viewModel.AcknowledgeGlobalSearchFocus();
            }
        };

        Width = AppShell.StartupWidth;
        Height = AppShell.StartupHeight;
        MinWidth = AppShell.MinWidth;
        MinHeight = AppShell.MinHeight;
        filePicker?.Attach(this);
        npcTextFilePicker?.Attach(this);
        clipboardService?.Attach(this);
        imageViewService?.Attach(this);
        noPresetNotificationService?.Attach(this);
        fileDialogService?.Attach(this);
        dialogService?.Attach(this);
    }

    private void OnWorkspaceSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        if (ViewModel is null || sender is not TabControl tabControl)
        {
            return;
        }

        ViewModel.ActiveWorkspace = tabControl.SelectedIndex == 0
            ? AppWorkspace.Templates
            : AppWorkspace.Morphs;
    }

    private void OnNpcSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        if (ViewModel is null || sender is not ListBox listBox)
        {
            return;
        }

        ViewModel.Morphs.SelectedNpcs.Clear();
        foreach (var item in listBox.SelectedItems?.OfType<Npc>() ?? Enumerable.Empty<Npc>())
        {
            ViewModel.Morphs.SelectedNpcs.Add(item);
        }
    }

    private void OnCommandPaletteSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        if (ViewModel is null || sender is not ListBox listBox)
        {
            return;
        }

        if (listBox.SelectedItem is CommandDescriptor descriptor)
        {
            ViewModel.RunCommandPaletteItemCommand.Execute(descriptor);
            listBox.SelectedItem = null;
        }
    }

    private void OnDragOver(object? sender, DragEventArgs args)
    {
        args.DragEffects = args.DataTransfer.TryGetFiles() is { Length: > 0 }
            ? DragDropEffects.Copy
            : DragDropEffects.None;
        args.Handled = true;
    }

    private async void OnDrop(object? sender, DragEventArgs args)
    {
        if (ViewModel is null)
        {
            return;
        }

        var paths = args.DataTransfer.TryGetFiles()?
            .Where(file => file.Path.IsFile)
            .Select(file => file.Path.LocalPath)
            .ToArray() ?? Array.Empty<string>();
        await ViewModel.HandleDroppedFilesAsync(paths);
        args.Handled = true;
    }
}
