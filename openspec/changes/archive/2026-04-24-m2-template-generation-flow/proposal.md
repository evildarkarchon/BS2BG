## Why

The first-time modder workflow depends on importing BodySlide XML, reviewing presets, and generating `templates.ini` text. M2 makes Flow A usable end-to-end after the core math and project file layers are in place.

## What Changes

- Implement BodySlide `SliderPresets` XML parsing for multi-file imports.
- Add preset list management, selected-preset binding, profile selection, omit-redundant behavior, and live template preview.
- Generate templates for all presets using the Core formatter and current profile data.
- Support copy-to-clipboard behavior and empty-output notifications for template text.
- Cover sparse sliders, optional XML declarations, special names, and negative values in tests.

## Capabilities

### New Capabilities
- `template-generation-flow`: Defines XML import, preset list behavior, live preview, and template generation for Flow A.

### Modified Capabilities

## Impact

- Adds XML parser and template-generation services in `BS2BG.Core`.
- Adds Templates workspace ViewModels and Avalonia controls in `BS2BG.App`.
- Expands tests for real-world XML observations and formatter integration.
