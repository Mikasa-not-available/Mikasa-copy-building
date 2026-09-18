package com.mikasa.copybuilding;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * Agent hotkey: hold Ctrl, press M, then I (Mi = Mikasa).
 * Author: Mikasa
 */
public final class AgentHotkey {
	private static final long CHORD_MS = 900L;

	private boolean prevM;
	private boolean prevI;
	private boolean sawM;
	private long sawMAt;

	public boolean pollToggle(Minecraft mc) {
		boolean ctrl = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)
				|| InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
		boolean m = InputConstants.isKeyDown(InputConstants.KEY_M);
		boolean i = InputConstants.isKeyDown(InputConstants.KEY_I);

		boolean mEdge = m && !prevM;
		boolean iEdge = i && !prevI;
		prevM = m;
		prevI = i;

		if (!ctrl) {
			sawM = false;
			return false;
		}

		long now = System.currentTimeMillis();
		if (mEdge) {
			sawM = true;
			sawMAt = now;
			return false;
		}
		if (sawM && iEdge && (now - sawMAt) <= CHORD_MS) {
			sawM = false;
			return true;
		}
		if (sawM && (now - sawMAt) > CHORD_MS) {
			sawM = false;
		}
		return false;
	}
}
