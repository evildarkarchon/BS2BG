using BS2BG.Core.Models;

namespace BS2BG.App.Services;

public interface INpcImageLookupService
{
    string? FindImagePath(Npc npc);
}
