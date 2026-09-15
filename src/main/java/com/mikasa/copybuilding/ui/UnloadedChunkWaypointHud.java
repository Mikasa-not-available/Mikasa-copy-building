package com.mikasa.copybuilding.ui;

import com.mikasa.copybuilding.CopyBuildingClient;
import com.mikasa.copybuilding.scan.ChunkScanJob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Through-wall waypoint toward the nearest unloaded chunk still needed for scanning.
 * Shown only while looking roughly at that chunk.
 * Author: Mikasa
 */
public final class UnloadedChunkWaypointHud {
	/** Cosine threshold ~ about 75° half-angle from look vector. */
	private static final float LOOK_DOT_MIN = 0.25f;
	private static final int MARKER_COLOR = 0xFFE74C3C;
	private static final int MARKER_CORE = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFFFEE88;

	private UnloadedChunkWaypointHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
			return;
		}
		ChunkScanJob job = CopyBuildingClient.scanJob();
		if (!job.isTracking() || !job.isScanning() || job.isSaving()) {
			return;
		}

		ChunkScanJob.UnloadedChunkHint hint = job.nearestUnloadedChunkHint(mc.level, mc.player.position());
		if (hint == null) {
			return;
		}

		Vec3 target = hint.asVec3();
		if (!WorldScreenProjection.lookingToward(target, LOOK_DOT_MIN)) {
			return;
		}

		WorldScreenProjection.ScreenPoint screen = WorldScreenProjection.project(
				target,
				graphics.guiWidth(),
				graphics.guiHeight()
		);
		if (!screen.inFront()) {
			return;
		}

		int sx = Math.round(screen.x());
		int sy = Math.round(screen.y());
		int margin = 18;
		sx = Mth.clamp(sx, margin, graphics.guiWidth() - margin);
		sy = Mth.clamp(sy, margin + 40, graphics.guiHeight() - margin);

		drawMarker(graphics, sx, sy);

		String line1 = "Unload gap → chunk " + hint.chunkX() + "," + hint.chunkZ();
		String line2 = (int) Math.round(hint.distance()) + "m  go here to load";
		graphics.centeredText(mc.font, line1, sx, sy + 14, LABEL_COLOR);
		graphics.centeredText(mc.font, line2, sx, sy + 26, 0xFFCCCCCC);
	}

	private static void drawMarker(GuiGraphicsExtractor graphics, int cx, int cy) {
		// Outer diamond (through-HUD = visible through world textures)
		fillDiamond(graphics, cx, cy, 10, MARKER_COLOR);
		fillDiamond(graphics, cx, cy, 6, 0xCC000000);
		fillDiamond(graphics, cx, cy, 3, MARKER_CORE);
		// Crosshair arms
		graphics.fill(cx - 14, cy - 1, cx - 8, cy + 1, MARKER_COLOR);
		graphics.fill(cx + 8, cy - 1, cx + 14, cy + 1, MARKER_COLOR);
		graphics.fill(cx - 1, cy - 14, cx + 1, cy - 8, MARKER_COLOR);
		graphics.fill(cx - 1, cy + 8, cx + 1, cy + 14, MARKER_COLOR);
	}

	private static void fillDiamond(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = radius - Math.abs(dy);
			graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}
}
