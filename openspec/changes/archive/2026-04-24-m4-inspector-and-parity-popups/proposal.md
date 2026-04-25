## Why

Several v1 features live in popups that are essential to day-to-day editing and parity. M4 moves the highest-use SetSliders editing into the inspector while preserving the required BoS JSON, image, and no-preset surfaces.

## What Changes

- Add the SetSliders editor as an inspector panel with enabled state, min/max percentages, live preview, and batch buttons.
- Add BoS JSON viewing with Java-compatible output and copy support.
- Add image preview/view behavior using the v1 `images/` lookup convention.
- Add the no-preset notifier for generated morph targets without assignments.
- Fall back to a secondary SetSliders window only if the inspector cannot fit the full parity editor ergonomically.

## Capabilities

### New Capabilities
- `inspector-parity-views`: Defines SetSliders editing, BoS JSON viewing, image viewing, and no-preset notifier parity.

### Modified Capabilities

## Impact

- Adds inspector ViewModels and controls.
- Adds BoS JSON formatting tests and UI smoke coverage for inspector-driven edit flows.
- Adds image discovery behavior while keeping `images/` relative to the working directory.
