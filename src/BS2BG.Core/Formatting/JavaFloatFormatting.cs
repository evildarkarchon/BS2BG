using System.Globalization;

namespace BS2BG.Core.Formatting;

public static class JavaFloatFormatting
{
    public static float RoundHalfUpToTwoDecimals(float value)
    {
        var decimalText = value.ToString("R", CultureInfo.InvariantCulture);
        var decimalValue = decimal.Parse(decimalText, NumberStyles.Float, CultureInfo.InvariantCulture);
        var rounded = decimal.Round(decimalValue, 2, MidpointRounding.AwayFromZero);

        return (float)rounded;
    }

    public static string FormatForText(float value)
    {
        if (value == 0f)
        {
            return "0.0";
        }

        var text = value.ToString("0.##", CultureInfo.InvariantCulture);
        return text.Contains('.') ? text : text + ".0";
    }

    public static string FormatForMinimalJsonNumber(float value)
    {
        if (value == 0f)
        {
            return "0";
        }

        return value.ToString("0.##", CultureInfo.InvariantCulture);
    }
}
