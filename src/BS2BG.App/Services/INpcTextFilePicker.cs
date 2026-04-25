namespace BS2BG.App.Services;

public interface INpcTextFilePicker
{
    Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken);
}
