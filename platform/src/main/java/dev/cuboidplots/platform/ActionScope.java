package dev.cuboidplots.platform;

import dev.cuboidplots.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.util.*;

/**
 * A synchronous vanilla action scope, never a player/chunk bypass. WrapMethod adapters
 * always restore the previous scope in finally, including nested calls and exceptions.
 */
public final class ActionScope implements AutoCloseable {
    private static final ThreadLocal<ActionScope> CURRENT = new ThreadLocal<>();
    private static final Map<UUID,String> LAST = new LinkedHashMap<UUID,String>() {
        protected boolean removeEldestEntry(Map.Entry<UUID,String> entry) { return size()>512; }
    };
    private final ActionScope previous;
    public final ServerPlayer player;
    public final Action action;
    public final List<BlockPos> effects;
    private final Set<BlockPos> parentProbes;
    private final boolean bounded;
    private final Entity entity;
    private final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> before = new HashMap<>();
    ActionScope(ServerPlayer player, Action action, List<BlockPos> effects, Collection<BlockPos> probes, boolean bounded, Entity entity) {
        this.player=player; this.action=action; this.effects=effects; this.parentProbes=new HashSet<>(probes); this.bounded=bounded; this.entity=entity;
        previous=CURRENT.get(); CURRENT.set(this);
        if(action==Action.PLACE) for(BlockPos pos:effects) before.put(pos,player.level().getBlockState(pos));
    }
    public static ActionScope current() { return CURRENT.get(); }
    public void close() {
        try {
            if(AddonRuntime.recovery!=null) {
                if(action==Action.PLACE) {
                    List<BlockPos> changed=new ArrayList<>();
                    for(BlockPos pos:effects)if(!player.level().getBlockState(pos).equals(before.get(pos))&&!player.level().getBlockState(pos).isAir())changed.add(pos);
                    AddonRuntime.recovery.recordPlacement(player,changed);
                } else if(action==Action.BREAK) AddonRuntime.recovery.forgetBroken(player,effects);
            }
        } finally { if(previous==null)CURRENT.remove(); else CURRENT.set(previous); }
    }
    public static void clear() { CURRENT.remove(); }
    public static String lastReason(UUID player) { return LAST.getOrDefault(player,"No scoped parent protection probe recorded in this session"); }
    public static boolean grants(Entity actor, BlockPos probe, Entity target) {
        ActionScope scope=CURRENT.get();
        if(scope==null || actor!=scope.player || target!=scope.entity || !scope.parentProbes.contains(probe) || AddonRuntime.regions==null) return false;
        List<Position> affected=new ArrayList<>();
        for(BlockPos pos:scope.effects) affected.add(AddonRuntime.position(pos));
        Decision decision=AddonRuntime.regions.evaluate(scope.player.getUUID(), scope.player.level().dimension().location().toString(), scope.action, affected, scope.bounded);
        LAST.put(scope.player.getUUID(),scope.action+" at "+affected+": "+decision.result+". "+decision.reason);
        return decision.isGrant();
    }
}
