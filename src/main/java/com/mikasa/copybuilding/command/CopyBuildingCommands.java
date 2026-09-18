package com.mikasa.copybuilding.command;

import com.mikasa.copybuilding.CopyBuildingActions;
import com.mikasa.copybuilding.CopyBuildingClient;
import com.mikasa.copybuilding.export.ExportFormat;
import com.mikasa.copybuilding.ui.CopyBuildingScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Registers remappable client-only slash commands (no Fabric command API).
 * Author: Mikasa
 */
public final class CopyBuildingCommands {
	private CopyBuildingCommands() {
	}

	public static void register(CommandsConfig names) {
		LiteralArgumentBuilder<ClientCommandSource> root = LiteralArgumentBuilder.literal(names.root());

		root.then(LiteralArgumentBuilder.<ClientCommandSource>literal(names.openmenu()).executes(ctx -> {
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> client.gui.setScreen(new CopyBuildingScreen()));
			ctx.getSource().sendFeedback(Component.literal("Opened Copy That Building menu."));
			return 1;
		}));

		root.then(LiteralArgumentBuilder.<ClientCommandSource>literal(names.setpointA()).executes(ctx -> {
			String msg = CopyBuildingActions.setPointA();
			ctx.getSource().sendFeedback(Component.literal(msg));
			return 1;
		}));

		root.then(LiteralArgumentBuilder.<ClientCommandSource>literal(names.setpointB()).executes(ctx -> {
			String msg = CopyBuildingActions.setPointB();
			ctx.getSource().sendFeedback(Component.literal(msg));
			return 1;
		}));

		root.then(LiteralArgumentBuilder.<ClientCommandSource>literal(names.status()).executes(ctx -> {
			ctx.getSource().sendFeedback(Component.literal(CopyBuildingActions.statusText()));
			return 1;
		}));

		root.then(LiteralArgumentBuilder.<ClientCommandSource>literal(names.cancel()).executes(ctx -> {
			String msg = CopyBuildingActions.cancel();
			if (msg.startsWith("ERROR:")) {
				ctx.getSource().sendError(Component.literal(msg.substring(7).trim()));
				return 0;
			}
			ctx.getSource().sendFeedback(Component.literal(msg));
			return 1;
		}));

		root.then(LiteralArgumentBuilder.<ClientCommandSource>literal(names.stop()).executes(ctx -> {
			String msg = CopyBuildingActions.stop();
			if (msg.startsWith("ERROR:")) {
				ctx.getSource().sendError(Component.literal(msg.substring(7).trim()));
				return 0;
			}
			ctx.getSource().sendFeedback(Component.literal(msg));
			return 1;
		}));

		root.then(LiteralArgumentBuilder.<ClientCommandSource>literal(names.save())
				.then(LiteralArgumentBuilder.<ClientCommandSource>literal("json").executes(ctx ->
						saveFeedback(ctx.getSource(), ExportFormat.JSON)))
				.then(LiteralArgumentBuilder.<ClientCommandSource>literal("nbt").executes(ctx ->
						saveFeedback(ctx.getSource(), ExportFormat.NBT)))
				.then(LiteralArgumentBuilder.<ClientCommandSource>literal("schem").executes(ctx ->
						saveFeedback(ctx.getSource(), ExportFormat.SCHEM)))
				.then(LiteralArgumentBuilder.<ClientCommandSource>literal("litematic").executes(ctx ->
						saveFeedback(ctx.getSource(), ExportFormat.LITEMATIC)))
		);

		ClientCommandBridge.dispatcher().register(root);
		CopyBuildingClient.log("client commands registered under /" + names.root());
	}

	private static int saveFeedback(ClientCommandSource source, ExportFormat format) {
		String msg = CopyBuildingActions.save(format);
		if (msg.startsWith("ERROR:")) {
			source.sendError(Component.literal(msg.substring(7).trim()));
			return 0;
		}
		source.sendFeedback(Component.literal(msg));
		return 1;
	}
}
