package dev.cuboidplots.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cuboidplots.platform.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ArmorStand.class)
public abstract class ArmorStandInteractionMixin {
    @WrapMethod(method="interactAt")
    private InteractionResult cuboidplots$interactAt(Player player,Vec3 location,InteractionHand hand,Operation<InteractionResult> original) {
        if(player instanceof ServerPlayer serverPlayer)try(ActionScope scope=ActionPlanner.entity(serverPlayer,(ArmorStand)(Object)this,false)){return original.call(player,location,hand);}
        return original.call(player,location,hand);
    }
}
