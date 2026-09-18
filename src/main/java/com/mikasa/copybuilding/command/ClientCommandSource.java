package com.mikasa.copybuilding.command;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Client-only brigadier source for slash commands (never sent to the server).
 * Author: Mikasa
 */
public final class ClientCommandSource {
	private final Minecraft client;

	public ClientCommandSource(Minecraft client) {
		this.client = client;
	}

	public Minecraft getClient() {
		return client;
	}

	public LocalPlayer getPlayer() {
		return client.player;
	}

	public void sendFeedback(Component message) {
		LocalPlayer player = client.player;
		if (player != null) {
			player.sendSystemMessage(message);
		}
	}

	public void sendError(Component message) {
		sendFeedback(Component.empty().append(message).withStyle(ChatFormatting.RED));
	}
}
