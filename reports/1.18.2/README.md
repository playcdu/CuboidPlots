# Minecraft 1.18.2 verification

| Loader | Parent | Build | Dedicated behaviour | Actual restart | Production jar and protocol clients | Graphical client |
| --- | --- | --- | --- | --- | --- | --- |
| Fabric 0.16.14 | FTB Chunks 1802.3.19-build.362 | Passed | 50 assertions | 7 assertions | Passed, 6 protocol checks | Not verified |
| Fabric 0.16.14 | OPAC 0.30.3 | Passed | 50 assertions | 7 assertions | Passed, 6 protocol checks | Not verified |
| Forge 40.3.12 | FTB Chunks 1802.3.19-build.362 | Passed | 50 assertions | 7 assertions | Passed, 6 protocol checks | Not verified |
| Forge 40.3.12 | OPAC 0.30.3 | Passed | 50 assertions | 7 assertions | Passed, 6 protocol checks | Not verified |

Each combination was launched separately with its exact dependencies. The behaviour fixture uses an owner and two non-operator identities and runs real parent protection calls plus selected Minecraft action mutations. A separate restart verifies persisted grants, parent enforcement and exact returned-item quantities. The production jar then accepts two ordinary protocol clients, displays a 54-slot chest menu, protects its decorative slots and sends private preview particles only to the selecting player.

The shared Java 8 core passed 78 assertions and its committed source tree is identical to 1.20.1: `618d88dd9e4f61ec4cdc5327e66f4288d55019cc`.

Both loaders also passed 13 live FTB Ranks checks and 3 additional restart checks using an ordinary network client. These cover exact limits, independent count and volume rejection, failed resize accounting, rank downgrade, reduction and deletion while over quota, zero limits, fractional-value failure, missing-value fallback, explicit unlimited values, and membership addition/removal. The rank configuration was restored after each test. The `ranks-*` files contain this evidence separately from the 50-action acceptance fixture.

The initial production Forge check found stale test-only mixin metadata when changing Gradle build modes. The build now declares the test-host flag as a resource-processing input and regenerates the mixin list. Both production Forge combinations were then rebuilt and passed. Failed attempts are not counted as passes.

See each loader/parent directory for the actual server logs, dependency hashes, acceptance summary and protocol results. These are dedicated and network checks, not a manual graphical demonstration. Crash fault injection, arbitrary modded storage and unbounded area tools remain outside verified scope. Item recovery limitations are documented in `docs/RECOVERY.md`.

## Forge 0.1.1 packaging fix

The current Forge artifact removes an incompatible MixinExtras upper bound. Runtime classes match the tested 0.1.0 exactly. New 0.5.4 dependency-resolution, server-startup and protocol-client evidence is in `forge/<parent>/hotfix-0.1.1`. See `docs/FORGE-0.1.1-HOTFIX.md`.
