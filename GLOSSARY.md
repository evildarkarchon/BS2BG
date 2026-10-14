# BS2BG

BS2BG converts BodySlide Slider Presets into BodyGen and BodyTypes of Skyrim outputs for a modder's Project.

## Language

**Project**:
The modder-authored collection of Slider Presets, Custom Morph Targets, and NPC Morph Assignments that is edited and preserved together. Custom Morph Targets and NPC Morph Assignments reference Slider Presets by name; a Project never references a Slider Preset it does not contain, and renaming or removing a Slider Preset updates every reference.
_Avoid_: Working document, workspace

**Slider Preset**:
A named collection of BodySlide slider choices available for assignment within a Project. Its name is trimmed, non-empty, dot-free, and unique within the Project without regard to case; imported dots normalize to spaces.

**Custom Morph Target**:
A named BodyGen target authored directly within a Project. Its name is trimmed, non-empty, and unique within the Project without regard to case; BodyGen condition syntax remains valid.

**NPC Morph Assignment**:
An NPC selected for BodyGen output together with its assigned Slider Presets. Its identity is the NPC's mod or plugin name plus editor ID, compared without regard to case.
_Avoid_: Morphed NPC, Project NPC

**Settings**:
The paired Standard and UUNP slider defaults, multipliers, and inversions that determine Slider Preset editing endpoints and generated output. One pair is current at a time; Save and Reload replace both together.
_Avoid_: Config, preferences (generation and theme preferences are separate)

**NPC Database**:
A session-scoped source catalog of NPCs that may be copied into a Project for morph assignment. Its entries remain independent from the Project.
