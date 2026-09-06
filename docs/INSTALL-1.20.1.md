# Install the Minecraft 1.20.1 proof of concept

Install the addon **only on the server**. Use the jar for the server's loader:

- `artifacts/1.20.1/fabric/cuboidplots-1.20.1-fabric-0.1.0.jar`
- `artifacts/1.20.1/forge/cuboidplots-1.20.1-forge-0.1.0.jar`

Use Java 17. Tested loaders are Fabric Loader 0.16.14 and Forge 47.4.10. Do not install both parent claim mods; initialization explicitly rejects that configuration.

## Parent dependencies

For FTB Chunks, the exact tested releases are Chunks 2001.3.8, Teams 2001.3.2, Library 2001.2.13, Architectury 9.2.14 and Cloth Config 11.1.136. FTB Ranks 2001.1.7 is optional. Select every dependency's matching loader artifact.

For OPAC, use Open Parties and Claims 0.30.3 for Minecraft 1.20.1 and the matching loader. On Fabric also install Fabric API 0.92.6+1.20.1 and Forge Config API Port 8.0.3. The dedicated tests also had Architectury and Cloth Config installed.

Fabric API 0.92.6+1.20.1 is required by the Fabric addon. The addon bundles its own portable core and MixinExtras; no separate installation of those is needed. Exact dependency filenames, official download links and checksums are recorded in `reports/1.20.1/<loader>/<parent>/dependencies.json`. Third-party mod jars are not redistributed in the artifacts directory.

## First run

1. Put the selected addon jar and parent dependencies in the server's `mods` folder.
2. Start the server. Look for `[CuboidPlots] Ready: parent=...` after the parent loads.
3. Claim land using the parent mod. Run `/cuboid tool`, select two blocks with the marked stick, then `/cuboid create workshop`.
4. Run `/cuboid` and use **Player permissions** to grant a visitor a specific action. A `BREAK` grant works on isolated stone; `CONTAINER` opens an empty-hand chest or barrel without granting building rights.
5. Use the menu preview button to show saved bounds. Use `/cuboid returns` to collect assigned-owner return boxes.

Configuration is created at `<world>/cuboidplots/config.properties`. Defaults are 16 regions and 65,536 inclusive blocks per creator. `provider=auto` prefers FTB Ranks when installed, otherwise configuration. Use `provider=config` to force configured limits, or `provider=ftbranks` to require ranks. Numeric nodes default to `cuboidplots.max_regions` and `cuboidplots.max_volume`. Only -1 means unlimited; zero means no allowance. Explicit addon administrators are configured by UUID in `admin_uuids`.

This reference limits region exceptions to bounded vanilla actions. Read [action scope](COMMANDS.md) and [item-return behaviour](RECOVERY.md), particularly the distinction between tracked placed blocks and preexisting terrain. The current automated checks are dedicated-server tests. They are not a claim that all modded tools, machines or storage systems are supported.

## Rebuild

From this branch on Windows, run `python tools/bootstrap.py`, then `powershell -File tools/build.ps1` for Fabric or `powershell -File tools/build-forge.ps1` for Forge. Bootstrap downloads the pinned JDKs and Gradle into `.work/toolchains`; it does not install them system-wide. Forge's runnable output is the `-all.jar`, which bundles MixinExtras. Artifact collection renames that file consistently.

`-TestHost` includes destructive test fixtures for the isolated worlds under `.work/servers`. Those jars are development-only and are excluded from the installable artifact list.
