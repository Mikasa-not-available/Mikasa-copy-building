package com.mikasa.copybuilding.mixin;

import com.mikasa.copybuilding.command.ClientCommandBridge;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercept slash commands on the client before they are sent to the server.
 * Author: Mikasa
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Shadow
	private CommandDispatcher<SharedSuggestionProvider> commands;

	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void mikasaCopyBuilding$sendCommand(String command, CallbackInfo ci) {
		if (ClientCommandBridge.tryExecute(command)) {
			ci.cancel();
		}
	}

	@Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
	private void mikasaCopyBuilding$sendUnattendedCommand(String command, Screen screen, CallbackInfo ci) {
		if (ClientCommandBridge.tryExecute(command)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleCommands", at = @At("RETURN"))
	private void mikasaCopyBuilding$handleCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
		ClientCommandBridge.addSuggestions(this.commands);
	}
}
