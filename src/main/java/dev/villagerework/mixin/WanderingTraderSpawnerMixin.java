package dev.villagerework.mixin;

import dev.villagerework.config.VRConfig;
import dev.villagerework.trader.TraderVisits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.12: the wandering trader comes at least every few days (see {@link TraderVisits}).
 * While {@code trader.guaranteedVisits} is on, this replaces the vanilla spawner's daily roll; the spawn
 * itself works like vanilla (near a random player, at the village meeting point if there is one,
 * with two llamas, staying for two days).
 */
@Mixin(WanderingTraderSpawner.class)
public abstract class WanderingTraderSpawnerMixin {
	@Shadow
	@Final
	private RandomSource random;

	@Unique
	private int villagerework$delay;

	@Shadow
	private BlockPos findSpawnPositionNear(LevelReader level, BlockPos center, int radius) {
		throw new AssertionError();
	}

	@Shadow
	private boolean hasEnoughSpace(LevelReader level, BlockPos pos) {
		throw new AssertionError();
	}

	@Shadow
	private void tryToSpawnLlamaFor(ServerLevel level, WanderingTrader trader, int radius) {
		throw new AssertionError();
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void villagerework$tick(ServerLevel level, boolean spawnEnemies, CallbackInfo ci) {
		if (!VRConfig.get().trader.guaranteedVisits) return;
		ci.cancel();
		if (!level.getGameRules().get(GameRules.SPAWN_WANDERING_TRADERS)) return;
		if (--villagerework$delay > 0) return;
		villagerework$delay = 1200; // once a minute, like vanilla
		TraderVisits.tick(level, random, () -> villagerework$spawn(level));
	}

	/** Vanilla's spawn without its extra 1-in-10 roll. Returns the trader, or null if none could be placed. */
	@Unique
	private WanderingTrader villagerework$spawn(ServerLevel level) {
		ServerPlayer player = level.getRandomPlayer();
		if (player == null) return null;
		BlockPos playerPos = player.blockPosition();
		BlockPos center = level.getPoiManager()
				.find(type -> type.is(PoiTypes.MEETING), pos -> true, playerPos, 48, PoiManager.Occupancy.ANY)
				.orElse(playerPos);
		BlockPos spawnPos = findSpawnPositionNear(level, center, 48);
		if (spawnPos == null || !hasEnoughSpace(level, spawnPos)) return null;
		if (level.getBiome(spawnPos).is(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)) return null;
		WanderingTrader trader = EntityTypes.WANDERING_TRADER.spawn(level, spawnPos, EntitySpawnReason.EVENT);
		if (trader == null) return null;
		for (int i = 0; i < 2; i++) tryToSpawnLlamaFor(level, trader, 4);
		trader.setDespawnDelay(48000);
		trader.setWanderTarget(center);
		trader.setHomeTo(center, 16);
		return trader;
	}
}
