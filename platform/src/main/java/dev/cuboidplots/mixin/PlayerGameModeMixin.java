package dev.cuboidplots.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cuboidplots.platform.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.*;

@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerGameModeMixin {
  @Shadow protected ServerPlayer player;

  @WrapMethod(method = "handleBlockBreakAction")
  private void cuboidplots$breakAttempt(
      BlockPos pos,
      ServerboundPlayerActionPacket.Action action,
      Direction face,
      int height,
      int sequence,
      Operation<Void> original) {
    if (SelectionTool.isTool(player.getMainHandItem())) {
      if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK)
        SelectionTool.select(player, pos, 1);
      return;
    }
    // Fabric's initial left-click protection runs before destroyBlock, so it needs the same scope.
    try (ActionScope scope = ActionPlanner.breaking(player, pos)) {
      original.call(pos, action, face, height, sequence);
    }
  }

  @WrapMethod(method = "destroyBlock")
  private boolean cuboidplots$break(BlockPos pos, Operation<Boolean> original) {
    try (ActionScope scope = ActionPlanner.breaking(player, pos)) {
      return original.call(pos);
    }
  }

  @WrapMethod(method = "useItemOn")
  private InteractionResult cuboidplots$useBlock(
      ServerPlayer player,
      Level level,
      ItemStack stack,
      InteractionHand hand,
      BlockHitResult hit,
      Operation<InteractionResult> original) {
    if (SelectionTool.isTool(stack)) {
      SelectionTool.select(player, hit.getBlockPos(), 2);
      return InteractionResult.SUCCESS;
    }
    try (ActionScope scope = ActionPlanner.useBlock(player, stack, hand, hit)) {
      return original.call(player, level, stack, hand, hit);
    }
  }

  @WrapMethod(method = "useItem")
  private InteractionResult cuboidplots$useAir(
      ServerPlayer player,
      Level level,
      ItemStack stack,
      InteractionHand hand,
      Operation<InteractionResult> original) {
    try (ActionScope scope = ActionPlanner.useAir(player, stack)) {
      return original.call(player, level, stack, hand);
    }
  }
}
