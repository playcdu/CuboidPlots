package dev.cuboidplots.core;

import java.util.*;

/** Immutable snapshot. Creator is charged quotas; parentOwner binds protection ownership. */
public final class Region {
    public final String name, integration, parentOwner, dimension;
    public final UUID id, creator, assignedOwner;
    public final Cuboid bounds;
    public final boolean suspended;
    public final Map<UUID, Set<Action>> grants;

    public Region(String name, String integration, String parentOwner, String dimension, UUID creator,
                  Cuboid bounds, boolean suspended, Map<UUID, Set<Action>> grants) {
        this(name, integration, parentOwner, dimension, creator, creator, bounds, suspended, grants);
    }
    public Region(String name, String integration, String parentOwner, String dimension, UUID creator, UUID assignedOwner,
                  Cuboid bounds, boolean suspended, Map<UUID, Set<Action>> grants) {
        this(UUID.randomUUID(), name, integration, parentOwner, dimension, creator, assignedOwner, bounds, suspended, grants);
    }
    public Region(UUID id, String name, String integration, String parentOwner, String dimension, UUID creator, UUID assignedOwner,
                  Cuboid bounds, boolean suspended, Map<UUID, Set<Action>> grants) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,48}")) throw new IllegalArgumentException("Name must be 1-48 letters, digits, underscores or hyphens");
        this.name = name; this.integration = Objects.requireNonNull(integration); this.parentOwner = Objects.requireNonNull(parentOwner);
        this.dimension = Objects.requireNonNull(dimension); this.creator = Objects.requireNonNull(creator); this.bounds = Objects.requireNonNull(bounds);
        this.id = Objects.requireNonNull(id); this.assignedOwner = Objects.requireNonNull(assignedOwner);
        this.suspended = suspended;
        Map<UUID,Set<Action>> copy = new LinkedHashMap<>();
        grants.forEach((player, actions) -> copy.put(Objects.requireNonNull(player), Collections.unmodifiableSet(actions.isEmpty() ? EnumSet.noneOf(Action.class) : EnumSet.copyOf(actions))));
        this.grants = Collections.unmodifiableMap(copy);
    }
    public Region withBounds(Cuboid replacement) { return new Region(id, name, integration, parentOwner, dimension, creator, assignedOwner, replacement, suspended, grants); }
    public Region withOwner(UUID owner) { return new Region(id, name, integration, parentOwner, dimension, creator, owner, bounds, suspended, grants); }
    public Region suspend() { return new Region(id, name, integration, parentOwner, dimension, creator, assignedOwner, bounds, true, grants); }
    public Region permission(UUID player, Action action, boolean grant) {
        Map<UUID,Set<Action>> updated = new LinkedHashMap<>(grants);
        Set<Action> actions = EnumSet.noneOf(Action.class);
        actions.addAll(updated.getOrDefault(player, Collections.emptySet()));
        if (grant) actions.add(action); else actions.remove(action);
        if (actions.isEmpty()) updated.remove(player); else updated.put(player, actions);
        return new Region(id, name, integration, parentOwner, dimension, creator, assignedOwner, bounds, suspended, updated);
    }
}
