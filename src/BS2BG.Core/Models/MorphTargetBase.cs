using System.Collections.ObjectModel;
using System.Collections.Specialized;

namespace BS2BG.Core.Models;

public abstract class MorphTargetBase : ProjectModelNode
{
    private string name;
    private bool sortingPresets;

    protected MorphTargetBase(string name)
    {
        this.name = name ?? throw new ArgumentNullException(nameof(name));
        SliderPresets.CollectionChanged += OnSliderPresetsChanged;
    }

    public string Name
    {
        get => name;
        set => SetProperty(ref name, value ?? throw new ArgumentNullException(nameof(value)));
    }

    public ObservableCollection<SliderPreset> SliderPresets { get; } = new();

    public bool HasPresets => SliderPresets.Count > 0;

    public void AddSliderPreset(SliderPreset sliderPreset)
    {
        if (sliderPreset is null)
        {
            throw new ArgumentNullException(nameof(sliderPreset));
        }

        if (SliderPresets.Any(existing => string.Equals(
                existing.Name,
                sliderPreset.Name,
                StringComparison.OrdinalIgnoreCase)))
        {
            return;
        }

        SliderPresets.Add(sliderPreset);
        SortSliderPresets();
    }

    public bool RemoveSliderPreset(string name)
    {
        var match = SliderPresets.FirstOrDefault(sliderPreset => string.Equals(
            sliderPreset.Name,
            name,
            StringComparison.OrdinalIgnoreCase));

        return match is not null && SliderPresets.Remove(match);
    }

    public void ClearSliderPresets()
    {
        SliderPresets.Clear();
    }

    public int RemoveMissingPresetReferences(IEnumerable<SliderPreset> availablePresets)
    {
        if (availablePresets is null)
        {
            throw new ArgumentNullException(nameof(availablePresets));
        }

        var available = new HashSet<SliderPreset>(availablePresets);
        var removed = 0;

        for (var index = SliderPresets.Count - 1; index >= 0; index--)
        {
            if (!available.Contains(SliderPresets[index]))
            {
                SliderPresets.RemoveAt(index);
                removed++;
            }
        }

        return removed;
    }

    public virtual string ToMorphLine()
    {
        return Name + "=" + string.Join("|", SliderPresets.Select(sliderPreset => sliderPreset.Name));
    }

    private void OnSliderPresetsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        if (!sortingPresets)
        {
            SortSliderPresets();
        }

        NotifyChanged(nameof(SliderPresets));
    }

    private void SortSliderPresets()
    {
        sortingPresets = true;
        try
        {
            for (var sortedIndex = 0; sortedIndex < SliderPresets.Count; sortedIndex++)
            {
                var item = SliderPresets
                    .Skip(sortedIndex)
                    .OrderBy(sliderPreset => sliderPreset.Name, StringComparer.OrdinalIgnoreCase)
                    .First();
                var currentIndex = SliderPresets.IndexOf(item);
                if (currentIndex != sortedIndex)
                {
                    SliderPresets.Move(currentIndex, sortedIndex);
                }
            }
        }
        finally
        {
            sortingPresets = false;
        }
    }
}
