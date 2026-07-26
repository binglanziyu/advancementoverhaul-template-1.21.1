package com.example.advancementoverhaul;

import com.example.advancementoverhaul.client.ClientEvents;
import com.example.advancementoverhaul.command.CommandHandler;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.event.ServerEventHandler;
import com.example.advancementoverhaul.network.NetworkHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.example.advancementoverhaul.client.gui.ImageManager;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ModInfo.MOD_ID)
public class AdvancementOverhaul {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModInfo.MOD_NAME);

    public AdvancementOverhaul(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        modBus.addListener(NetworkHandler::registerPayloads);
        modBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.register(ServerEventHandler.class);
        NeoForge.EVENT_BUS.addListener(CommandHandler::registerCommands);
        NeoForge.EVENT_BUS.addListener(AdvancementOverhaul::onServerStopping);
        if (FMLEnvironment.dist.isClient()) {
            try {
                ClientEvents.init(modBus);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize client events", e);
            }
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        ServerDataStore.getInstance().init(FMLPaths.CONFIGDIR.get());
        ImageManager.init(FMLPaths.CONFIGDIR.get());
        LOGGER.info("Advancement Overhaul initialized");
    }

    private static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        ServerDataStore.getInstance().shutdown();
        LOGGER.info("Advancement Overhaul data flushed");
    }
}