using System.ComponentModel;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class ModelSubscriptionTests
{
    [Fact]
    public void ProjectModelDoesNotTrackSliderPresetAfterPresetCollectionIsCleared()
    {
        var project = new ProjectModel();
        var preset = new SliderPreset("Alpha");

        project.SliderPresets.Add(preset);
        project.MarkClean();

        project.SliderPresets.Clear();
        project.MarkClean();

        preset.Name = "Beta";

        project.IsDirty.Should().BeFalse();
    }

    [Fact]
    public void SliderPresetDoesNotTrackSetSliderAfterSetSlidersCollectionIsCleared()
    {
        var preset = new SliderPreset("Alpha");
        var slider = new SetSlider("Scale") { ValueBig = 50 };
        var changeCount = 0;
        preset.PropertyChanged += CountPresetChanges;

        preset.AddSetSlider(slider);
        preset.SetSliders.Clear();
        changeCount = 0;

        slider.ValueBig = 75;

        changeCount.Should().Be(0);

        void CountPresetChanges(object? sender, PropertyChangedEventArgs args)
        {
            changeCount++;
        }
    }

    [Fact]
    public void SliderPresetDoesNotTrackSetSliderAfterMissingDefaultCollectionIsCleared()
    {
        var preset = new SliderPreset("Alpha");
        var slider = new SetSlider("Scale");
        var changeCount = 0;
        preset.PropertyChanged += CountPresetChanges;

        preset.AddSetSlider(slider);
        preset.MissingDefaultSetSliders.Clear();
        changeCount = 0;

        slider.ValueBig = 75;

        changeCount.Should().Be(0);

        void CountPresetChanges(object? sender, PropertyChangedEventArgs args)
        {
            changeCount++;
        }
    }
}
