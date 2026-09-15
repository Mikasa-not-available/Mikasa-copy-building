package com.mikasa.copybuilding.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mikasa.copybuilding.CopyBuildingClient;
import com.mikasa.copybuilding.export.ExportFormat;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Persistent UI/export settings.
 * Author: Mikasa
 */
public final class CopyBuildingConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public enum FilterMode {
		EXCLUDE,
		INCLUDE
	}

	public enum StorageMode {
		/** Keep capture in memory until Save (default). */
		RAM,
		/** Write JSON to disk as chunks are read. */
		FILE
	}

	private final Path file;
	private final Data data;

	private CopyBuildingConfig(Path file, Data data) {
		this.file = file;
		this.data = data;
	}

	public static CopyBuildingConfig load(Path modConfigDir) {
		Path file = modConfigDir.resolve("config.json");
		try {
			Files.createDirectories(modConfigDir);
			if (!Files.isRegularFile(file)) {
				CopyBuildingConfig created = new CopyBuildingConfig(file, Data.defaults());
				created.save();
				return created;
			}
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Data parsed = GSON.fromJson(reader, Data.class);
				if (parsed == null) {
					parsed = Data.defaults();
				}
				parsed.normalize();
				return new CopyBuildingConfig(file, parsed);
			}
		} catch (Exception e) {
			CopyBuildingClient.LOGGER.error("{} Failed to load config.json, using defaults", CopyBuildingClient.LOG_PREFIX, e);
			return new CopyBuildingConfig(file, Data.defaults());
		}
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			CopyBuildingClient.LOGGER.error("{} Failed to save config.json", CopyBuildingClient.LOG_PREFIX, e);
		}
	}

	public int yMin() {
		return data.yMin;
	}

	public void setYMin(int yMin) {
		data.yMin = yMin;
	}

	public int yMax() {
		return data.yMax;
	}

	public void setYMax(int yMax) {
		data.yMax = yMax;
	}

	public boolean excludeAir() {
		return data.excludeAir;
	}

	public void setExcludeAir(boolean excludeAir) {
		data.excludeAir = excludeAir;
	}

	public boolean excludeGrass() {
		return data.excludeGrass;
	}

	public void setExcludeGrass(boolean excludeGrass) {
		data.excludeGrass = excludeGrass;
	}

	public boolean excludeFlowers() {
		return data.excludeFlowers;
	}

	public void setExcludeFlowers(boolean excludeFlowers) {
		data.excludeFlowers = excludeFlowers;
	}

	public boolean excludeDirt() {
		return data.excludeDirt;
	}

	public void setExcludeDirt(boolean excludeDirt) {
		data.excludeDirt = excludeDirt;
	}

	public boolean excludeWater() {
		return data.excludeWater;
	}

	public void setExcludeWater(boolean excludeWater) {
		data.excludeWater = excludeWater;
	}

	public StorageMode storageMode() {
		return data.storageMode == null ? StorageMode.RAM : data.storageMode;
	}

	public void setStorageMode(StorageMode mode) {
		data.storageMode = mode == null ? StorageMode.RAM : mode;
	}

	public FilterMode filterMode() {
		return data.filterMode == null ? FilterMode.EXCLUDE : data.filterMode;
	}

	public void setFilterMode(FilterMode mode) {
		data.filterMode = mode == null ? FilterMode.EXCLUDE : mode;
	}

	public List<String> blockFilters() {
		if (data.blockFilters == null) {
			data.blockFilters = new ArrayList<>();
		}
		return data.blockFilters;
	}

	public void setBlockFiltersFromCsv(String csv) {
		List<String> list = new ArrayList<>();
		if (csv != null && !csv.isBlank()) {
			for (String part : csv.split(",")) {
				String id = part.trim().toLowerCase(Locale.ROOT);
				if (!id.isEmpty()) {
					list.add(id);
				}
			}
		}
		data.blockFilters = list;
	}

	public String blockFiltersCsv() {
		return String.join(", ", blockFilters());
	}

	public ExportFormat lastFormat() {
		ExportFormat parsed = ExportFormat.fromToken(data.lastFormat);
		return parsed == null ? ExportFormat.JSON : parsed;
	}

	public void setLastFormat(ExportFormat format) {
		data.lastFormat = (format == null ? ExportFormat.JSON : format).name();
	}

	public static final class Data {
		public int yMin = -64;
		public int yMax = 320;
		public boolean excludeAir = true;
		public boolean excludeGrass = false;
		public boolean excludeFlowers = false;
		public boolean excludeDirt = false;
		public boolean excludeWater = false;
		public FilterMode filterMode = FilterMode.EXCLUDE;
		public StorageMode storageMode = StorageMode.RAM;
		public List<String> blockFilters = new ArrayList<>();
		/** Stored as string so old values like SCHEMA migrate cleanly. */
		public String lastFormat = "JSON";

		public static Data defaults() {
			return new Data();
		}

		public void normalize() {
			if (blockFilters == null) {
				blockFilters = new ArrayList<>();
			}
			if (filterMode == null) {
				filterMode = FilterMode.EXCLUDE;
			}
			if (storageMode == null) {
				storageMode = StorageMode.RAM;
			}
			ExportFormat fmt = ExportFormat.fromToken(lastFormat);
			lastFormat = (fmt == null ? ExportFormat.JSON : fmt).name();
			if (yMin > yMax) {
				int t = yMin;
				yMin = yMax;
				yMax = t;
			}
		}
	}
}
