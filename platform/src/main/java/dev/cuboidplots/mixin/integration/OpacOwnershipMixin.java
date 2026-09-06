package dev.cuboidplots.mixin.integration;

import dev.cuboidplots.platform.AddonRuntime;
import net.minecraft.resources.ResourceLocation;
import xaero.pac.common.server.claims.ServerClaimsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ServerClaimsManager.class,remap=false)
public abstract class OpacOwnershipMixin {
    @Inject(method="unclaim",at=@At("HEAD"),remap=false)
    private void cuboidplots$unclaim(ResourceLocation dimension,int x,int z,CallbackInfo info) { AddonRuntime.ownershipChanged(dimension.toString(),x,z); }
}
