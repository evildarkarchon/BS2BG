using BS2BG.Core.Models;

namespace BS2BG.App.Services;

public interface IImageViewService
{
    void ShowImage(Npc npc, string? imagePath);
}
