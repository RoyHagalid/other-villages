package dev.villagerework.net;

import dev.villagerework.VillageRework;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server → client (0.17.5): which station blocks in the player's dimension look upgraded.
 * {@code positions} are packed BlockPos longs, {@code tiers} the matching tier (2 or 3; 0 = back to plain).
 * {@code reset}: forget everything known so far first (join, dimension change).
 */
public record StationLooksPayload(boolean reset, List<Long> positions, List<Integer> tiers) implements CustomPacketPayload {
	public static final Type<StationLooksPayload> TYPE = new Type<>(VillageRework.id("station_looks"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StationLooksPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, StationLooksPayload::reset,
			ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), StationLooksPayload::positions,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), StationLooksPayload::tiers,
			StationLooksPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
