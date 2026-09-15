package com.mikasa.copybuilding.export;

/**
 * Rough size estimates for HUD / status (not exact JVM heap accounting).
 * Author: Mikasa
 */
public final class CaptureSizeEstimate {
	private CaptureSizeEstimate() {
	}

	public static long estimateRamBytes(RegionCapture capture) {
		int blocks = capture.blockCount();
		int palette = capture.paletteCount();
		long paletteChars = 0L;
		for (RegionCapture.PaletteEntry entry : capture.palette()) {
			paletteChars += entry.name().length();
			for (var e : entry.properties().entrySet()) {
				paletteChars += e.getKey().length() + e.getValue().length();
			}
		}
		// Object headers + ArrayList slots + CapturedBlock fields + palette strings
		return 2048L + blocks * 48L + palette * 64L + paletteChars * 2L;
	}

	public static long estimateFileBytes(RegionCapture capture, ExportFormat format) {
		return switch (format) {
			case JSON -> estimateJsonBytes(capture);
			case NBT -> estimateNbtBytes(capture);
			case SCHEM -> estimateSchemBytes(capture);
			case LITEMATIC -> estimateLitematicBytes(capture);
		};
	}

	public static long estimateJsonBytes(RegionCapture capture) {
		long size = 220L;
		for (RegionCapture.PaletteEntry entry : capture.palette()) {
			size += 48L + entry.name().length();
			for (var e : entry.properties().entrySet()) {
				size += 16L + e.getKey().length() + e.getValue().length();
			}
		}
		size += (long) capture.blockCount() * 42L;
		return size;
	}

	public static long estimateNbtBytes(RegionCapture capture) {
		long uncompressed = 128L;
		uncompressed += (long) capture.paletteCount() * 48L;
		uncompressed += (long) capture.blockCount() * 28L;
		return Math.max(64L, (long) (uncompressed * 0.45));
	}

	public static long estimateSchemBytes(RegionCapture capture) {
		long volume = (long) capture.sizeX() * capture.sizeY() * capture.sizeZ();
		return Math.max(128L, (long) ((volume * 1.2) + capture.paletteCount() * 40L));
	}

	public static long estimateLitematicBytes(RegionCapture capture) {
		long volume = (long) capture.sizeX() * capture.sizeY() * capture.sizeZ();
		int bits = 2;
		int palette = Math.max(2, capture.paletteCount() + 1);
		while ((1 << bits) < palette) {
			bits++;
		}
		long packed = (volume * bits + 63) / 64 * 8;
		return Math.max(256L, (long) ((packed + capture.paletteCount() * 48L) * 0.5));
	}

	public static String formatBytes(long bytes) {
		if (bytes < 1024L) {
			return bytes + " B";
		}
		double kb = bytes / 1024.0;
		if (kb < 1024.0) {
			return String.format(java.util.Locale.ROOT, "%.1f KB", kb);
		}
		double mb = kb / 1024.0;
		if (mb < 1024.0) {
			return String.format(java.util.Locale.ROOT, "%.2f MB", mb);
		}
		return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0);
	}
}
