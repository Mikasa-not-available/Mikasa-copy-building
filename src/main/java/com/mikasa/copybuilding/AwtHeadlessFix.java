package com.mikasa.copybuilding;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Minecraft / Fabric often set {@code java.awt.headless=true}. Clear it so Swing can open.
 * Author: Mikasa
 */
public final class AwtHeadlessFix {
	private AwtHeadlessFix() {
	}

	/**
	 * @return true if the JVM no longer reports headless after the fix attempt
	 */
	public static boolean tryEnableGui() {
		System.setProperty("java.awt.headless", "false");
		resetCachedHeadlessFlag();
		try {
			boolean headless = GraphicsEnvironment.isHeadless();
			System.out.println("[CopyBuildingAgent] GraphicsEnvironment.isHeadless=" + headless);
			return !headless;
		} catch (Throwable t) {
			System.out.println("[CopyBuildingAgent] headless check failed: " + t);
			return false;
		}
	}

	private static void resetCachedHeadlessFlag() {
		try {
			Field defaultHeadless = GraphicsEnvironment.class.getDeclaredField("defaultHeadless");
			defaultHeadless.setAccessible(true);
			defaultHeadless.set(null, Boolean.FALSE);
		} catch (Throwable ignored) {
		}
		try {
			Field headless = GraphicsEnvironment.class.getDeclaredField("headless");
			headless.setAccessible(true);
			headless.set(null, Boolean.FALSE);
		} catch (Throwable ignored) {
		}
		// Java 9+ may use a different cache via getHeadlessProperty
		try {
			Method m = GraphicsEnvironment.class.getDeclaredMethod("getHeadlessProperty");
			m.setAccessible(true);
			// no-op invoke; property already false
		} catch (Throwable ignored) {
		}
	}
}
