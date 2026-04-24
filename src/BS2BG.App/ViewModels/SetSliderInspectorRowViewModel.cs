using System.ComponentModel;
using System.Globalization;
using BS2BG.App.Services;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using ReactiveUI;

namespace BS2BG.App.ViewModels;

public sealed class SetSliderInspectorRowViewModel : ReactiveObject, IDisposable
{
    private readonly Func<TemplateProfile> getProfile;
    private readonly SetSlider slider;
    private readonly TemplateGenerationService templateGenerationService;
    private readonly UndoRedoService undoRedo;
    private readonly Action valueChanged;

    public SetSliderInspectorRowViewModel(
        SetSlider slider,
        TemplateGenerationService templateGenerationService,
        Func<TemplateProfile> getProfile,
        Action valueChanged,
        UndoRedoService undoRedo)
    {
        this.slider = slider ?? throw new ArgumentNullException(nameof(slider));
        this.templateGenerationService = templateGenerationService
                                         ?? throw new ArgumentNullException(nameof(templateGenerationService));
        this.getProfile = getProfile ?? throw new ArgumentNullException(nameof(getProfile));
        this.valueChanged = valueChanged ?? throw new ArgumentNullException(nameof(valueChanged));
        this.undoRedo = undoRedo ?? throw new ArgumentNullException(nameof(undoRedo));

        slider.PropertyChanged += OnSliderPropertyChanged;
    }

    public string Name => slider.Name;

    public bool Enabled
    {
        get => slider.Enabled;
        set
        {
            if (slider.Enabled == value) return;

            ApplyEdit("Edit slider", () => slider.Enabled = value, true);
        }
    }

    public double PercentMin
    {
        get => slider.PercentMin;
        set => SetPercentMin(value);
    }

    public double PercentMax
    {
        get => slider.PercentMax;
        set => SetPercentMax(value);
    }

    public string PercentMinText => slider.PercentMin.ToString(CultureInfo.InvariantCulture) + "%";

    public string PercentMaxText => slider.PercentMax.ToString(CultureInfo.InvariantCulture) + "%";

    public string PreviewText => templateGenerationService.PreviewSetSlider(slider, getProfile());

    public void Dispose() => slider.PropertyChanged -= OnSliderPropertyChanged;

    public void SetBothPercents(int value, bool recordUndo = true)
    {
        var clamped = ClampPercent(value);
        if (slider.PercentMin == clamped && slider.PercentMax == clamped) return;

        ApplyEdit(
            "Edit slider",
            () =>
            {
                slider.PercentMin = clamped;
                slider.PercentMax = clamped;
            },
            recordUndo);
    }

    public void SetMinPercent(int value, bool recordUndo = true) => SetPercentMin(value, recordUndo);

    public void SetMaxPercent(int value, bool recordUndo = true) => SetPercentMax(value, recordUndo);

    public void RefreshPreview() => this.RaisePropertyChanged(nameof(PreviewText));

    private void SetPercentMin(double value, bool recordUndo = true)
    {
        var clamped = Math.Min(ClampPercent(value), slider.PercentMax);
        if (slider.PercentMin == clamped) return;

        ApplyEdit("Edit slider", () => slider.PercentMin = clamped, recordUndo);
    }

    private void SetPercentMax(double value, bool recordUndo = true)
    {
        var clamped = Math.Max(ClampPercent(value), slider.PercentMin);
        if (slider.PercentMax == clamped) return;

        ApplyEdit("Edit slider", () => slider.PercentMax = clamped, recordUndo);
    }

    private void ApplyEdit(string name, Action edit, bool recordUndo)
    {
        var before = SetSliderSnapshot.Create(slider);
        edit();
        var after = SetSliderSnapshot.Create(slider);
        RaiseAllChanged();
        valueChanged();

        if (recordUndo)
            undoRedo.Record(
                name,
                () => Restore(before),
                () => Restore(after));
    }

    private void Restore(SetSliderSnapshot snapshot)
    {
        snapshot.Apply(slider);
        RaiseAllChanged();
        valueChanged();
    }

    private void OnSliderPropertyChanged(object? sender, PropertyChangedEventArgs args) => RaiseAllChanged();

    private void RaiseAllChanged()
    {
        this.RaisePropertyChanged(nameof(Name));
        this.RaisePropertyChanged(nameof(Enabled));
        this.RaisePropertyChanged(nameof(PercentMin));
        this.RaisePropertyChanged(nameof(PercentMax));
        this.RaisePropertyChanged(nameof(PercentMinText));
        this.RaisePropertyChanged(nameof(PercentMaxText));
        this.RaisePropertyChanged(nameof(PreviewText));
    }

    private static int ClampPercent(double value)
    {
        if (double.IsNaN(value)) return 0;

        return Math.Clamp((int)Math.Round(value, MidpointRounding.AwayFromZero), 0, 100);
    }

    private sealed class SetSliderSnapshot(
        bool enabled,
        int percentMin,
        int percentMax)
    {
        private bool Enabled { get; } = enabled;

        private int PercentMin { get; } = percentMin;

        private int PercentMax { get; } = percentMax;

        public static SetSliderSnapshot Create(SetSlider slider) =>
            new(slider.Enabled, slider.PercentMin, slider.PercentMax);

        public void Apply(SetSlider slider)
        {
            slider.Enabled = Enabled;
            slider.PercentMin = PercentMin;
            slider.PercentMax = PercentMax;
        }
    }
}
