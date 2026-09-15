package com.mikasa.copybuilding.command;

import com.mikasa.copybuilding.CopyBuildingClient;
import com.mikasa.copybuilding.export.ExportFormat;
import com.mikasa.copybuilding.selection.SelectionState;
import com.mikasa.copybuilding.ui.CopyBuildingScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Registers remappable client commands.
 * Author: Mikasa
 */
public final class CopyBuildingCommands {
	private CopyBuildingCommands() {
	}

	public static void register(CommandsConfig names) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(names.root());

			root.then(ClientCommands.literal(names.openmenu()).executes(ctx -> {
				Minecraft client = Minecraft.getInstance();
				client.execute(() -> client.gui.setScreen(new CopyBuildingScreen()));
				ctx.getSource().sendFeedback(Component.literal("Opened Copy That Building menu."));
				return 1;
			}));

			root.then(ClientCommands.literal(names.setpointA()).executes(ctx -> {
				BlockPos pos = pickBlock(ctx.getSource().getPlayer());
				CopyBuildingClient.selection().setPointA(pos);
				ctx.getSource().sendFeedback(Component.literal("Point A set to " + formatPos(pos)));
				notifySelectionChanged(ctx.getSource());
				return 1;
			}));

			root.then(ClientCommands.literal(names.setpointB()).executes(ctx -> {
				BlockPos pos = pickBlock(ctx.getSource().getPlayer());
				CopyBuildingClient.selection().setPointB(pos);
				ctx.getSource().sendFeedback(Component.literal("Point B set to " + formatPos(pos)));
				notifySelectionChanged(ctx.getSource());
				return 1;
			}));

			root.then(ClientCommands.literal(names.status()).executes(ctx -> {
				ctx.getSource().sendFeedback(Component.literal(statusText()));
				return 1;
			}));

			root.then(ClientCommands.literal(names.cancel()).executes(ctx -> {
				if (!CopyBuildingClient.scanJob().isScanning()) {
					ctx.getSource().sendError(Component.literal("No active reading."));
					return 0;
				}
				CopyBuildingClient.scanJob().cancel();
				ctx.getSource().sendFeedback(Component.literal("Cancel requested (progress kept)."));
				return 1;
			}));

			root.then(ClientCommands.literal(names.stop()).executes(ctx -> {
				boolean ok = CopyBuildingClient.scanJob().stopWork();
				if (!ok) {
					ctx.getSource().sendError(Component.literal("Nothing to stop."));
					return 0;
				}
				ctx.getSource().sendFeedback(Component.literal("Stopped. Memory cleared, temp file removed."));
				return 1;
			}));

			root.then(ClientCommands.literal(names.save())
					.then(ClientCommands.literal("json").executes(ctx ->
							save(ctx.getSource(), ExportFormat.JSON)))
					.then(ClientCommands.literal("nbt").executes(ctx ->
							save(ctx.getSource(), ExportFormat.NBT)))
					.then(ClientCommands.literal("schem").executes(ctx ->
							save(ctx.getSource(), ExportFormat.SCHEM)))
					.then(ClientCommands.literal("litematic").executes(ctx ->
							save(ctx.getSource(), ExportFormat.LITEMATIC)))
			);

			dispatcher.register(root);
		});
	}

	private static void notifySelectionChanged(FabricClientCommandSource source) {
		SelectionState selection = CopyBuildingClient.selection();
		if (!selection.hasCorners()) {
			CopyBuildingClient.scanJob().onPointMissing();
			source.sendFeedback(Component.literal("Waiting for both points before reading."));
			return;
		}
		if (!CopyBuildingClient.scanJob().isTracking()) {
			CopyBuildingClient.scanJob().beginWhenBothPointsSet(selection, CopyBuildingClient.config());
			source.sendFeedback(Component.literal("Both points set — reading started."));
		} else {
			CopyBuildingClient.scanJob().updateTrackedZone(selection, CopyBuildingClient.config());
			source.sendFeedback(Component.literal(
					"Zone rebuilt — kept overlapping chunks, progress "
							+ CopyBuildingClient.scanJob().progressLabel() + "."));
		}
	}

	private static int save(FabricClientCommandSource source, ExportFormat format) {
		SelectionState selection = CopyBuildingClient.selection();
		if (!selection.hasCorners()) {
			source.sendError(Component.literal("Set point A and point B first."));
			return 0;
		}
		if (!CopyBuildingClient.scanJob().isTracking() || !CopyBuildingClient.scanJob().hasCapture()) {
			source.sendError(Component.literal("Nothing to save yet."));
			return 0;
		}
		CopyBuildingClient.config().setLastFormat(format);
		CopyBuildingClient.config().save();
		boolean ok = CopyBuildingClient.scanJob().saveNow(format);
		if (!ok) {
			source.sendError(Component.literal(
					CopyBuildingClient.scanJob().isSaving() ? "Save already in progress." : "Save failed."));
			return 0;
		}
		return 1;
	}

	private static String statusText() {
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

	private static BlockPos pickBlock(LocalPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult blockHit && blockHit.getType() != HitResult.Type.MISS) {
			return blockHit.getBlockPos();
		}
		Vec3 from = player.getEyePosition(1.0f);
		Vec3 look = player.getViewVector(1.0f);
		Vec3 to = from.add(look.scale(player.blockInteractionRange()));
		BlockHitResult ray = player.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (ray.getType() != HitResult.Type.MISS) {
			return ray.getBlockPos();
		}
		return player.blockPosition();
	}

	private static String formatPos(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}
}
