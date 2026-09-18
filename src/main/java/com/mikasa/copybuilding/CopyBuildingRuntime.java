package com.mikasa.copybuilding;

import com.mikasa.copybuilding.ui.AgentControlFrame;
import com.mikasa.copybuilding.ui.CopyBuildingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent-mode runtime: core bootstrap, tick pump, Ctrl+M+I menu toggle (starts hidden).
 * Author: Mikasa
 */
public final class CopyBuildingRuntime {
	private static final AtomicBoolean STARTED = new AtomicBoolean(false);
	private static ScheduledExecutorService tickScheduler;
	private static final AgentHotkey HOTKEY = new AgentHotkey();
	private static volatile AgentControlFrame swingFrame;
	private static volatile boolean preferSwing;

	private CopyBuildingRuntime() {
	}

	public static void startAgentMode() {
		if (!STARTED.compareAndSet(false, true)) {
			System.out.println("[CopyBuildingAgent] runtime already started");
			return;
		}

		CopyBuildingClient.markAgentMode();
		boolean fresh = CopyBuildingClient.bootstrapCore();
		CopyBuildingClient.log("agent mode " + CopyBuildingClient.VERSION
				+ " by " + CopyBuildingClient.AUTHOR
				+ (fresh ? " (core initialized)" : " (core already present)"));

		preferSwing = AwtHeadlessFix.tryEnableGui();
		startTickPump();

		System.out.println("[CopyBuildingAgent] ready — menu hidden. Toggle with Ctrl+M+I"
				+ (preferSwing ? " (Swing)" : " (in-game menu)"));
		notifyOnce("Agent ready. Press Ctrl+M+I to open/close menu.");
	}

	public static void toggleMenu() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}
		if (isMenuVisible(mc)) {
			hideMenu(mc);
		} else {
			showMenu(mc);
		}
	}

	private static boolean isMenuVisible(Minecraft mc) {
		if (preferSwing && swingFrame != null && swingFrame.isDisplayable() && swingFrame.isVisible()) {
			return true;
		}
		return mc.gui.screen() instanceof CopyBuildingScreen;
	}

	private static void showMenu(Minecraft mc) {
		if (preferSwing) {
			SwingUtilities.invokeLater(() -> {
				try {
					if (swingFrame == null || !swingFrame.isDisplayable()) {
						swingFrame = new AgentControlFrame();
					}
					swingFrame.setVisible(true);
					swingFrame.toFront();
					System.out.println("[CopyBuildingAgent] Swing menu shown");
				} catch (Throwable t) {
					System.out.println("[CopyBuildingAgent] Swing failed, using in-game menu:");
					t.printStackTrace(System.out);
					preferSwing = false;
					mc.execute(() -> openInGameMenu(mc, false));
				}
			});
			return;
		}
		mc.execute(() -> openInGameMenu(mc, false));
	}

	private static void hideMenu(Minecraft mc) {
		if (preferSwing && swingFrame != null) {
			SwingUtilities.invokeLater(() -> {
				if (swingFrame != null) {
					swingFrame.setVisible(false);
					System.out.println("[CopyBuildingAgent] Swing menu hidden");
				}
			});
		}
		mc.execute(() -> {
			if (mc.gui.screen() instanceof CopyBuildingScreen) {
				mc.gui.setScreen(null);
				System.out.println("[CopyBuildingAgent] in-game menu hidden");
			}
		});
	}

	private static void openInGameMenu(Minecraft mc, boolean announce) {
		try {
			mc.gui.setScreen(new CopyBuildingScreen());
			if (announce && mc.player != null) {
				mc.player.sendSystemMessage(Component.literal(
						"[CopyThatBuilding] Menu opened (Ctrl+M+I to close)."));
			}
			System.out.println("[CopyBuildingAgent] in-game menu shown");
		} catch (Throwable t) {
			System.out.println("[CopyBuildingAgent] in-game menu failed:");
			t.printStackTrace(System.out);
		}
	}

	private static void notifyOnce(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}
		mc.execute(() -> {
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.literal("[CopyThatBuilding] " + text));
			}
		});
	}

	private static void startTickPump() {
		tickScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "copybuilding-agent-tick");
			t.setDaemon(true);
			return t;
		});
		tickScheduler.scheduleAtFixedRate(() -> {
			try {
				Minecraft mc = Minecraft.getInstance();
				if (mc == null || !CopyBuildingClient.isCoreReady()) {
					return;
				}
				mc.execute(() -> {
					try {
						if (HOTKEY.pollToggle(mc)) {
							toggleMenu();
						}
						CopyBuildingClient.scanJob().tick(mc);
					} catch (Throwable t) {
						CopyBuildingClient.LOGGER.warn("{} agent tick failed", CopyBuildingClient.LOG_PREFIX, t);
					}
				});
			} catch (Throwable ignored) {
			}
		}, 50, 50, TimeUnit.MILLISECONDS);
	}
}
