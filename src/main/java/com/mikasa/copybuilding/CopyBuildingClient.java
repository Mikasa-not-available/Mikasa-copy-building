package com.mikasa.copybuilding;

import com.mikasa.copybuilding.command.CommandsConfig;
import com.mikasa.copybuilding.command.CopyBuildingCommands;
import com.mikasa.copybuilding.config.CopyBuildingConfig;
import com.mikasa.copybuilding.scan.ChunkScanJob;
import com.mikasa.copybuilding.selection.SelectionState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Copy That Building - client-side region export.
 * Author: Mikasa
 * Version: fabric-26.3-2.9
 */
public final class CopyBuildingClient implements ClientModInitializer {
	public static final String AUTHOR = "Mikasa";
	public static final String MOD_ID = "mikasa-copy-building";
	public static final String MOD_NAME = "Copy That Building";
	public static final String VERSION = "fabric-26.3-2.9";
	public static final String MOD_FOLDER = "Mikasa-copy-building";
	public static final String LOG_PREFIX = "[CopyThatBuilding]";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Path modConfigDir;
	private static CommandsConfig commandsConfig;
	private static CopyBuildingConfig config;
	private static SelectionState selection;
	private static ChunkScanJob scanJob;
	private static boolean commandsRegistered;
	private static boolean agentMode;

	public static void log(String message) {
		LOGGER.info("{} {}", LOG_PREFIX, message);
	}

	public static Path modConfigDir() {
		return modConfigDir;
	}

	public static Path exportsDir() {
		if (agentMode && config != null) {
			String custom = config.agentExportPath();
			if (!custom.isEmpty()) {
				return Path.of(custom).toAbsolutePath().normalize();
			}
		}
		return modConfigDir.resolve("exports");
	}

	public static boolean isAgentMode() {
		return agentMode;
	}

	public static void markAgentMode() {
		agentMode = true;
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

	public static boolean isCoreReady() {
		return scanJob != null;
	}

	/**
	 * Shared core init for mods/ entrypoint and agent inject.
	 *
	 * @return true if this call performed initialization
	 */
	public static synchronized boolean bootstrapCore() {
		if (scanJob != null) {
			return false;
		}
		modConfigDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_FOLDER);
		if (agentMode) {
			// Inject mode: in-memory defaults only — no config.json / commands.json I/O.
			commandsConfig = null;
			config = CopyBuildingConfig.agentEphemeral();
			selection = new SelectionState(config);
			scanJob = new ChunkScanJob();
			return true;
		}
		commandsConfig = CommandsConfig.load(modConfigDir);
		config = CopyBuildingConfig.load(modConfigDir);
		selection = new SelectionState(config);
		scanJob = new ChunkScanJob();
		return true;
	}

	public static synchronized void registerCommandsIfNeeded() {
		if (commandsRegistered || commandsConfig == null) {
			return;
		}
		CopyBuildingCommands.register(commandsConfig);
		commandsRegistered = true;
	}

	@Override
	public void onInitializeClient() {
		bootstrapCore();
		registerCommandsIfNeeded();
		log("starting " + MOD_NAME + " " + VERSION + " by " + AUTHOR + " (mods mode)");
	}
}
