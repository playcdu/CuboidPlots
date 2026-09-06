# Forge 0.1.1 dependency compatibility fix

The original Forge jars declared MixinExtras `[0.4.1,0.5)`. That range cannot coexist with pack mods requiring `[0.5.4,)`, so Forge rejected dependency resolution before starting the server. This was an addon packaging bug.

The replacement jars declare `[0.4.1,)` and still bundle the same minimum 0.4.1 implementation. Forge can select the newer shared copy supplied by the pack. All addon runtime class files are byte-identical to 0.1.0; this fix changes the dependency metadata and addon version only.

Replace the old CuboidPlots Forge jar with the **0.1.1 jar for the same Minecraft version**. Install only one CuboidPlots jar on the server. Other mods and their bundled libraries do not need to be removed or downgraded for this conflict.

- Minecraft 1.20.1: `artifacts/1.20.1/forge/cuboidplots-1.20.1-forge-0.1.1.jar`
- Minecraft 1.18.2: `artifacts/1.18.2/forge/cuboidplots-1.18.2-forge-0.1.1.jar`

Verification reproduces the original failure with a private low-code Forge fixture that embeds MixinExtras 0.5.4 and requires `[0.5.4,)`. The old jar fails JarJar selection. With the replacement, both Minecraft versions start with both parent mods separately and log MixinExtras 0.5.4 initialization. Two ordinary protocol clients then pass all six login, menu, selection and private-particle checks. The full user modpack was not available for testing.

The `hotfix-0.1.1` report directories contain the new runtime evidence. Earlier behaviour, restart and rank checks remain baseline evidence for the identical addon class files; they were not rerun as part of this metadata-only hotfix. Superseded Forge jars are retained only under `artifacts/superseded` and must not be installed. Fabric artifacts are unchanged.
