package com.mikasa.copybuilding.command;

import com.mikasa.copybuilding.CopyBuildingClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.BuiltInExceptionProvider;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Own client command dispatcher — intercepts matching slash commands locally
 * (same idea as Fabric client commands: cancel send, never hit the server).
 * Author: Mikasa
 */
public final class ClientCommandBridge {
	private static final CommandDispatcher<ClientCommandSource> DISPATCHER = new CommandDispatcher<>();

	private ClientCommandBridge() {
	}

	public static CommandDispatcher<ClientCommandSource> dispatcher() {
		return DISPATCHER;
	}

	/**
	 * @param command slash already stripped (same as {@code ClientPacketListener#sendCommand})
	 * @return {@code true} if the packet/send must be cancelled (handled on client)
	 */
	public static boolean tryExecute(String command) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || command == null || command.isEmpty()) {
			return false;
		}

		ClientCommandSource source = new ClientCommandSource(client);
		try {
			DISPATCHER.execute(command, source);
			return true;
		} catch (CommandSyntaxException e) {
			if (isIgnoredException(e.getType())) {
				return false;
			}
			CopyBuildingClient.LOGGER.warn("{} Bad client command '{}'", CopyBuildingClient.LOG_PREFIX, command, e);
			source.sendError(errorMessage(e));
			return true;
		} catch (Exception e) {
			CopyBuildingClient.LOGGER.warn("{} Client command failed '{}'", CopyBuildingClient.LOG_PREFIX, command, e);
			source.sendError(Component.nullToEmpty(e.getMessage()));
			return true;
		}
	}

	/**
	 * Merge our literals into the vanilla suggestion tree so Tab-complete works in chat.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void addSuggestions(CommandDispatcher<SharedSuggestionProvider> target) {
		if (DISPATCHER.getRoot().getChildren().isEmpty()) {
			return;
		}
		Map<CommandNode, CommandNode> nodes = new HashMap<>();
		nodes.put(DISPATCHER.getRoot(), target.getRoot());
		copyChildren(DISPATCHER.getRoot(), target.getRoot(), nodes);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void copyChildren(CommandNode root, CommandNode newRoot, Map<CommandNode, CommandNode> nodes) {
		for (Object rawChild : root.getChildren()) {
			CommandNode child = (CommandNode) rawChild;
			ArgumentBuilder builder = child.createBuilder();
			builder.requires(s -> true);
			if (builder.getCommand() != null) {
				builder.executes(ctx -> 0);
			}
			if (builder.getRedirect() != null) {
				builder.redirect(nodes.get(builder.getRedirect()));
			}
			CommandNode result = builder.build();
			nodes.put(child, result);
			newRoot.addChild(result);
			if (!child.getChildren().isEmpty()) {
				copyChildren(child, result, nodes);
			}
		}
	}

	private static boolean isIgnoredException(CommandExceptionType type) {
		BuiltInExceptionProvider builtins = CommandSyntaxException.BUILT_IN_EXCEPTIONS;
		return type == builtins.dispatcherUnknownCommand() || type == builtins.dispatcherParseException();
	}

	private static Component errorMessage(CommandSyntaxException e) {
		Component message = ComponentUtils.fromMessage(e.getRawMessage());
		String context = e.getContext();
		return context != null
				? Component.translatable("command.context.parse_error", message, e.getCursor(), context)
				: message;
	}
}
