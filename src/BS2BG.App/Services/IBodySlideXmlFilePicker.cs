namespace BS2BG.App.Services;

public interface IBodySlideXmlFilePicker
{
    Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken);
}
