package dev.cuboidplots.mixin.integration;

import dev.cuboidplots.platform.ActionScope;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.claims.protection.*;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;

/**
 * Exact OPAC 0.30.3 entry points. Full-chunk access overriders cannot carry this action footprint.
 */
@Mixin(value = ChunkProtection.class, remap = false)
public abstract class OpacProtectionMixin {
  @Shadow @Final private ChunkProtectionExceptionSet<Item> completelyDisabledItems;
  @Shadow @Final private ChunkProtectionExceptionSet<Block> completelyDisabledBlocks;
  @Shadow @Final private ChunkProtectionExceptionSet<EntityType<?>> completelyDisabledEntities;

  @Inject(
      target =
          @Desc(
              value = "onBlockInteraction",
              args = {
                IServerData.class,
                BlockState.class,
                Entity.class,
                InteractionHand.class,
                ItemStack.class,
                ServerLevel.class,
                BlockPos.class,
                Direction.class,
                boolean.class,
                boolean.class
              },
              ret = boolean.class),
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private void cuboidplots$block(
      IServerData data,
      BlockState state,
      Entity actor,
      InteractionHand hand,
      ItemStack held,
      ServerLevel world,
      BlockPos pos,
      Direction face,
      boolean breaking,
      boolean messages,
      CallbackInfoReturnable<Boolean> result) {
    // Keep OPAC's global disabled-content rules. Only its claim decision receives an exception.
    if (state != null && completelyDisabledBlocks.contains(state.getBlock())) return;
    if (held != null && completelyDisabledItems.contains(held.getItem())) return;
    if (ActionScope.grants(actor, pos, null)) result.setReturnValue(false);
  }

  @Inject(
      target =
          @Desc(
              value = "onEntityPlaceBlock",
              args = {
                IServerData.class,
                BlockState.class,
                Entity.class,
                ServerLevel.class,
                BlockPos.class,
                IPlayerConfigOptionSpecAPI.class
              },
              ret = boolean.class),
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private void cuboidplots$place(
      IServerData data,
      BlockState state,
      Entity actor,
      ServerLevel world,
      BlockPos pos,
      IPlayerConfigOptionSpecAPI option,
      CallbackInfoReturnable<Boolean> result) {
    if (state != null && completelyDisabledItems.contains(state.getBlock().asItem())) return;
    if (ActionScope.grants(actor, pos, null)) result.setReturnValue(false);
  }

  @Inject(method = "onUseItemAt", at = @At("HEAD"), cancellable = true, remap = false)
  private void cuboidplots$itemAt(
      IServerData data,
      Entity actor,
      ServerLevel world,
      BlockPos pos,
      Direction face,
      ItemStack held,
      InteractionHand hand,
      boolean targetAllowed,
      boolean offsetAllowed,
      boolean messages,
      CallbackInfoReturnable<Boolean> result) {
    if (!completelyDisabledItems.contains(held.getItem()) && ActionScope.grants(actor, pos, null))
      result.setReturnValue(false);
  }

  @Inject(method = "onItemRightClick", at = @At("HEAD"), cancellable = true, remap = false)
  private void cuboidplots$air(
      IServerData data,
      InteractionHand hand,
      ItemStack held,
      BlockPos pos,
      LivingEntity actor,
      boolean messages,
      CallbackInfoReturnable<Boolean> result) {
    if (!completelyDisabledItems.contains(held.getItem()) && ActionScope.grants(actor, pos, null))
      result.setReturnValue(false);
  }

  @Inject(
      target =
          @Desc(
              value = "onEntityInteraction",
              args = {
                IServerData.class,
                Entity.class,
                Entity.class,
                Entity.class,
                ItemStack.class,
                InteractionHand.class,
                boolean.class,
                boolean.class,
                boolean.class
              },
              ret = boolean.class),
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private void cuboidplots$entity(
      IServerData data,
      Entity indirect,
      Entity actor,
      Entity target,
      ItemStack held,
      InteractionHand hand,
      boolean attack,
      boolean messages,
      boolean targetExceptions,
      CallbackInfoReturnable<Boolean> result) {
    if (!attack && completelyDisabledEntities.contains(target.getType())) return;
    if (indirect != null && indirect != actor) return; // No projectile/indirect actor exception.
    if (ActionScope.grants(actor, target.blockPosition(), target)) result.setReturnValue(false);
  }
}
