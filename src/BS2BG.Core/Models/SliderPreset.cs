using System.Collections.ObjectModel;
using System.Collections.Specialized;

namespace BS2BG.Core.Models;

public sealed class SliderPreset : ProjectModelNode
{
    private string name;
    private string profileName;
    private bool sortingSetSliders;
    private bool sortingMissingDefaults;

    public SliderPreset(string name, string? profileName = null)
    {
        this.name = NormalizePresetName(name);
        this.profileName = ProjectProfileMapping.Resolve(profileName, isUunp: false);

        SetSliders.CollectionChanged += OnSetSlidersChanged;
        MissingDefaultSetSliders.CollectionChanged += OnMissingDefaultSetSlidersChanged;
    }

    public string Name
    {
        get => name;
        set => SetProperty(ref name, NormalizePresetName(value));
    }

    public string ProfileName
    {
        get => profileName;
        set => SetProperty(ref profileName, ProjectProfileMapping.Resolve(value, isUunp: false));
    }

    public bool IsUunp
    {
        get => ProjectProfileMapping.ToLegacyIsUunp(ProfileName);
        set => ProfileName = ProjectProfileMapping.FromLegacyIsUunp(value);
    }

    public ObservableCollection<SetSlider> SetSliders { get; } = new();

    public ObservableCollection<SetSlider> MissingDefaultSetSliders { get; } = new();

    public void AddSetSlider(SetSlider slider)
    {
        if (slider is null)
        {
            throw new ArgumentNullException(nameof(slider));
        }

        if (slider.IsMissingDefault)
        {
            MissingDefaultSetSliders.Add(slider);
            SortMissingDefaultSetSliders();
        }
        else
        {
            SetSliders.Add(slider);
            SortSetSliders();
        }
    }

    public IEnumerable<SetSlider> GetAllSetSlidersForSave()
    {
        return SetSliders
            .Concat(MissingDefaultSetSliders)
            .OrderBy(slider => slider.Name, StringComparer.OrdinalIgnoreCase);
    }

    public void RefreshMissingDefaultSetSliders(IEnumerable<string> defaultSliderNames)
    {
        if (defaultSliderNames is null)
        {
            throw new ArgumentNullException(nameof(defaultSliderNames));
        }

        var existingNames = new HashSet<string>(
            SetSliders.Select(slider => slider.Name)
                .Concat(MissingDefaultSetSliders.Select(slider => slider.Name)),
            StringComparer.OrdinalIgnoreCase);

        foreach (var sliderName in defaultSliderNames)
        {
            if (!existingNames.Add(sliderName))
            {
                continue;
            }

            MissingDefaultSetSliders.Add(new SetSlider(sliderName));
        }

        SortMissingDefaultSetSliders();
    }

    private void OnSetSlidersChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        UpdateChildSubscriptions(args);

        if (!sortingSetSliders)
        {
            SortSetSliders();
        }

        NotifyChanged(nameof(SetSliders));
    }

    private void OnMissingDefaultSetSlidersChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        UpdateChildSubscriptions(args);

        if (!sortingMissingDefaults)
        {
            SortMissingDefaultSetSliders();
        }

        NotifyChanged(nameof(MissingDefaultSetSliders));
    }

    private void UpdateChildSubscriptions(NotifyCollectionChangedEventArgs args)
    {
        if (args.OldItems is not null)
        {
            foreach (SetSlider slider in args.OldItems)
            {
                slider.Changed -= OnChildChanged;
            }
        }

        if (args.NewItems is not null)
        {
            foreach (SetSlider slider in args.NewItems)
            {
                slider.Changed += OnChildChanged;
            }
        }
    }

    private void OnChildChanged(object? sender, EventArgs args)
    {
        NotifyChanged(nameof(SetSliders));
    }

    private void SortSetSliders()
    {
        SortCollection(SetSliders, ref sortingSetSliders);
    }

    private void SortMissingDefaultSetSliders()
    {
        SortCollection(MissingDefaultSetSliders, ref sortingMissingDefaults);
    }

    private static void SortCollection(ObservableCollection<SetSlider> collection, ref bool sorting)
    {
        sorting = true;
        try
        {
            for (var sortedIndex = 0; sortedIndex < collection.Count; sortedIndex++)
            {
                var item = collection
                    .Skip(sortedIndex)
                    .OrderBy(slider => slider.Name, StringComparer.OrdinalIgnoreCase)
                    .First();
                var currentIndex = collection.IndexOf(item);
                if (currentIndex != sortedIndex)
                {
                    collection.Move(currentIndex, sortedIndex);
                }
            }
        }
        finally
        {
            sorting = false;
        }
    }

    private static string NormalizePresetName(string value)
    {
        return (value ?? throw new ArgumentNullException(nameof(value)))
            .Replace('.', ' ');
    }
}
