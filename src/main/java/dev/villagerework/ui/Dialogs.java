package dev.villagerework.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** Shared building blocks for the mod's panels (vanilla server-driven dialogs). */
final class Dialogs {
	static final int TEXT_WIDTH = 260;
	static final int BUTTON_WIDTH = 130;

	private Dialogs() {
	}

	static void show(ServerPlayer player, Component title, List<DialogBody> body, List<ActionButton> buttons) {
		CommonDialogData common = new CommonDialogData(
				title,
				Optional.empty(),   // external title
				true,               // can close with Escape
				false,              // don't pause singleplayer
				DialogAction.CLOSE, // close after a button is pressed
				body,
				List.of()           // no input fields
		);
		ActionButton close = new ActionButton(
				new CommonButtonData(Component.literal("Close"), Optional.empty(), BUTTON_WIDTH), Optional.empty());
		Dialog dialog = new MultiActionDialog(common, buttons, Optional.of(close), 2);
		player.openDialog(Holder.direct(dialog));
	}

	static ActionButton button(String label, String tooltip, String command) {
		return new ActionButton(
				new CommonButtonData(Component.literal(label), Optional.of(Component.literal(tooltip)), BUTTON_WIDTH),
				Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
	}

	static DialogBody text(String text, ChatFormatting color) {
		return new PlainMessage(Component.literal(text).withStyle(color), TEXT_WIDTH);
	}

	static DialogBody line(String label, String value) {
		MutableComponent c = Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
		return new PlainMessage(c, TEXT_WIDTH);
	}

	static DialogBody component(Component c) {
		return new PlainMessage(c, TEXT_WIDTH);
	}

	static DialogBody requirement(boolean met, String text) {
		MutableComponent c = Component.literal(met ? "✔ " : "✘ ")
				.withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED)
				.append(Component.literal(text).withStyle(met ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		return new PlainMessage(c, TEXT_WIDTH);
	}

	static String coords(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}
}
