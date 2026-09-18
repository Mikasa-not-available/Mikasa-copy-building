package com.mikasa.copybuilding;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent entry (Attach API / -javaagent).
 * Author: Mikasa
 */
public final class CopyBuildingAgent {
	private CopyBuildingAgent() {
	}

	public static void agentmain(String args, Instrumentation inst) {
		System.out.println("[CopyBuildingAgent] agentmain args=" + args);
		try {
			AwtHeadlessFix.tryEnableGui();
			CopyBuildingAgentEntry.bootstrap(inst, args);
		} catch (Throwable t) {
			System.out.println("[CopyBuildingAgent] agentmain failed:");
			t.printStackTrace(System.out);
			AgentInjectHandshake.report(args, "failed", String.valueOf(t));
		}
	}

	public static void premain(String args, Instrumentation inst) {
		agentmain(args, inst);
	}
}
