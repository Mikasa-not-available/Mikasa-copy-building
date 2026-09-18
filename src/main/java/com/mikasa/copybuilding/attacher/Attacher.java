package com.mikasa.copybuilding.attacher;

import com.sun.tools.attach.VirtualMachine;

/**
 * Attach utility: load agent JAR into a running JVM by PID.
 * Author: Mikasa
 */
public final class Attacher {
	private Attacher() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: Attacher <pid> <agent-jar> [token]");
			System.exit(1);
		}
		String pid = args[0];
		String agentPath = args[1];
		String token = args.length >= 3 ? args[2].trim() : "";
		String agentArgs = token.isEmpty()
				? "injected-by-attacher"
				: "injected-by-attacher;token=" + token;
		System.out.println("[Attacher] PID=" + pid);
		System.out.println("[Attacher] agent=" + agentPath);
		if (!token.isEmpty()) {
			System.out.println("[Attacher] token=" + token);
		}

		VirtualMachine vm = VirtualMachine.attach(pid);
		try {
			vm.loadAgent(agentPath, agentArgs);
			System.out.println("[Attacher] done");
		} finally {
			vm.detach();
		}
	}
}
