using System.Collections.ObjectModel;
using System.Collections.Specialized;

namespace BS2BG.Core.Models;

public sealed class ProjectModel : ProjectModelNode
{
    public ProjectModel()
    {
        AttachCollection(SliderPresets);
        AttachCollection(CustomMorphTargets);
        AttachCollection(MorphedNpcs);
    }

    public ObservableCollection<SliderPreset> SliderPresets { get; } = new();

    public ObservableCollection<CustomMorphTarget> CustomMorphTargets { get; } = new();

    public ObservableCollection<Npc> MorphedNpcs { get; } = new();

    public bool IsDirty { get; private set; }

    public int ChangeVersion { get; private set; }

    public event EventHandler? DirtyStateChanged;

    private int suppressDirtyDepth;

    public IDisposable SuppressDirtyTracking()
    {
        suppressDirtyDepth++;
        return new SuppressionScope(this);
    }

    public SliderPreset? FindSliderPreset(string name)
    {
        return SliderPresets.FirstOrDefault(sliderPreset => string.Equals(
            sliderPreset.Name,
            name,
            StringComparison.OrdinalIgnoreCase));
    }

    public void SortPresets() => SortCollection(SliderPresets);

    public void SortCustomMorphTargets() => SortCollection(CustomMorphTargets);

    public void ReplaceWith(ProjectModel source)
    {
        if (source is null) throw new ArgumentNullException(nameof(source));

        SliderPresets.Clear();
        CustomMorphTargets.Clear();
        MorphedNpcs.Clear();

        var presetMap = new Dictionary<string, SliderPreset>(StringComparer.OrdinalIgnoreCase);
        foreach (var preset in source.SliderPresets)
        {
            var clone = ClonePreset(preset);
            SliderPresets.Add(clone);
            presetMap[clone.Name] = clone;
        }

        foreach (var target in source.CustomMorphTargets)
        {
            var clone = new CustomMorphTarget(target.Name);
            CopyPresetAssignments(target, clone, presetMap);
            CustomMorphTargets.Add(clone);
        }

        foreach (var npc in source.MorphedNpcs)
        {
            var clone = new Npc(npc.Name)
            {
                Mod = npc.Mod, EditorId = npc.EditorId, Race = npc.Race, FormId = npc.FormId
            };
            CopyPresetAssignments(npc, clone, presetMap);
            MorphedNpcs.Add(clone);
        }

        MarkClean();
    }

    public int RemoveStalePresetReferences()
    {
        var removed = 0;

        foreach (var target in CustomMorphTargets) removed += target.RemoveMissingPresetReferences(SliderPresets);

        foreach (var npc in MorphedNpcs) removed += npc.RemoveMissingPresetReferences(SliderPresets);

        return removed;
    }

    public bool RemoveSliderPreset(string name)
    {
        var preset = FindSliderPreset(name);
        if (preset is null) return false;

        var removed = SliderPresets.Remove(preset);
        if (!removed) return false;

        foreach (var target in CustomMorphTargets) target.RemoveSliderPreset(preset.Name);

        foreach (var npc in MorphedNpcs) npc.RemoveSliderPreset(preset.Name);

        return true;
    }

    public void MarkDirty()
    {
        if (IsDirty) return;

        IsDirty = true;
        NotifyChanged(nameof(IsDirty));
        DirtyStateChanged?.Invoke(this, EventArgs.Empty);
    }

    public void MarkClean()
    {
        if (!IsDirty) return;

        IsDirty = false;
        NotifyChanged(nameof(IsDirty));
        DirtyStateChanged?.Invoke(this, EventArgs.Empty);
    }

    private void AttachCollection<T>(ObservableCollection<T> collection)
        where T : ProjectModelNode
    {
        var childSubscriptions = new Dictionary<T, int>();

        collection.CollectionChanged += (_, args) =>
        {
            UpdateChildSubscriptions(args, childSubscriptions);
            OnAnyChange();
        };
    }

    private void OnAnyChange()
    {
        ChangeVersion = unchecked(ChangeVersion + 1);
        if (suppressDirtyDepth == 0) MarkDirty();
    }

    private void UpdateChildSubscriptions<T>(
        NotifyCollectionChangedEventArgs args,
        Dictionary<T, int> childSubscriptions)
        where T : ProjectModelNode
    {
        if (args.Action == NotifyCollectionChangedAction.Reset)
        {
            DetachAllChildren(childSubscriptions);
            return;
        }

        if (args.OldItems is not null)
            foreach (T item in args.OldItems)
                DetachChild(item, childSubscriptions);

        if (args.NewItems is not null)
            foreach (T item in args.NewItems)
                AttachChild(item, childSubscriptions);
    }

    private void AttachChild<T>(T item, Dictionary<T, int> childSubscriptions)
        where T : ProjectModelNode
    {
        item.Changed += OnChildChanged;
        childSubscriptions.TryGetValue(item, out var count);
        childSubscriptions[item] = count + 1;
    }

    private void DetachChild<T>(T item, Dictionary<T, int> childSubscriptions)
        where T : ProjectModelNode
    {
        item.Changed -= OnChildChanged;

        if (!childSubscriptions.TryGetValue(item, out var count)) return;

        if (count == 1)
        {
            childSubscriptions.Remove(item);
            return;
        }

        childSubscriptions[item] = count - 1;
    }

    private void DetachAllChildren<T>(Dictionary<T, int> childSubscriptions)
        where T : ProjectModelNode
    {
        foreach (var subscription in childSubscriptions)
            for (var index = 0; index < subscription.Value; index++)
                subscription.Key.Changed -= OnChildChanged;

        childSubscriptions.Clear();
    }

    private void OnChildChanged(object? sender, EventArgs args) => OnAnyChange();

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
            if (currentIndex != sortedIndex) collection.Move(currentIndex, sortedIndex);
        }
    }

    private static string GetName(ProjectModelNode value)
    {
        return value switch
        {
            SliderPreset preset => preset.Name,
            MorphTargetBase target => target.Name,
            _ => string.Empty
        };
    }

    private static SliderPreset ClonePreset(SliderPreset source)
    {
        var clone = new SliderPreset(source.Name, source.ProfileName);
        foreach (var slider in source.SetSliders.Concat(source.MissingDefaultSetSliders))
            clone.AddSetSlider(new SetSlider(slider.Name)
            {
                Enabled = slider.Enabled,
                ValueSmall = slider.ValueSmall,
                ValueBig = slider.ValueBig,
                PercentMin = slider.PercentMin,
                PercentMax = slider.PercentMax
            });

        return clone;
    }

    private static void CopyPresetAssignments(
        MorphTargetBase source,
        MorphTargetBase target,
        Dictionary<string, SliderPreset> presetMap)
    {
        foreach (var preset in source.SliderPresets)
            if (presetMap.TryGetValue(preset.Name, out var resolvedPreset))
                target.AddSliderPreset(resolvedPreset);
    }

    private sealed class SuppressionScope(ProjectModel owner) : IDisposable
    {
        private bool disposed;

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            owner.suppressDirtyDepth--;
        }
    }
}
