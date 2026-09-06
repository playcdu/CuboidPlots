# Cuboid Plots

A server-side reference addon for block-accurate permission exceptions within FTB Chunks or Open Parties and Claims. Parent chunks stay claimed. Several adjacent or vertically stacked cuboids can have different assigned owners and UUID permission entries.

This is a proof of concept with a deliberately narrow, tested action scope. Publication of an upstream dependency is separate from addon compatibility. See [upstream releases](research/COMPATIBILITY.md) and the verification reports before selecting a target.

## In-game workflow

1. Claim the enclosing chunks with exactly one parent mod.
2. Run `/cuboid` for the six-row chest menu, then **Get surveyor's stick**, or use `/cuboid tool`.
3. Left-click a block for corner one. Right-click for corner two. Selection does not break or activate blocks. A private particle outline appears for 30 seconds.
4. Run `/cuboid create workshop`, or use **Create from selection** in the menu. Both corner blocks are included, including every air block between them.
5. Select the region, choose **Player permissions**, choose a player, and toggle individual actions. `/cuboid grant workshop PlayerName BREAK` is the command equivalent.
6. Choose **Preview saved bounds** to inspect an existing cuboid. `/cuboid preview` redraws the current selection.

The addon uses a marked vanilla stick, vanilla chest menus and vanilla particles. It has no client addon, custom networking requirement, resource pack or WorldEdit dependency. A parent mod may have its own optional client features; these are separate from this addon.

Only parent claim managers and configured addon administrators create, resize or reassign regions. An assigned cuboid owner can manage that cuboid's UUID action entries. Assignment adds no implicit building grant. The original creator continues paying both quotas. Creators can delete their own regions to release quota even after losing parent authority.

Missing grants defer to the parent. They do not deny an owner or teammate who already has parent permissions. Cuboid grants are exceptions, not a second deny layer.

## Item returns

Deleting a cuboid, assigning it to a different owner, or removing/transferring a parent claim reserves its contents for the previous assigned owner. Vanilla container contents, dropped item entities and normal drops from tracked placed blocks are packed into purple shulker boxes. Use **Collect returned items** or `/cuboid returns`. Offline recipients and full inventories keep a persistent return queue.

Placement history begins when a region is created. Preexisting terrain and structures are not classified as player-placed blocks. Existing containers still have their contents collected. Existing shulkers are returned intact because vanilla forbids nesting shulker boxes. Unsupported storage or unsafe recovery fails the ownership operation instead of silently destroying its items. See [recovery details](docs/RECOVERY.md).

## Source layout

- `core`: dependency-free Java 8 geometry, grants, quotas, application operations and checked persistence.
- `platform`: Minecraft adapters, bounded action classification, commands, menus, selection particles and recovery.
- `fabric`, `forge`: loader entrypoints and build configuration.
- `testhost`: dedicated-server acceptance fixtures included only with `-PtestHost`.
- `research`: exact upstream release inventory, source audit and toolchain pins.
- `tools`: reproducible builds, dependency retrieval and local test-server runners.

See [commands](docs/COMMANDS.md), [architecture](docs/ARCHITECTURE.md), and [recovery](docs/RECOVERY.md). Development test jars run destructive fixtures in private test worlds and are not installation artifacts.
