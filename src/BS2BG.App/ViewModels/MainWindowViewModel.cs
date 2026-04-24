using ReactiveUI;

namespace BS2BG.App.ViewModels;

public sealed class MainWindowViewModel : ReactiveObject
{
    private readonly string title = AppShell.Title;

    public MainWindowViewModel()
        : this(new TemplatesViewModel(), new MorphsViewModel())
    {
    }

    public MainWindowViewModel(TemplatesViewModel templates, MorphsViewModel morphs)
    {
        Templates = templates ?? throw new ArgumentNullException(nameof(templates));
        Morphs = morphs ?? throw new ArgumentNullException(nameof(morphs));
    }

    public string Title => title;

    public TemplatesViewModel Templates { get; }

    public MorphsViewModel Morphs { get; }
}
