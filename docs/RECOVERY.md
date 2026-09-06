# Assigned-owner item returns

Recovery runs before deletion, reassignment, parent unclaim or parent ownership transfer. The recipient is the previous assigned cuboid owner, independent of who created the cuboid or manages its parent chunks.

## Scope

- Contents of vanilla block-entity containers inside the cuboid, including containers that existed before the cuboid.
- Dropped item entities whose block position is inside the inclusive bounds. Persisted entities are loaded before the snapshot.
- Normal block loot from vanilla blocks placed through player block-item operations after region creation. Placement history records positions and block identities. Breaking a tracked block removes its history.
- Existing shulker boxes remain intact as standalone returned boxes. Other stacks are split at their normal maximum stack sizes into 27-slot purple shulkers.

Loot is evaluated with an unenchanted diamond pickaxe. It is not a silk-touch copy of every block. Blocks without ordinary drops produce no item. Natural terrain and structures without placement history are preserved. Modded storage, modded block loot and double chests crossing the region boundary are rejected for explicit handling. Inventory-bearing entities, piston-moved history, modded placement mechanisms, commands and pre-installation placement history are not covered by the reference adapter.

## Transaction sequence

1. Load the relevant terrain and entity data. Snapshot containers, dropped-item UUIDs, tracked blocks and return payload.
2. Write and force a `PREPARED` journal before modifying world sources.
3. Validate every source, clear inventories, discard captured items, and remove tracked blocks without neighbor-driven destruction.
4. Force a world save. Persist placement-history removal, then mark the batch `READY`.
5. Complete the region ownership mutation and its database write.

Prepared journals resume at startup before addon permissions become available. Source reconciliation accepts unchanged sources or already-cleared sources, and stops on conflicts. Batch delivery uses receipt tags and saved player data to reduce duplicate-delivery risk across interruption. These mechanisms have not established a filesystem-wide atomic transaction across all Minecraft files. Crash/fault injection coverage must be consulted separately from ordinary restart tests.

Only the recipient can collect a ready batch. Full inventories leave the remainder queued. No box is dropped into the world for another player to pick up. Collection is available through `/cuboid returns` and the chest menu. The server does not need the recipient online at removal time.

Changing cuboid bounds currently changes the permission geometry without harvesting the removed portion. Use deletion and recreation when the intended operation is to clear and return the entire previous occupant's space. A reassignment preserves existing explicit UUID grants; review them in the permission menu when changing occupants.
