package com.mikasa.copybuilding;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * File handshake so inject.py does not rely on Minecraft latest.log capturing System.out.
 * Author: Mikasa
 */
public final class AgentInjectHandshake {
	public static final String STATUS_FILE_NAME = "mikasa-copy-building-inject.status";

	private AgentInjectHandshake() {
	}

	public static Path statusFile() {
		return Path.of(System.getProperty("java.io.tmpdir"), STATUS_FILE_NAME);
	}

	public static String parseToken(String args) {
		if (args == null || args.isBlank()) {
			return "";
		}
		for (String part : args.split("[;,]")) {
			String p = part.trim();
			int eq = p.indexOf('=');
			if (eq > 0 && "token".equalsIgnoreCase(p.substring(0, eq).trim())) {
				return p.substring(eq + 1).trim();
			}
		}
		return "";
	}

	public static void report(String args, String status, String detail) {
		String token = parseToken(args);
		Path file = statusFile();
		String safeDetail = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
		String body = "token=" + token + "\n"
				+ "status=" + status.toLowerCase(Locale.ROOT) + "\n"
				+ "detail=" + safeDetail + "\n"
				+ "ts=" + System.currentTimeMillis() + "\n";
		try {
			Files.writeString(file, body, StandardCharsets.UTF_8);
			System.out.println("[CopyBuildingAgent] handshake status=" + status
					+ " file=" + file.toAbsolutePath());
		} catch (Throwable t) {
			System.out.println("[CopyBuildingAgent] handshake write failed: " + t);
		}
	}
}
