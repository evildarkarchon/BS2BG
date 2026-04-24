using System.Collections.ObjectModel;
using System.Collections.Specialized;

namespace BS2BG.Core.Models;

public sealed class SliderPreset : ProjectModelNode
{
    private string name;
    private string profileName;
    private bool sortingSetSliders;
    private bool sortingMissingDefaults;
    private readonly Dictionary<SetSlider, int> setSliderSubscriptions = new();
    private readonly Dictionary<SetSlider, int> missingDefaultSetSliderSubscriptions = new();

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

        var defaultNames = defaultSliderNames.ToArray();
        var activeDefaultNames = new HashSet<string>(
            defaultNames,
            StringComparer.OrdinalIgnoreCase);
        var setSliderNames = new HashSet<string>(
            SetSliders.Select(slider => slider.Name),
            StringComparer.OrdinalIgnoreCase);

        for (var index = MissingDefaultSetSliders.Count - 1; index >= 0; index--)
        {
            var slider = MissingDefaultSetSliders[index];
            if (!activeDefaultNames.Contains(slider.Name) || setSliderNames.Contains(slider.Name))
            {
                MissingDefaultSetSliders.RemoveAt(index);
            }
        }

        var existingNames = new HashSet<string>(
            SetSliders.Select(slider => slider.Name)
                .Concat(MissingDefaultSetSliders.Select(slider => slider.Name)),
            StringComparer.OrdinalIgnoreCase);

        foreach (var sliderName in defaultNames)
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
        UpdateChildSubscriptions(args, setSliderSubscriptions);

        if (!sortingSetSliders)
        {
            SortSetSliders();
        }

        NotifyChanged(nameof(SetSliders));
    }

    private void OnMissingDefaultSetSlidersChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        UpdateChildSubscriptions(args, missingDefaultSetSliderSubscriptions);

        if (!sortingMissingDefaults)
        {
            SortMissingDefaultSetSliders();
        }

        NotifyChanged(nameof(MissingDefaultSetSliders));
    }

    private void UpdateChildSubscriptions(
        NotifyCollectionChangedEventArgs args,
        Dictionary<SetSlider, int> childSubscriptions)
    {
        if (args.Action == NotifyCollectionChangedAction.Reset)
        {
            DetachAllChildren(childSubscriptions);
            return;
        }

        if (args.OldItems is not null)
        {
            foreach (SetSlider slider in args.OldItems)
            {
                DetachChild(slider, childSubscriptions);
            }
        }

        if (args.NewItems is not null)
        {
            foreach (SetSlider slider in args.NewItems)
            {
                AttachChild(slider, childSubscriptions);
            }
        }
    }

    private void AttachChild(SetSlider slider, Dictionary<SetSlider, int> childSubscriptions)
    {
        slider.Changed += OnChildChanged;
        childSubscriptions.TryGetValue(slider, out var count);
        childSubscriptions[slider] = count + 1;
    }

    private void DetachChild(SetSlider slider, Dictionary<SetSlider, int> childSubscriptions)
    {
        slider.Changed -= OnChildChanged;

        if (!childSubscriptions.TryGetValue(slider, out var count))
        {
            return;
        }

        if (count == 1)
        {
            childSubscriptions.Remove(slider);
            return;
        }

        childSubscriptions[slider] = count - 1;
    }

    private void DetachAllChildren(Dictionary<SetSlider, int> childSubscriptions)
    {
        foreach (var subscription in childSubscriptions)
        {
            for (var index = 0; index < subscription.Value; index++)
            {
                subscription.Key.Changed -= OnChildChanged;
            }
        }

        childSubscriptions.Clear();
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
