# Commands and menus

All commands begin with `/cuboid`. Commands require a player context. Names resolve only to currently online players; use a UUID for offline players. UUIDs are persisted, never mutable display names.

| Command | Purpose |
|---|---|
| `menu` or no arguments | Open the vanilla chest manager |
| `help` | In-game command and action reference |
| `tool` | Receive a marked surveyor's stick |
| `pos1 [x y z]`, `pos2 [x y z]` | Set an explicit corner, or use your current block |
| `selection` | Show dimension, corners and inclusive volume |
| `preview` | Draw a private selection outline for 30 seconds |
| `create <name>` | Create using the current selection |
| `list` | List regions you can manage or own |
| `info <name>` | Inspect bounds, binding, owner, creator and grants |
| `resize <name>` | Replace bounds with the current selection |
| `delete <name>` | Return contents and remove the region |
| `owner <name> <player-or-UUID>` | Return contents to the old owner and assign a new owner |
| `grant <name> <player-or-UUID> <action>` | Add one independent permission |
| `revoke <name> <player-or-UUID> <action>` | Remove one permission, restoring parent behaviour |
| `permissions <name>` | List UUID permission entries |
| `quota` | Show creator usage, effective limits and remaining allowance |
| `diagnose <action> <x> <y> <z>` | Probe your core grant at one position in your dimension |
| `why` | Show your most recent scoped parent-protection decision |
| `returns` | Collect reserved return boxes into free inventory slots |

`diagnose` is a geometry/permission probe. It does not claim that an actual action is safely bounded. Use `why` after the actual action for the adapter's decision.

The chest manager includes region cards, saved bounds, owner assignment, player selection, independent permission toggles, quota usage, previews and item collection. Destructive menu deletion and reassignment have confirmation screens. Commands apply immediately. Shift-clicks, drag, number keys and drop actions cannot remove menu decoration items.

## Action vocabulary

| Action | Reference scope and affected positions |
|---|---|
| `BREAK` | Selected inert vanilla blocks, oak doors and red beds; both halves where applicable |
| `PLACE` | Inert vanilla blocks, oak doors and red beds; actual destination plus every additional part |
| `CONTAINER` | Empty-hand chest or barrel opening; both halves of a double chest |
| `DOOR` | Empty-hand oak door, oak trapdoor, oak fence gate; both door halves |
| `SWITCH` | Empty-hand isolated lever or stone button |
| `OTHER_BLOCK` | Empty-hand isolated note block |
| `ITEM_ON_BLOCK` | Unenchanted shears carving a pumpkin; target and all six adjacent blocks |
| `ITEM_IN_AIR` | Apple, bread or carrot use; eye-position block; no remote world effect |
| `ENTITY_INTERACT` | Empty-hand armor stand or item frame; entire target bounding box |
| `ENTITY_DAMAGE` | Empty-hand armor stand or item frame; entire target bounding box; parent protection policies differ for living entities |

Container, door and switch classification takes precedence over generic block interactions. A block item with an appropriate placement context is classified as placement. No generic interaction grant substitutes for a container grant. Container permission permits opening and ordinary inventory use; individual inventory slot policies are outside scope.

The conservative neighborhood audit rejects fluids, connected redstone, unknown adjacent blocks, modded tools and other effects it cannot safely bound. Buckets, projectiles, pistons, explosions and arbitrary automation receive no addon exception. Parent owners retain the parent's ordinary abilities in these cases. These cuboids do not isolate all machines or indirect effects within one parent chunk.
