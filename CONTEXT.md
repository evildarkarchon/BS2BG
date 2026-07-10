# BS2BG

BS2BG converts BodySlide presets into BodyGen and BoS outputs while preserving the profile choices carried by a modder's project.

## Language

**Custom profile**:
A user-authored slider definition identified by a case-insensitive display name, distinct from the bundled Skyrim CBBE, Skyrim UUNP, and Fallout 4 CBBE profiles.
_Avoid_: User profile, profile file

**Referenced custom profile**:
A custom profile named by at least one slider preset in the current project. Referenced custom profiles, rather than every available custom profile, define the project's portable profile scope.
_Avoid_: Active profile

**Project copy**:
A custom profile definition carried by a project. When duplicate project copies have the same case-insensitive name, the first eligible definition is authoritative and later definitions are ignored.
_Avoid_: Project profile

**Local custom profile**:
A reusable custom profile stored outside a project. It can supply a referenced custom profile when the project has no same-named project copy.
_Avoid_: Global profile

**Output artifact plan**:
The complete ordered description of the BodyGen INI and BoS JSON files BS2BG intends to produce for one project state, including each file's commit-group-relative path and exact byte content. The same plan governs preview, direct export, automation, and portable bundles.
_Avoid_: Export plan, Output list
