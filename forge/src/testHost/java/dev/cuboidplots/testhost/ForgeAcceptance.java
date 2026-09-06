package dev.cuboidplots.testhost;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="cuboidplots")
public final class ForgeAcceptance {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void started(net.minecraftforge.event.server.ServerStartedEvent event){
        if(Boolean.getBoolean("cuboidplots.test"))event.getServer().execute(()->ServerAcceptance.run(event.getServer()));
    }
}
