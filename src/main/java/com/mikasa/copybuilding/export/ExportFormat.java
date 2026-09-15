package com.mikasa.copybuilding.export;

/**
 * Supported export formats.
 * Author: Mikasa
 */
public enum ExportFormat {
	JSON,
	NBT,
	SCHEM,
	LITEMATIC;

	public String fileExtension() {
		return switch (this) {
			case JSON -> "json";
			case NBT -> "nbt";
			case SCHEM -> "schem";
			case LITEMATIC -> "litematic";
		};
	}

	public static ExportFormat fromToken(String token) {
		if (token == null) {
			return null;
		}
		return switch (token.trim().toLowerCase()) {
			case "json" -> JSON;
			case "nbt" -> NBT;
			case "schem" -> SCHEM;
			case "litematic" -> LITEMATIC;
			default -> null;
		};
	}
}
