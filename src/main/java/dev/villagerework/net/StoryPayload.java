package dev.villagerework.net;

import dev.villagerework.VillageRework;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server → client: play a signed book as a story scroll (librarian's lectern, level III). */
public record StoryPayload(String title, String author, List<Component> pages) implements CustomPacketPayload {
	public static final Type<StoryPayload> TYPE = new Type<>(VillageRework.id("story_scroll"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StoryPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, StoryPayload::title,
			ByteBufCodecs.STRING_UTF8, StoryPayload::author,
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), StoryPayload::pages,
			StoryPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
