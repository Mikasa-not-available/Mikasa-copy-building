package com.mikasa.copybuilding.ui;

import com.mikasa.copybuilding.CopyBuildingClient;
import com.mikasa.copybuilding.scan.ChunkScanJob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Top-center progress bar while both points are set (scan or save).
 * Author: Mikasa
 */
public final class ScanProgressHud {
	private static final int BAR_WIDTH = 180;
	private static final int BAR_HEIGHT = 10;
	private static final int TOP = 12;

	private ScanProgressHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
			return;
		}
		if (!CopyBuildingClient.selection().hasCorners()) {
			return;
		}
		ChunkScanJob job = CopyBuildingClient.scanJob();
		if (!job.isTracking() && !job.isSaving()) {
			return;
		}

		float ratio = Mth.clamp(job.hudPercent() / 100f, 0f, 1f);
		int screenX = graphics.guiWidth() / 2;
		int barLeft = screenX - BAR_WIDTH / 2;
		int barTop = TOP;

		int fillColor = job.isSaving() ? 0xFFE67E22 : 0xFF3498DB;

		graphics.fill(barLeft - 1, barTop - 1, barLeft + BAR_WIDTH + 1, barTop + BAR_HEIGHT + 1, 0xC0000000);
		graphics.fill(barLeft, barTop, barLeft + BAR_WIDTH, barTop + BAR_HEIGHT, 0xFF333333);
		int fill = Math.max(0, Math.round(BAR_WIDTH * ratio));
		graphics.fill(barLeft, barTop, barLeft + fill, barTop + BAR_HEIGHT, fillColor);

		graphics.centeredText(mc.font, job.progressLabel(), screenX, barTop + BAR_HEIGHT + 2, 0xFFFFFFFF);
		graphics.centeredText(mc.font, job.sizeLabel(), screenX, barTop + BAR_HEIGHT + 14, 0xFFCCCCCC);
	}
}
