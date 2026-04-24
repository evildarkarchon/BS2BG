using BS2BG.App.ViewModels;
using ReactiveUI.Avalonia;

namespace BS2BG.App.Views;

public partial class MainWindow : ReactiveWindow<MainWindowViewModel>
{
    public MainWindow()
        : this(new MainWindowViewModel())
    {
    }

    public MainWindow(MainWindowViewModel viewModel)
    {
        InitializeComponent();

        ViewModel = viewModel;
        DataContext = viewModel;
        Title = AppShell.Title;
        Width = AppShell.StartupWidth;
        Height = AppShell.StartupHeight;
        MinWidth = AppShell.MinWidth;
        MinHeight = AppShell.MinHeight;
    }
}
