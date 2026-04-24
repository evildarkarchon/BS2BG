using System.Collections.ObjectModel;
using System.Collections.Specialized;

namespace BS2BG.Core.Models;

public sealed class ProjectModel : ProjectModelNode
{
    private bool isDirty;

    public ProjectModel()
    {
        AttachCollection(SliderPresets);
        AttachCollection(CustomMorphTargets);
        AttachCollection(MorphedNpcs);
    }

    public event EventHandler? DirtyStateChanged;

    public ObservableCollection<SliderPreset> SliderPresets { get; } = new();

    public ObservableCollection<CustomMorphTarget> CustomMorphTargets { get; } = new();

    public ObservableCollection<Npc> MorphedNpcs { get; } = new();

    public bool IsDirty => isDirty;

    public SliderPreset? FindSliderPreset(string name)
    {
        return SliderPresets.FirstOrDefault(sliderPreset => string.Equals(
            sliderPreset.Name,
            name,
            StringComparison.OrdinalIgnoreCase));
    }

    public void SortPresets()
    {
        SortCollection(SliderPresets);
    }

    public void SortCustomMorphTargets()
    {
        SortCollection(CustomMorphTargets);
    }

    public int RemoveStalePresetReferences()
    {
        var removed = 0;

        foreach (var target in CustomMorphTargets)
        {
            removed += target.RemoveMissingPresetReferences(SliderPresets);
        }

        foreach (var npc in MorphedNpcs)
        {
            removed += npc.RemoveMissingPresetReferences(SliderPresets);
        }

        return removed;
    }

    public bool RemoveSliderPreset(string name)
    {
        var preset = FindSliderPreset(name);
        if (preset is null)
        {
            return false;
        }

        var removed = SliderPresets.Remove(preset);
        if (!removed)
        {
            return false;
        }

        foreach (var target in CustomMorphTargets)
        {
            target.RemoveSliderPreset(preset.Name);
        }

        foreach (var npc in MorphedNpcs)
        {
            npc.RemoveSliderPreset(preset.Name);
        }

        return true;
    }

    public void MarkDirty()
    {
        if (isDirty)
        {
            return;
        }

        isDirty = true;
        NotifyChanged(nameof(IsDirty));
        DirtyStateChanged?.Invoke(this, EventArgs.Empty);
    }

    public void MarkClean()
    {
        if (!isDirty)
        {
            return;
        }

        isDirty = false;
        NotifyChanged(nameof(IsDirty));
        DirtyStateChanged?.Invoke(this, EventArgs.Empty);
    }

    private void AttachCollection<T>(ObservableCollection<T> collection)
        where T : ProjectModelNode
    {
        collection.CollectionChanged += (_, args) =>
        {
            UpdateChildSubscriptions(args);
            MarkDirty();
        };
    }

    private void UpdateChildSubscriptions(NotifyCollectionChangedEventArgs args)
    {
        if (args.OldItems is not null)
        {
            foreach (ProjectModelNode item in args.OldItems)
            {
                item.Changed -= OnChildChanged;
            }
        }

        if (args.NewItems is not null)
        {
            foreach (ProjectModelNode item in args.NewItems)
            {
                item.Changed += OnChildChanged;
            }
        }
    }

    private void OnChildChanged(object? sender, EventArgs args)
    {
        MarkDirty();
    }

    private static void SortCollection<T>(ObservableCollection<T> collection)
        where T : ProjectModelNode
    {
        for (var sortedIndex = 0; sortedIndex < collection.Count; sortedIndex++)
        {
            var item = collection
                .Skip(sortedIndex)
                .OrderBy(value => GetName(value), StringComparer.OrdinalIgnoreCase)
                .First();
            var currentIndex = collection.IndexOf(item);
            if (currentIndex != sortedIndex)
            {
                collection.Move(currentIndex, sortedIndex);
            }
        }
    }

    private static string GetName(ProjectModelNode value)
    {
        return value switch
        {
            SliderPreset preset => preset.Name,
            MorphTargetBase target => target.Name,
            _ => string.Empty,
        };
    }
}
