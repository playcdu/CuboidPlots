package dev.cuboidplots.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cuboidplots.platform.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class PlayerEntityMixin {
  @WrapMethod(method = "interactOn")
  private InteractionResult cuboidplots$interact(
      Entity target, InteractionHand hand, Operation<InteractionResult> original) {
    if ((Object) this instanceof ServerPlayer player)
      try (ActionScope scope = ActionPlanner.entity(player, target, false)) {
        return original.call(target, hand);
      }
    return original.call(target, hand);
  }

  @WrapMethod(method = "attack")
  private void cuboidplots$attack(Entity target, Operation<Void> original) {
    if ((Object) this instanceof ServerPlayer player) {
      try (ActionScope scope = ActionPlanner.entity(player, target, true)) {
        original.call(target);
      }
    } else original.call(target);
  }
}
