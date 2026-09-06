# Developer walkthrough

## Permission exception

`PlayerGameModeMixin` wraps a synchronous vanilla action in an `ActionScope`. `ActionPlanner` classifies the action and enumerates its affected footprint. A bed placement includes both bed blocks; a double chest includes both inventories. The target is checked regardless of where the player stands. Every wrapper restores the previous scope in `finally`, including nested actions.

The parent mod then runs its own protection method. FTB's `shouldPreventInteraction` normally returns true for a denied action. Its return injection changes only that decision to false when `ActionScope.grants` matches the actor, parent probe position and target entity, and the core returns `REGION_GRANT`. OPAC's specific protection methods are intercepted with equivalent action context. OPAC's global disabled-content sets are still respected. No event belonging to another protection system is indiscriminately uncancelled.

`RegionService.evaluate` requires every affected voxel to be inside an active region with an explicit UUID grant for the same action. It verifies the parent integration and owner identity in every covered chunk. A missing grant, unsupported effect, inaccessible parent or stale binding returns `DEFER_TO_PARENT`. This result is neither an allowance nor a forced denial.

The parent method can combine categories. The scoped vanilla action separates break from place and container from other interaction before the combined parent method is called. OPAC's same-chunk shortcuts are therefore never sufficient to authorize a cuboid exception.

FTB uses a return injection because its own denial is explicit. OPAC uses narrowly scoped head injections because its internal shortcut and policy tree do not expose a sufficient position-aware extension hook. OPAC's whole-chunk overrider is intentionally unused.

## Creation and quota enforcement

Commands and inventory clicks call the same `RegionService` methods. Creation normalizes inclusive corners, performs overflow-safe volume and chunk calculations, rejects excessive dimensions, checks every covered parent chunk and verifies parent-management authority. Overlapping cuboids in one dimension are rejected, including suspended regions. Touching faces and separated vertical ranges do not overlap.

The creator UUID is independent of the parent team and assigned cuboid owner. `usage` sums that creator's count and inclusive volume, including air and suspended regions. Grants and assignment never charge recipients. Before increases, `LimitProvider.effectiveLimits` is queried again. `Limits` checks count and volume independently. A rank downgrade preserves existing regions and allows reductions even while over quota. Provider failures prevent increases, but do not prevent deletion or reductions.

The configuration provider supplies fallback limits. `auto` prefers FTB Ranks when loaded; `config` always uses configuration; `ftbranks` requires that mod. Missing numeric values use configured defaults. Zero permits no capacity; only -1 is unlimited. Invalid values or provider errors do not silently become unlimited. FTB Ranks reads the selected release's actual `FTBRanksAPI.getPermissionValue` and parses an exact integer. Conditional ranks require a currently online creator when capacity increases.

All application mutations synchronize on one service lock. Validation, quota calculations, writing the replacement database and publishing its in-memory list form one operation. A failed save leaves old quota usage intact and disables addon exceptions. `FileRegionStore` uses a bounded, versioned binary format, CRC validation, a forced temporary file and atomic replacement. Corruption is an initialization error, not an empty database.

## Ownership and recovery

Parent removal and transfer hooks run before the parent binding changes. They invoke item recovery and then persist suspension. Completed regions are committed individually so a later recovery failure can be retried without repeating completed work. A failure aborts the parent mutation. Every later action still revalidates the binding, so missed external changes cannot grant access to a new parent's land. Restart validation also suspends stale regions.

Observed stale bindings are never harvested after the fact: doing so could take items from the new owner. Recovery after direct external save edits is a manual review case. Normal supported parent operations use the pre-change hooks.

## Portability boundary

The entire `core/src` tree is Java 8 and imports no Minecraft, loader or parent-mod classes. Platform code translates Minecraft positions, UUIDs, dimensions and parent identities into project-owned types. Loader entrypoints register lifecycle, command and tick callbacks. Version-sensitive parent methods are confined to integration adapters and targeted Mixins. World recovery and UI serialization are platform responsibilities, not part of the portable geometry and permissions core.
