namespace BS2BG.Core.Models;

public sealed class SetSlider(string name) : ProjectModelNode
{
    private bool enabled = true;
    private string name = name ?? throw new ArgumentNullException(nameof(name));
    private int percentMax = 100;
    private int percentMin = 100;
    private int? valueBig;
    private int? valueSmall;

    public string Name
    {
        get => name;
        set => SetProperty(ref name, value ?? throw new ArgumentNullException(nameof(value)));
    }

    public bool Enabled
    {
        get => enabled;
        set => SetProperty(ref enabled, value);
    }

    public int? ValueSmall
    {
        get => valueSmall;
        set => SetProperty(ref valueSmall, value);
    }

    public int? ValueBig
    {
        get => valueBig;
        set => SetProperty(ref valueBig, value);
    }

    public int PercentMin
    {
        get => percentMin;
        set => SetProperty(ref percentMin, value);
    }

    public int PercentMax
    {
        get => percentMax;
        set => SetProperty(ref percentMax, value);
    }

    public bool IsMissingDefault => ValueSmall is null && ValueBig is null;
}
