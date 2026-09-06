package dev.cuboidplots.fabric;

import dev.cuboidplots.platform.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class CuboidPlotsFabric implements ModInitializer {
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> AddonCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> AddonRuntime.start(server, FabricLoader.getInstance()::isModLoaded));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> AddonRuntime.stop());
        ServerTickEvents.END_SERVER_TICK.register(SelectionTool::tick);
    }
}
