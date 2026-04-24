using System.ComponentModel;
using System.Globalization;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using ReactiveUI;

namespace BS2BG.App.ViewModels;

public sealed class SetSliderInspectorRowViewModel : ReactiveObject, IDisposable
{
    private readonly Func<TemplateProfile> getProfile;
    private readonly SetSlider slider;
    private readonly TemplateGenerationService templateGenerationService;
    private readonly Action valueChanged;

    public SetSliderInspectorRowViewModel(
        SetSlider slider,
        TemplateGenerationService templateGenerationService,
        Func<TemplateProfile> getProfile,
        Action valueChanged)
    {
        this.slider = slider ?? throw new ArgumentNullException(nameof(slider));
        this.templateGenerationService = templateGenerationService
                                         ?? throw new ArgumentNullException(nameof(templateGenerationService));
        this.getProfile = getProfile ?? throw new ArgumentNullException(nameof(getProfile));
        this.valueChanged = valueChanged ?? throw new ArgumentNullException(nameof(valueChanged));

        slider.PropertyChanged += OnSliderPropertyChanged;
    }

    public string Name => slider.Name;

    public bool Enabled
    {
        get => slider.Enabled;
        set
        {
            if (slider.Enabled == value) return;

            slider.Enabled = value;
            RaiseAllChanged();
            valueChanged();
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

    public void SetBothPercents(int value)
    {
        var clamped = ClampPercent(value);
        if (slider.PercentMin == clamped && slider.PercentMax == clamped) return;

        slider.PercentMin = clamped;
        slider.PercentMax = clamped;
        RaiseAllChanged();
        valueChanged();
    }

    public void SetMinPercent(int value) => SetPercentMin(value);

    public void SetMaxPercent(int value) => SetPercentMax(value);

    public void RefreshPreview() => this.RaisePropertyChanged(nameof(PreviewText));

    private void SetPercentMin(double value)
    {
        var clamped = Math.Min(ClampPercent(value), slider.PercentMax);
        if (slider.PercentMin == clamped) return;

        slider.PercentMin = clamped;
        RaiseAllChanged();
        valueChanged();
    }

    private void SetPercentMax(double value)
    {
        var clamped = Math.Max(ClampPercent(value), slider.PercentMin);
        if (slider.PercentMax == clamped) return;

        slider.PercentMax = clamped;
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
}
