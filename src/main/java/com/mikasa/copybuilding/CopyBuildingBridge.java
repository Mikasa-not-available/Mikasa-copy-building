package com.mikasa.copybuilding;

/**
 * Called from the child-of-Knot classloader after agent inject.
 * Author: Mikasa
 */
public final class CopyBuildingBridge {
	private static volatile boolean injected;

	private CopyBuildingBridge() {
	}

	public static synchronized void onInjected(String args) {
		System.out.println("[CopyBuildingAgent] injected, loader=" + CopyBuildingBridge.class.getClassLoader()
				+ " args=" + args);

		if (injected) {
			System.out.println("[CopyBuildingAgent] already injected, skipping");
			AgentInjectHandshake.report(args, "already_injected", "agent already injected");
			return;
		}

		try {
			Class<?> fabricLoaderClass = Class.forName(
					"net.fabricmc.loader.api.FabricLoader",
					true,
					CopyBuildingBridge.class.getClassLoader()
			);
			Object fabricLoader = fabricLoaderClass.getMethod("getInstance").invoke(null);
			Boolean isLoaded = (Boolean) fabricLoaderClass
					.getMethod("isModLoaded", String.class)
					.invoke(fabricLoader, CopyBuildingClient.MOD_ID);
			if (Boolean.TRUE.equals(isLoaded)) {
				System.out.println("[CopyBuildingAgent] mod already loaded via mods/, skipping agent runtime");
				injected = true;
				AgentInjectHandshake.report(args, "mods_loaded", "mod already loaded via mods/");
				return;
			}
		} catch (Throwable t) {
			System.out.println("[CopyBuildingAgent] isModLoaded check failed (continuing): " + t);
		}

		try {
			CopyBuildingRuntime.startAgentMode();
			injected = true;
			System.out.println("[CopyBuildingAgent] agent runtime started");
			AgentInjectHandshake.report(args, "started", "agent runtime started");
		} catch (Throwable t) {
			System.out.println("[CopyBuildingAgent] runtime start failed:");
			t.printStackTrace(System.out);
			AgentInjectHandshake.report(args, "failed", String.valueOf(t));
		}
	}
}
