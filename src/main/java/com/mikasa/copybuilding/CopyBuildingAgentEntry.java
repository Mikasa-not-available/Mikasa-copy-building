package com.mikasa.copybuilding;

import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

/**
 * Finds Fabric Knot (Minecraft ClassLoader) and loads the bridge via a child URLClassLoader.
 * Does not patch KnotClassDelegate.
 * Author: Mikasa
 */
public final class CopyBuildingAgentEntry {
	private CopyBuildingAgentEntry() {
	}

	public static void bootstrap(Instrumentation inst, String args) {
		System.out.println("[CopyBuildingAgent] bootstrap, agent loader="
				+ CopyBuildingAgentEntry.class.getClassLoader());

		ClassLoader knot = findMinecraftClassLoader(inst);
		if (knot == null) {
			System.out.println("[CopyBuildingAgent] Minecraft ClassLoader not found (is Fabric client running?)");
			AgentInjectHandshake.report(args, "no_knot", "Minecraft ClassLoader not found");
			return;
		}
		System.out.println("[CopyBuildingAgent] Minecraft ClassLoader=" + knot);

		URL jarUrl = agentJarUrl();
		if (jarUrl == null) {
			System.out.println("[CopyBuildingAgent] cannot resolve agent JAR URL");
			AgentInjectHandshake.report(args, "failed", "cannot resolve agent JAR URL");
			return;
		}
		System.out.println("[CopyBuildingAgent] agent jar=" + jarUrl);

		try {
			URLClassLoader child = new URLClassLoader(new URL[]{jarUrl}, knot);
			Class<?> bridge = Class.forName(
					"com.mikasa.copybuilding.CopyBuildingBridge",
					true,
					child
			);
			System.out.println("[CopyBuildingAgent] bridge loader=" + bridge.getClassLoader());
			bridge.getMethod("onInjected", String.class).invoke(null, args == null ? "" : args);
		} catch (Throwable t) {
			System.out.println("[CopyBuildingAgent] bootstrap failed:");
			t.printStackTrace(System.out);
			AgentInjectHandshake.report(args, "failed", String.valueOf(t));
		}
	}

	private static ClassLoader findMinecraftClassLoader(Instrumentation inst) {
		for (Class<?> c : inst.getAllLoadedClasses()) {
			if ("net.minecraft.client.Minecraft".equals(c.getName())) {
				return c.getClassLoader();
			}
		}
		return null;
	}

	private static URL agentJarUrl() {
		try {
			CodeSource cs = CopyBuildingAgent.class.getProtectionDomain().getCodeSource();
			if (cs != null && cs.getLocation() != null) {
				return cs.getLocation();
			}
		} catch (Throwable ignored) {
		}
		return null;
	}
}
