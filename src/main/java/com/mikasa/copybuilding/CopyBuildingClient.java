package com.mikasa.copybuilding;

import com.mikasa.copybuilding.command.CommandsConfig;
import com.mikasa.copybuilding.command.CopyBuildingCommands;
import com.mikasa.copybuilding.config.CopyBuildingConfig;
import com.mikasa.copybuilding.scan.ChunkScanJob;
import com.mikasa.copybuilding.selection.SelectionState;
import com.mikasa.copybuilding.ui.ScanProgressHud;
import com.mikasa.copybuilding.ui.UnloadedChunkWaypointHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Copy That Building - client-side region export.
 * Author: Mikasa
 * Version: fabric-26.3-2.6
 */
public final class CopyBuildingClient implements ClientModInitializer {
	public static final String AUTHOR = "Mikasa";
	public static final String MOD_ID = "mikasa-copy-building";
	public static final String MOD_NAME = "Copy That Building";
	public static final String VERSION = "fabric-26.3-2.6";
	public static final String MOD_FOLDER = "Mikasa-copy-building";
	public static final String LOG_PREFIX = "[CopyThatBuilding]";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Path modConfigDir;
	private static CommandsConfig commandsConfig;
	private static CopyBuildingConfig config;
	private static SelectionState selection;
	private static ChunkScanJob scanJob;

	public static void log(String message) {
		LOGGER.info("{} {}", LOG_PREFIX, message);
	}

	public static Path modConfigDir() {
		return modConfigDir;
	}

	public static Path exportsDir() {
		return modConfigDir.resolve("exports");
	}

	public static CommandsConfig commandsConfig() {
		return commandsConfig;
	}

	public static CopyBuildingConfig config() {
		return config;
	}

	public static SelectionState selection() {
		return selection;
	}

	public static ChunkScanJob scanJob() {
		return scanJob;
	}

	@Override
	public void onInitializeClient() {
		modConfigDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_FOLDER);
		commandsConfig = CommandsConfig.load(modConfigDir);
		config = CopyBuildingConfig.load(modConfigDir);
		selection = new SelectionState(config);
		scanJob = new ChunkScanJob();

		CopyBuildingCommands.register(commandsConfig);
		ClientTickEvents.END_CLIENT_TICK.register(client -> scanJob.tick(client));
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(MOD_ID, "scan_progress"),
				ScanProgressHud::render
		);
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(MOD_ID, "unloaded_chunk_waypoint"),
				UnloadedChunkWaypointHud::render
		);

		log("starting " + MOD_NAME + " " + VERSION + " by " + AUTHOR);
	}
}
