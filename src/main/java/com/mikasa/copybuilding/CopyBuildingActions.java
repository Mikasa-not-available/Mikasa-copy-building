package com.mikasa.copybuilding;

import com.mikasa.copybuilding.export.ExportFormat;
import com.mikasa.copybuilding.selection.SelectionState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Shared game-thread actions for slash commands and agent Swing UI.
 * Author: Mikasa
 */
public final class CopyBuildingActions {
	private CopyBuildingActions() {
	}

	public static String setPointA() {
		LocalPlayer player = requirePlayer();
		BlockPos pos = player.blockPosition();
		CopyBuildingClient.selection().setPointA(pos);
		return "Point A set to player " + formatPos(pos) + ". " + afterSelectionChanged();
	}

	public static String setPointB() {
		LocalPlayer player = requirePlayer();
		BlockPos pos = player.blockPosition();
		CopyBuildingClient.selection().setPointB(pos);
		return "Point B set to player " + formatPos(pos) + ". " + afterSelectionChanged();
	}

	public static String afterSelectionChanged() {
		SelectionState selection = CopyBuildingClient.selection();
		if (!selection.hasCorners()) {
			CopyBuildingClient.scanJob().onPointMissing();
			return "Waiting for both points before reading.";
		}
		if (!CopyBuildingClient.scanJob().isTracking()) {
			CopyBuildingClient.scanJob().beginWhenBothPointsSet(selection, CopyBuildingClient.config());
			return "Both points set — reading started.";
		}
		CopyBuildingClient.scanJob().updateTrackedZone(selection, CopyBuildingClient.config());
		return "Zone rebuilt — kept overlapping chunks, progress "
				+ CopyBuildingClient.scanJob().progressLabel() + ".";
	}

	public static String save(ExportFormat format) {
		SelectionState selection = CopyBuildingClient.selection();
		if (!selection.hasCorners()) {
			return "ERROR: Set point A and point B first.";
		}
		if (!CopyBuildingClient.scanJob().isTracking() || !CopyBuildingClient.scanJob().hasCapture()) {
			return "ERROR: Nothing to save yet.";
		}
		CopyBuildingClient.config().setLastFormat(format);
		CopyBuildingClient.config().save();
		boolean ok = CopyBuildingClient.scanJob().saveNow(format);
		if (!ok) {
			return CopyBuildingClient.scanJob().isSaving()
					? "ERROR: Save already in progress."
					: "ERROR: Save failed.";
		}
		return "Save started (" + format.name() + ").";
	}

	public static String cancel() {
		if (!CopyBuildingClient.scanJob().isScanning()) {
			return "ERROR: No active reading.";
		}
		CopyBuildingClient.scanJob().cancel();
		return "Cancel requested (progress kept).";
	}

	public static String stop() {
		boolean ok = CopyBuildingClient.scanJob().stopWork();
		if (!ok) {
			return "ERROR: Nothing to stop.";
		}
		return "Stopped. Memory cleared, temp file removed.";
	}

	public static String statusText() {
		SelectionState s = CopyBuildingClient.selection();
		String a = s.pointA() == null ? "unset" : formatPos(s.pointA());
		String b = s.pointB() == null ? "unset" : formatPos(s.pointB());
		String size = s.hasCorners()
				? s.size().getX() + "x" + s.size().getY() + "x" + s.size().getZ() + " (vol " + s.volume() + ")"
				: "n/a";
		return "A=" + a
				+ " | B=" + b
				+ " | Y=" + s.yMin() + ".." + s.yMax()
				+ " | size=" + size
				+ " | progress=" + CopyBuildingClient.scanJob().progressLabel()
				+ " | " + CopyBuildingClient.scanJob().sizeLabel()
				+ " | reading=" + CopyBuildingClient.scanJob().isScanning()
				+ " | saving=" + CopyBuildingClient.scanJob().isSaving();
	}

	public static String formatPos(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	private static LocalPlayer requirePlayer() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			throw new IllegalStateException("Not in a world.");
		}
		return player;
	}
}
