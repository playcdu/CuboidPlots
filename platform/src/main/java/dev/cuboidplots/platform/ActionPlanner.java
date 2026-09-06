package dev.cuboidplots.platform;

import dev.cuboidplots.core.Action;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Conservative vanilla reference scope. Unknown/modded effects defer to the parent. */
public final class ActionPlanner {
    private static boolean inert(Block block) {
        return block==Blocks.AIR || block==Blocks.CAVE_AIR || block==Blocks.VOID_AIR || block==Blocks.STONE || block==Blocks.DIRT || block==Blocks.COBBLESTONE
            || block==Blocks.GLASS || block==Blocks.OAK_PLANKS || block==Blocks.BRICKS || block==Blocks.QUARTZ_BLOCK;
    }
    private static boolean fixture(Block block) {
        return block==Blocks.OAK_DOOR || block==Blocks.OAK_TRAPDOOR || block==Blocks.OAK_FENCE_GATE || block==Blocks.LEVER || block==Blocks.STONE_BUTTON
            || block==Blocks.CHEST || block==Blocks.BARREL || block==Blocks.RED_BED || block==Blocks.NOTE_BLOCK;
    }
    private static boolean isolated(Level level, List<BlockPos> effects, int radius) {
        // Refuse neighbor-driven redstone, falling blocks, fluids and unrecognized modded blocks.
        // This is intentionally restrictive: same-chunk parent checks cannot isolate such effects.
        for(BlockPos center:effects) for(BlockPos pos:BlockPos.betweenClosed(center.offset(-radius,-radius,-radius),center.offset(radius,radius,radius))) {
            if(effects.contains(pos)) continue;
            BlockState state=level.getBlockState(pos);
            if(!state.getFluidState().isEmpty() || !inert(state.getBlock())) return false;
        }
        return true;
    }
    private static List<BlockPos> connected(Level level, BlockPos pos) {
        List<BlockPos> result=new ArrayList<>(); result.add(pos.immutable());
        BlockState state=level.getBlockState(pos);
        if(state.getBlock() instanceof DoorBlock) result.add(state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER?pos.above():pos.below());
        if(state.getBlock() instanceof BedBlock) result.add(pos.relative(state.getValue(BedBlock.PART)==BedPart.FOOT?state.getValue(BedBlock.FACING):state.getValue(BedBlock.FACING).getOpposite()));
        if(state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE) result.add(pos.relative(ChestBlock.getConnectedDirection(state)));
        return result;
    }
    public static ActionScope breaking(ServerPlayer player, BlockPos pos) {
        var effects=connected(player.level(),pos); Block block=player.level().getBlockState(pos).getBlock();
        boolean safe=(inert(block)||block==Blocks.OAK_DOOR||block==Blocks.RED_BED) && isolated(player.level(),effects,1)
            && !player.getMainHandItem().isEnchanted();
        return new ActionScope(player,Action.BREAK,effects,effects,safe,null);
    }
    public static ActionScope useBlock(ServerPlayer player, ItemStack held, InteractionHand hand, BlockHitResult hit) {
        Level level=player.level(); BlockPos pos=hit.getBlockPos(); BlockState state=level.getBlockState(pos); Block block=state.getBlock();
        List<BlockPos> effects=connected(level,pos); Set<BlockPos> probes=new HashSet<>(effects); probes.add(pos.relative(hit.getDirection()));
        Action action; boolean safe=false;
        if(held.getItem() instanceof BlockItem item && (inert(block)||player.isSecondaryUseActive()||!fixture(block))) {
            action=Action.PLACE;
            BlockPlaceContext context=new BlockPlaceContext(player,hand,held,hit);
            BlockPos placement=context.getClickedPos(); effects=new ArrayList<>(); effects.add(placement);
            Block placed=item.getBlock();
            if(placed instanceof DoorBlock || placed instanceof DoublePlantBlock) effects.add(placement.above());
            if(placed instanceof BedBlock) effects.add(placement.relative(player.getDirection()));
            probes.addAll(effects);
            safe=(inert(placed)&&placed!=Blocks.AIR || placed==Blocks.OAK_DOOR || placed==Blocks.RED_BED)
                && inert(block) && effects.stream().allMatch(p -> level.getBlockState(p).isAir()) && isolated(level,effects,1);
        } else if(block==Blocks.CHEST || block==Blocks.BARREL) {
            action=Action.CONTAINER; safe=held.isEmpty();
            // Trapped chests, hoppers, modded containers and inventory slot restrictions are not covered.
            if(block==Blocks.CHEST) safe &= effects.stream().allMatch(p -> level.getBlockState(p).getBlock()==Blocks.CHEST);
        } else if(block==Blocks.OAK_DOOR || block==Blocks.OAK_TRAPDOOR || block==Blocks.OAK_FENCE_GATE) {
            action=Action.DOOR; safe=held.isEmpty() && isolated(level,effects,1);
        } else if(block==Blocks.STONE_BUTTON || block==Blocks.LEVER) {
            action=Action.SWITCH; safe=held.isEmpty() && isolated(level,effects,2);
        } else if(block==Blocks.NOTE_BLOCK) {
            action=Action.OTHER_BLOCK; safe=held.isEmpty() && isolated(level,effects,1);
        } else if(block==Blocks.PUMPKIN && held.getItem()==Items.SHEARS) {
            action=Action.ITEM_ON_BLOCK;
            for(Direction direction:Direction.values()) effects.add(pos.relative(direction));
            safe=!held.isEnchanted() && isolated(level,Collections.singletonList(pos),1);
        } else { action=Action.OTHER_BLOCK; }
        return new ActionScope(player,action,effects,probes,safe,null);
    }
    public static ActionScope useAir(ServerPlayer player, ItemStack held) {
        // Only food with no teleportation, projectile, fluid or remote-world effect is in scope.
        boolean safe=held.getItem()==Items.APPLE || held.getItem()==Items.BREAD || held.getItem()==Items.CARROT;
        BlockPos eye=BlockPos.containing(player.getEyePosition());
        return new ActionScope(player,Action.ITEM_IN_AIR,Collections.singletonList(eye),Arrays.asList(eye,player.blockPosition()),safe,null);
    }
    public static ActionScope entity(ServerPlayer player, Entity target, boolean damage) {
        AABB bounds=target.getBoundingBox(); List<BlockPos> effects=new ArrayList<>();
        BlockPos min=BlockPos.containing(bounds.minX,bounds.minY,bounds.minZ), max=BlockPos.containing(Math.nextDown(bounds.maxX),Math.nextDown(bounds.maxY),Math.nextDown(bounds.maxZ));
        boolean small=bounds.getXsize()<=4 && bounds.getYsize()<=4 && bounds.getZsize()<=4;
        if(small) for(BlockPos p:BlockPos.betweenClosed(min,max)) effects.add(p.immutable());
        boolean safe=small && target instanceof ArmorStand && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty() && !target.isPassenger() && !target.isVehicle();
        return new ActionScope(player,damage?Action.ENTITY_DAMAGE:Action.ENTITY_INTERACT,effects,Collections.singletonList(target.blockPosition()),safe,target);
    }
}
