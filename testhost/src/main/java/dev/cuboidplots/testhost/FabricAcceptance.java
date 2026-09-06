package dev.cuboidplots.testhost;

public final class FabricAcceptance implements net.fabricmc.api.ModInitializer {
    public void onInitialize(){
        if(Boolean.getBoolean("cuboidplots.test"))net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server->server.execute(()->ServerAcceptance.run(server)));
    }
}
