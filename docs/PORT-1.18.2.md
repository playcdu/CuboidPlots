# Minecraft 1.18.2 adapter differences

The Java 8 core is identical to the 1.20.1 branch. Geometry, exception decisions, creator quotas, ownership operations and persistence use the same methods and contract tests. The platform adapters retain the same class responsibilities and action vocabulary.

| Operation | 1.18.2 upstream mapping |
| --- | --- |
| FTB parent denial | `ClaimedChunkManager.protect(player, hand, position, protection, target)` |
| FTB chunk lookup | `dev.ftb.mods.ftbchunks.data.FTBChunksAPI.getManager().getChunk(...)` |
| FTB manager authority | Team owner or `Team.getHighestRank(UUID).isOfficer()` |
| FTB parent unclaim | `ClaimedChunkManager.unregisterClaim` before mutation |
| FTB ownership transfer | `FTBChunks.transferClaims` writes `ClaimedChunk.teamData`; a narrow field-write wrapper performs recovery first |
| FTB rank missing value | `PermissionValue.isDefaultValue()`; numeric values still use `asNumber()` and exact long conversion |
| OPAC protection | The same target-specific `ChunkProtection` methods as 1.20.1, remapped with class-literal descriptors |
| Minecraft block action scope | `ServerPlayerGameMode.handleBlockBreakAction` has no sequence argument |
| Fabric commands | Command registration API v1 |
| Forge server tick | `ServerLifecycleHooks.getCurrentServer()`; Forge 40 tick events have no server accessor |

Minecraft's older text, registry, player-level and position APIs are translated inside platform code. Recovery still uses the same source journal, inventory receipts, shulker packing rules and persisted entity loading. No permission or quota semantics are weakened to accommodate the port.

The test-only FTB transfer invoker is included only in `-PtestHost` builds. Production jars contain the transfer interception but cannot invoke the test fixture. Reuse the dedicated acceptance scenarios for both parent mods and both loaders, then run the restart and ordinary-protocol client checks separately.
