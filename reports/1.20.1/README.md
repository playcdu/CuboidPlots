# Minecraft 1.20.1 verification

All four combinations passed the same dedicated-server scenarios, using an owner and two ordinary non-operator UUID identities. Exact dependency URLs and SHA-256 values are in each combination's `dependencies.json`.

| Loader | Parent | Compile | Normal-jar startup | Server behaviour | Restart | Unmodified-protocol clients | Manual visual client |
|---|---|---|---|---|---|---|---|
| Fabric 0.16.14 | FTB Chunks 2001.3.8 | Passed | Passed | 50 checks passed | 7 checks passed | 6 checks passed | Not performed |
| Fabric 0.16.14 | OPAC 0.30.3 | Passed | Passed | 50 checks passed | 7 checks passed | 6 checks passed | Not performed |
| Forge 47.4.10 | FTB Chunks 2001.3.8 | Passed | Passed | 50 checks passed | 7 checks passed | 6 checks passed | Not performed |
| Forge 47.4.10 | OPAC 0.30.3 | Passed | Passed | 50 checks passed | 7 checks passed | 6 checks passed | Not performed |

The 50-check suite exercises actual parent protection methods, vanilla game-mode block breaking and placement, container opening, doors, levers, note blocks and pumpkin carving. It verifies different permissions in adjacent and stacked cuboids, four covered chunks, negative chunk coordinates, different dimensions, revocation and stale binding suspension. Entity interaction and damage use separate scoped calls to the actual parent's protection API. Food use checks the independent addon scope; both parents ordinarily permit this food, so this test is not a claimed default-denial override.

The recovery fixture places a chest through the player game-mode path, fills it with 27 stacks of gold, adds dropped diamonds and deletes the cuboid. A separate transfer fixture returns dropped emeralds on direct parent ownership transfer. Restart checks verify exact chest, gold and diamond counts, retained grants, offline return persistence and collection without duplication on a repeated command.

The six protocol checks use two network clients speaking vanilla Minecraft 1.20.1, without Fabric, Forge or the addon installed on the clients. They verify successful login, the surveyor command, a 54-slot chest menu plus 36 inventory slots, preview particle packets to the selector, zero preview packets to the observer, and an empty cursor after a shift-click attempt on menu decoration. This is network verification, not a screenshot or manual visual assessment.

`behaviour.log`, `restart.log` and `production-startup.log` preserve the evidence. `protocol-result.json` records the separate client result. `artifacts/manifest.json` identifies source commit and jar checksums. The development test harness is excluded from both installable jars.

The portable Java 8 contract suite additionally passes 78 assertions for geometry, action separation, authorization, quotas, provider failure, downgrade behaviour, persistence corruption, concurrent quota enforcement, overflow and recovery retry behaviour.

## Still outside this verification claim

- Live FTB Ranks membership changes and conditional numeric permissions are not yet exercised by the server suite; its quotas use configuration. Provider and downgrade semantics have portable-core tests.
- Crash injection between individual recovery journal, world and player-data writes has not been performed. Ordinary restart success is not a proof of a crash-atomic transaction.
- Arbitrary modded blocks, storage, automation and tool effects are outside the reference adapter's action/recovery scope.
- Manual graphical client testing has not been performed in this environment.
