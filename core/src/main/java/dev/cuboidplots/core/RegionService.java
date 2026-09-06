package dev.cuboidplots.core;

import java.io.IOException;
import java.util.*;

/**
 * All application operations serialize through this service. Validation, quota accounting,
 * persistence and publication share one lock, including when called outside the server thread.
 */
public final class RegionService {
    public static final long HARD_VOLUME = 16_777_216L, HARD_CHUNKS = 4096;
    private final ParentClaims parent;
    private final LimitProvider limits;
    private final RegionStore store;
    private final RegionLifecycle lifecycle;
    private List<Region> regions;
    private boolean unhealthy;

    public RegionService(ParentClaims parent, LimitProvider limits, RegionStore store) throws IOException {
        this(parent, limits, store, (region, reason, current) -> {});
    }
    public RegionService(ParentClaims parent, LimitProvider limits, RegionStore store, RegionLifecycle lifecycle) throws IOException {
        this.parent = Objects.requireNonNull(parent); this.limits = Objects.requireNonNull(limits); this.store = Objects.requireNonNull(store);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        regions = store.load();
        Set<String> names = new HashSet<>();
        try {
            for (Region region : regions) {
                validateBounds(region.bounds);
                if (!names.add(region.name)) throw new IllegalArgumentException("Duplicate region name");
                rejectOverlap(region.name, region.dimension, region.bounds);
            }
            for (Region region : regions) usage(region.creator); // Check aggregate arithmetic too.
        } catch (RuntimeException ex) { throw new IOException("Invalid region database", ex); }
        // Binding checks are lazy so construction never mistakes an uninitialized parent for wilderness.
        // Adapters must call revalidateAll once the parent has finished loading and before accepting actions.
    }

    public static void validateBounds(Cuboid box) {
        if (box.volume() > HARD_VOLUME || box.chunkCount() > HARD_CHUNKS)
            throw new IllegalArgumentException("Selection exceeds safety cap: " + HARD_VOLUME + " blocks or " + HARD_CHUNKS + " chunks");
        if (box.min.x < -30_000_000 || box.max.x >= 30_000_000 || box.min.z < -30_000_000 || box.max.z >= 30_000_000 || box.min.y < -2048 || box.max.y > 2047)
            throw new IllegalArgumentException("Selection exceeds supported coordinate range");
    }

    public synchronized Region create(UUID actor, boolean administrator, String name, String dimension, Cuboid bounds) throws IOException {
        validateBounds(bounds);
        if (find(name) != null) throw new IllegalArgumentException("Region name already exists");
        if (regions.size() >= 10000) throw new IllegalArgumentException("Server safety cap: 10000 regions");
        rejectOverlap(null, dimension, bounds);
        String owner = parent.ownerAt(dimension, bounds.min.x >> 4, bounds.min.z >> 4);
        if (owner == null || !bindingMatches(dimension, bounds, owner)) throw new IllegalArgumentException("Every intersected chunk must be claimed by the same parent owner");
        requireManager(actor, administrator, owner);
        long[] before = usage(actor);
        checkLimits(actor, before, new long[]{Math.addExact(before[0], 1), Math.addExact(before[1], bounds.volume())});
        Region region = new Region(name, parent.integration(), owner, dimension, actor, bounds, false, Collections.emptyMap());
        List<Region> changed = new ArrayList<>(regions); changed.add(region); commit(changed); return region;
    }

    public synchronized Region resize(UUID actor, boolean administrator, String name, Cuboid bounds) throws IOException {
        Region region = managed(actor, administrator, name);
        validateBounds(bounds); rejectOverlap(name, region.dimension, bounds);
        if (region.suspended || !isCurrent(region)) throw new IllegalArgumentException("Suspended or stale region: delete and recreate it");
        if (!bindingMatches(region.dimension, bounds, region.parentOwner)) throw new IllegalArgumentException("Replacement crosses wilderness or another owner");
        long[] before = usage(region.creator);
        long after = Math.addExact(Math.subtractExact(before[1], region.bounds.volume()), bounds.volume());
        checkLimits(region.creator, before, new long[]{before[0], after});
        Region changed = region.withBounds(bounds); replace(changed); return changed;
    }

    public synchronized void delete(UUID actor, boolean administrator, String name) throws IOException {
        Region region = required(name);
        // A creator can always release their own quota, including after losing parent authority.
        if (!region.creator.equals(actor)) managed(actor, administrator, name);
        lifecycle.beforeOwnershipRemoved(region, RegionLifecycle.Removal.DELETE, isCurrent(region));
        List<Region> changed = new ArrayList<>(regions); changed.remove(region); commit(changed);
    }

    public synchronized void permission(UUID actor, boolean administrator, String name, UUID recipient, Action action, boolean grant) throws IOException {
        Region region = required(name);
        if (!region.assignedOwner.equals(actor)) managed(actor, administrator, name);
        if (grant && (region.suspended || !isCurrent(region))) throw new IllegalArgumentException("Suspended or stale region: delete and recreate it");
        if (grant && !region.grants.containsKey(recipient) && region.grants.size() >= 10000) throw new IllegalArgumentException("Too many permission entries");
        replace(region.permission(recipient, action, grant));
    }

    public synchronized void assignOwner(UUID actor, boolean administrator, String name, UUID owner) throws IOException {
        Region region = managed(actor, administrator, name);
        if (region.suspended || !isCurrent(region)) throw new IllegalArgumentException("Suspended or stale region: delete and recreate it");
        if (region.assignedOwner.equals(owner)) return;
        lifecycle.beforeOwnershipRemoved(region, RegionLifecycle.Removal.REASSIGN, true);
        // Delegation changes permission management only. It never moves quota charges or adds action grants.
        replace(region.withOwner(owner));
    }
    public synchronized Region inspect(UUID actor, boolean administrator, String name) {
        Region region = required(name);
        if (!region.assignedOwner.equals(actor) && !region.creator.equals(actor)) managed(actor, administrator, name);
        return region;
    }
    public synchronized List<Region> list(UUID actor, boolean administrator) {
        List<Region> visible = new ArrayList<>();
        for (Region region : regions) if (administrator || region.creator.equals(actor) || region.assignedOwner.equals(actor) || parent.canManage(actor, region.parentOwner)) visible.add(region);
        return Collections.unmodifiableList(visible);
    }
    public synchronized long[] usage(UUID creator) {
        long count = 0, volume = 0;
        for (Region region : regions) if (region.creator.equals(creator)) { count = Math.addExact(count, 1); volume = Math.addExact(volume, region.bounds.volume()); }
        return new long[]{count, volume};
    }
    /** Trusted platform adapters only. Commands must use inspect/list with actor authorization. */
    public synchronized Region regionAt(String dimension, Position position) { return at(dimension, position); }
    public Limits effectiveLimits(UUID creator) {
        try { return Objects.requireNonNull(limits.effectiveLimits(creator), "Provider returned no limits"); }
        catch (Exception ex) { throw new IllegalStateException("Permission provider failed; capacity increases are disabled", ex); }
    }

    /** Complete affected footprint, supplied by a platform adapter. Unknown effects must use bounded=false. */
    public synchronized Decision evaluate(UUID player, String dimension, Action action, Collection<Position> affected, boolean bounded) {
        if (unhealthy) return Decision.defer("Persistence failure disabled all addon exceptions");
        if (!bounded || affected.isEmpty() || affected.size() > 4096) return Decision.defer("Effects cannot be bounded safely");
        for (Position position : affected) {
            Region region = at(dimension, position);
            if (region == null) return Decision.defer("At least one affected position is outside all regions");
            if (region.suspended) return Decision.defer("Region is suspended");
            try {
                if (!isCurrent(region)) { replace(region.suspend()); return Decision.defer("Parent ownership changed; region suspended"); }
            } catch (Exception ex) { return Decision.defer("Parent binding could not be verified: " + ex.getMessage()); }
            if (!region.grants.getOrDefault(player, Collections.emptySet()).contains(action)) return Decision.defer("No explicit " + action + " grant at an affected position");
        }
        return Decision.grant();
    }

    /** Called on removal/reassignment, even if the same owner immediately reclaims the chunk. */
    public synchronized void ownershipChanged(String dimension, int chunkX, int chunkZ) throws IOException {
        List<Region> changed = new ArrayList<>(); boolean dirty = false; IOException recoveryFailure = null;
        for (Region region : regions) {
            if (!region.suspended && region.dimension.equals(dimension) && region.bounds.coversChunk(chunkX, chunkZ)) {
                // The platform must invoke this before the parent's removal/reassignment takes effect.
                try { lifecycle.beforeOwnershipRemoved(region, RegionLifecycle.Removal.PARENT_CHANGE, isCurrent(region)); }
                catch (IOException ex) { recoveryFailure = ex; }
                changed.add(region.suspend()); dirty = true;
            }
            else changed.add(region);
        }
        if (dirty) commit(changed);
        if (recoveryFailure != null) throw recoveryFailure;
    }
    public synchronized void revalidateAll() throws IOException {
        List<Region> changed = new ArrayList<>(); boolean dirty = false;
        for (Region region : regions) {
            if (!region.suspended && !isCurrent(region)) { changed.add(region.suspend()); dirty = true; }
            else changed.add(region);
        }
        if (dirty) commit(changed);
    }
    private void checkLimits(UUID creator, long[] before, long[] after) {
        // Reductions remain possible even while the permission provider is unavailable.
        if (after[0] <= before[0] && after[1] <= before[1]) return;
        Limits effective = effectiveLimits(creator);
        Limits.check(before[0], after[0], effective.regions, "Region count");
        Limits.check(before[1], after[1], effective.volume, "Block volume");
    }
    private boolean bindingMatches(String dimension, Cuboid box, String owner) {
        for (int x = box.min.x >> 4; x <= (box.max.x >> 4); x++)
            for (int z = box.min.z >> 4; z <= (box.max.z >> 4); z++)
                if (!owner.equals(parent.ownerAt(dimension, x, z))) return false;
        return true;
    }
    private boolean isCurrent(Region region) { return parent.integration().equals(region.integration) && bindingMatches(region.dimension, region.bounds, region.parentOwner); }
    private Region managed(UUID actor, boolean admin, String name) {
        Region region = required(name);
        requireManager(actor, admin, region.parentOwner);
        return region;
    }
    private void requireManager(UUID actor, boolean admin, String owner) {
        if (!admin && !parent.canManage(actor, owner)) throw new SecurityException("Parent claim management authority or explicit addon administrator permission required");
    }
    private Region at(String dimension, Position position) {
        for (Region region : regions) if (region.dimension.equals(dimension) && region.bounds.contains(position)) return region;
        return null;
    }
    private Region find(String name) { for (Region region : regions) if (region.name.equals(name)) return region; return null; }
    private Region required(String name) { Region region = find(name); if (region == null) throw new IllegalArgumentException("Region not found or inaccessible"); return region; }
    private void rejectOverlap(String except, String dimension, Cuboid bounds) {
        for (Region region : regions) if (!region.name.equals(except) && region.dimension.equals(dimension) && region.bounds.intersects(bounds))
            throw new IllegalArgumentException("Selection overlaps an existing region (inclusive bounds)");
    }
    private void replace(Region replacement) throws IOException {
        List<Region> changed = new ArrayList<>(regions);
        for (int i = 0; i < changed.size(); i++) if (changed.get(i).name.equals(replacement.name)) { changed.set(i, replacement); break; }
        commit(changed);
    }
    private void commit(List<Region> changed) throws IOException {
        try { store.save(changed); }
        catch (IOException ex) { unhealthy = true; throw ex; }
        regions = changed;
    }
}
