package com.mikasa.copybuilding.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mikasa.copybuilding.CopyBuildingClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Remappable client command literals. Action keys are stable; names are editable.
 * Author: Mikasa
 */
public final class CommandsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static final String KEY_ROOT = "root";
	public static final String KEY_OPENMENU = "openmenu";
	public static final String KEY_SETPOINT_A = "setpointA";
	public static final String KEY_SETPOINT_B = "setpointB";
	public static final String KEY_STATUS = "status";
	public static final String KEY_SAVE = "save";
	public static final String KEY_CANCEL = "cancel";
	public static final String KEY_STOP = "stop";

	private final Data data;

	private CommandsConfig(Data data) {
		this.data = data;
	}

	public static CommandsConfig load(Path modConfigDir) {
		Path file = modConfigDir.resolve("commands.json");
		try {
			Files.createDirectories(modConfigDir);
			if (!Files.isRegularFile(file)) {
				CommandsConfig created = new CommandsConfig(Data.defaults());
				created.save(file);
				CopyBuildingClient.log("Wrote default commands.json");
				return created;
			}
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Data parsed = GSON.fromJson(reader, Data.class);
				if (parsed == null) {
					parsed = Data.defaults();
				}
				parsed.applyDefaults();
				CommandsConfig cfg = new CommandsConfig(parsed);
				cfg.save(file);
				return cfg;
			}
		} catch (Exception e) {
			CopyBuildingClient.LOGGER.error("{} Failed to load commands.json, using defaults", CopyBuildingClient.LOG_PREFIX, e);
			return new CommandsConfig(Data.defaults());
		}
	}

	public void save(Path file) throws IOException {
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(data, writer);
		}
	}

	public String root() {
		return sanitize(data.root, "copybuilding");
	}

	public String openmenu() {
		return sanitize(data.openmenu, "openmenu");
	}

	public String setpointA() {
		return sanitize(data.setpointA, "setpointA");
	}

	public String setpointB() {
		return sanitize(data.setpointB, "setpointB");
	}

	public String status() {
		return sanitize(data.status, "status");
	}

	public String save() {
		return sanitize(data.save, "save");
	}

	public String cancel() {
		return sanitize(data.cancel, "cancel");
	}

	public String stop() {
		return sanitize(data.stop, "stop");
	}

	private static String sanitize(String value, String fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String trimmed = value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		if (!trimmed.matches("[a-z0-9_]+")) {
			CopyBuildingClient.log("Invalid command name '" + value + "', using '" + fallback + "'");
			return fallback;
		}
		return trimmed;
	}

	public static final class Data {
		public String root;
		public String openmenu;
		public String setpointA;
		public String setpointB;
		public String status;
		public String save;
		public String cancel;
		public String stop;

		public static Data defaults() {
			Data d = new Data();
			d.root = "copybuilding";
			d.openmenu = "openmenu";
			d.setpointA = "setpointA";
			d.setpointB = "setpointB";
			d.status = "status";
			d.save = "save";
			d.cancel = "cancel";
			d.stop = "stop";
			return d;
		}

		public void applyDefaults() {
			Data d = defaults();
			if (root == null || root.isBlank()) root = d.root;
			if (openmenu == null || openmenu.isBlank()) openmenu = d.openmenu;
			if (setpointA == null || setpointA.isBlank()) setpointA = d.setpointA;
			if (setpointB == null || setpointB.isBlank()) setpointB = d.setpointB;
			if (status == null || status.isBlank()) status = d.status;
			if (save == null || save.isBlank()) save = d.save;
			if (cancel == null || cancel.isBlank()) cancel = d.cancel;
			if (stop == null || stop.isBlank()) stop = d.stop;
		}
	}
}
