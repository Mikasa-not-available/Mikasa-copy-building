package com.mikasa.copybuilding.export;

import com.google.gson.stream.JsonWriter;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Writes mikasa-copy-building JSON v1 with optional progress (streaming, background-safe).
 * Author: Mikasa
 */
public final class JsonStructureExporter {
	private JsonStructureExporter() {
	}

	public static void write(RegionCapture capture, Path out) throws Exception {
		write(capture, out, ExportProgress.noop());
	}

	public static void write(RegionCapture capture, Path out, ExportProgress progress) throws Exception {
		progress.accept(1);
		List<RegionCapture.PaletteEntry> palette = capture.palette();
		List<RegionCapture.CapturedBlock> blocks = capture.blocks();
		progress.accept(5);

		try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8);
		     JsonWriter json = new JsonWriter(bw)) {
			json.setIndent("  ");
			json.beginObject();
			json.name("format").value("mikasa-copy-building");
			json.name("version").value(1);

			json.name("origin").beginObject();
			json.name("x").value(capture.origin().getX());
			json.name("y").value(capture.origin().getY());
			json.name("z").value(capture.origin().getZ());
			json.endObject();

			json.name("size").beginObject();
			json.name("x").value(capture.sizeX());
			json.name("y").value(capture.sizeY());
			json.name("z").value(capture.sizeZ());
			json.endObject();

			json.name("palette").beginArray();
			for (int i = 0; i < palette.size(); i++) {
				RegionCapture.PaletteEntry entry = palette.get(i);
				json.beginObject();
				json.name("name").value(entry.name());
				if (!entry.properties().isEmpty()) {
					json.name("properties").beginObject();
					for (Map.Entry<String, String> prop : entry.properties().entrySet()) {
						json.name(prop.getKey()).value(prop.getValue());
					}
					json.endObject();
				}
				json.endObject();
				if ((i & 15) == 0) {
					progress.accept(5 + (int) (10.0 * (i + 1) / Math.max(1, palette.size())));
				}
			}
			json.endArray();
			progress.accept(20);

			json.name("blocks").beginArray();
			int total = Math.max(1, blocks.size());
			for (int i = 0; i < blocks.size(); i++) {
				RegionCapture.CapturedBlock block = blocks.get(i);
				json.beginObject();
				json.name("p").value(block.paletteIndex());
				json.name("x").value(block.x());
				json.name("y").value(block.y());
				json.name("z").value(block.z());
				json.endObject();
				if ((i & 1023) == 0) {
					progress.accept(20 + (int) (75.0 * (i + 1) / total));
					Thread.yield();
				}
			}
			json.endArray();
			json.endObject();
		}
		progress.accept(100);
	}
}
