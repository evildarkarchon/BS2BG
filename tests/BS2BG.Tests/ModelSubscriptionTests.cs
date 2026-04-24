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
    public void AddSetSliderKeepsSortedOrderWhenCollectionHasExternalSubscribers()
    {
        var preset = new SliderPreset("Alpha");
        var collectionNotifications = 0;
        preset.SetSliders.CollectionChanged += (_, _) => collectionNotifications++;

        preset.AddSetSlider(new SetSlider("P2") { ValueBig = 2 });
        FluentActions.Invoking(() => preset.AddSetSlider(new SetSlider("P10") { ValueBig = 10 }))
            .Should()
            .NotThrow();

        collectionNotifications.Should().BeGreaterThan(0);
        preset.SetSliders.Select(slider => slider.Name).Should().Equal(new List<string> { "P10", "P2" });
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
