using BS2BG.Core.Models;

namespace BS2BG.App.Services;

public interface INoPresetNotificationService
{
    void ShowTargetsWithoutPresets(IReadOnlyList<MorphTargetBase> targets);
}
