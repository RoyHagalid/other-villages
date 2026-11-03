package dev.villagerework.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Village prosperity (0.18): how well a village is doing, 0–100, shown as its mood.
 *
 * - Standing, worked out from what the village is: a base, its stations' levels together, and the iron golems
 *   inside its border (up to a few).
 * - Memory, from what happened lately: cures, raids beaten, deaths, golems lost, villagers hurt by players,
 *   villagers turned into zombies. Each memory starts at its full worth and fades to nothing over some in-game days.
 *
 * Abuse guards: a cure counts once per cooldown (and zombifying costs more than a cure gives), beating a raid while
 * the last raid's memory is still fresh only refreshes it, golems count up to a cap, and the same villager hurt again
 * within a short while counts once.
 *
 * The mood sets production (WorkCycle) at every level; abundance celebrates during the afternoon gathering.
 */
public final class Prosperity extends SavedData {
	public static final String[] MOODS = {"miserable", "unhappy", "neutral", "content", "abundant"};
	public static final int ABUNDANT = 4;

	/** One thing the village remembers. {@code kind} picks the gossip line; {@code days} how long it takes to fade. */
	public record Memory(String kind, double points, long time, double days) {
		static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("kind").forGetter(Memory::kind),
				Codec.DOUBLE.fieldOf("points").forGetter(Memory::points),
				Codec.LONG.fieldOf("time").forGetter(Memory::time),
				Codec.DOUBLE.fieldOf("days").forGetter(Memory::days)
		).apply(i, Memory::new));

		double now(long gameTime) {
			double age = (gameTime - time) / 24000.0;
			return age >= days ? 0 : points * (1 - age / days);
		}
	}

	/** One village's record. */
	public static final class Record {
		static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
				Memory.CODEC.listOf().optionalFieldOf("memories", List.of()).forGetter(r -> r.memories),
				Codec.LONG.optionalFieldOf("last_cure", Long.MIN_VALUE / 2).forGetter(r -> r.lastCure),
				Codec.INT.optionalFieldOf("defended", 0).forGetter(r -> r.defended),
				Codec.INT.optionalFieldOf("golems", 0).forGetter(r -> r.golems),
				Codec.INT.optionalFieldOf("last_raid", -1).forGetter(r -> r.lastRaid),
				Codec.LONG.optionalFieldOf("last_song_day", -1L).forGetter(r -> r.lastSongDay),
				Codec.BOOL.optionalFieldOf("party", true).forGetter(r -> r.party),
				Codec.LONG.optionalFieldOf("party_day", -1L).forGetter(r -> r.partyDay),
				Codec.LONG.optionalFieldOf("song_day", -1L).forGetter(r -> r.songDay)
		).apply(i, Record::new));

		final List<Memory> memories;
		long lastCure;
		int defended;
		int golems;
		int lastRaid;
		/** The last day it was decided whether the village parties (0.18.3). */
		long lastSongDay;
		/** The bell screen's party switch: fireworks and music at abundance (0.18.1). */
		boolean party;
		/** The day of the village's party, if any (0.18.3), and the day its song played. */
		long partyDay;
		long songDay;

		Record(List<Memory> memories, long lastCure, int defended, int golems, int lastRaid, long lastSongDay, boolean party,
				long partyDay, long songDay) {
			this.memories = new ArrayList<>(memories);
			this.lastCure = lastCure;
			this.defended = defended;
			this.golems = golems;
			this.lastRaid = lastRaid;
			this.lastSongDay = lastSongDay;
			this.party = party;
			this.partyDay = partyDay;
			this.songDay = songDay;
		}

		Record() {
			this(List.of(), Long.MIN_VALUE / 2, 0, 0, -1, -1, true, -1, -1);
		}
	}

	public static final Codec<Prosperity> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Record.CODEC)
			.xmap(Prosperity::new, p -> p.records);

	public static final SavedDataType<Prosperity> TYPE = new SavedDataType<>(
			VillageRework.id("prosperity"), Prosperity::new, CODEC, null);

	private final Map<UUID, Record> records;

	public Prosperity() {
		this(Map.of());
	}

	private Prosperity(Map<UUID, Record> records) {
		this.records = new HashMap<>(records);
	}

	public static Prosperity get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	private Record record(Village village) {
		return records.computeIfAbsent(village.id(), id -> new Record());
	}

	// ---------------------------------------------------------------- the score

	/** 0–100. */
	public static int score(ServerLevel level, Village village) {
		VRConfig.Prosperity cfg = VRConfig.get().prosperity;
		Record r = get(level).record(village);
		int stationLevels = 0;
		for (ProfessionStation p : village.professions().values()) if (p.active()) stationLevels += p.level();
		double standing = cfg.base + Math.min(cfg.stationMax, stationLevels * cfg.perStationLevel)
				+ Math.min(cfg.maxGolems, r.golems) * cfg.perGolem;
		long now = level.getGameTime();
		double memory = 0;
		for (Memory m : r.memories) memory += m.now(now);
		memory = Math.max(cfg.memoryMin, Math.min(cfg.memoryMax, memory));
		return (int) Math.round(Math.max(0, Math.min(100, standing + memory)));
	}

	/** 0 miserable … 4 abundant. */
	public static int mood(ServerLevel level, Village village) {
		if (!VRConfig.get().prosperity.enabled) return 2;
		int score = score(level, village);
		int[] bands = VRConfig.get().prosperity.bands;
		int mood = 0;
		while (mood < 4 && score >= bands[mood]) mood++;
		return mood;
	}

	/** The mood of the village at {@code pos}, or neutral (2) outside villages. For villager and golem behaviour. */
	public static int moodAt(ServerLevel level, BlockPos pos) {
		if (!VRConfig.get().prosperity.enabled) return 2;
		return villageAt(level, pos).map(v -> mood(level, v)).orElse(2);
	}

	/** Food a villager needs to want to breed, by the mood here (vanilla: 12). -1: not at all. */
	public static int breedFood(ServerLevel level, BlockPos pos) {
		return VRConfig.get().prosperity.breedFood[moodAt(level, pos)];
	}

	/** The mood's production multiplier (progress and goods at the evening work). */
	public static double production(ServerLevel level, Village village) {
		if (!VRConfig.get().prosperity.enabled) return 1.0;
		return VRConfig.get().prosperity.production[mood(level, village)];
	}

	/** Whether the village may party (fireworks and music at abundance). */
	public static boolean partyAllowed(ServerLevel level, Village village) {
		return get(level).record(village).party;
	}

	public static void setParty(ServerLevel level, Village village, boolean allowed) {
		Prosperity data = get(level);
		data.record(village).party = allowed;
		data.setDirty();
		if (!allowed) stopSong(level, village.bellPos());
	}

	/**
	 * Switching the party off (0.20.3) also silences a song that is already playing: players who could hear it
	 * are told to stop that disc's sound. (This also stops a Lava Chicken record in a nearby jukebox.)
	 */
	private static void stopSong(ServerLevel level, BlockPos bell) {
		var stop = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
				SoundEvents.MUSIC_DISC_LAVA_CHICKEN.value().location(), SoundSource.RECORDS);
		for (net.minecraft.server.level.ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(bell) <= 96 * 96) player.connection.send(stop);
		}
	}

	public static int defended(ServerLevel level, Village village) {
		return get(level).record(village).defended;
	}

	/** What the villagers talk about: the freshest memories that still matter, newest first, as gossip lines. */
	public static List<Component> gossip(ServerLevel level, Village village) {
		long now = level.getGameTime();
		List<Memory> fresh = new ArrayList<>();
		for (Memory m : get(level).record(village).memories) if (Math.abs(m.now(now)) >= 0.5) fresh.add(m);
		fresh.sort(Comparator.comparingLong(Memory::time).reversed());
		List<Component> out = new ArrayList<>();
		Set<String> said = new HashSet<>();
		for (Memory m : fresh) {
			if (!said.add(m.kind())) continue; // one line per kind
			out.add(Component.literal(line(m.kind())));
			if (out.size() == 4) break;
		}
		return out;
	}

	private static String line(String kind) {
		return switch (kind) {
			case "cure" -> "Someone cured a zombie villager!";
			case "raid" -> "We beat back a raid!";
			case "death" -> "We lost one of our own.";
			case "golem_death" -> "An iron golem fell.";
			case "hurt" -> "A villager was hurt.";
			case "zombified" -> "A neighbour turned into a zombie.";
			default -> kind;
		};
	}

	// ---------------------------------------------------------------- debugging (/vr prosperity)

	/** One line on how the score adds up. */
	public static String describe(ServerLevel level, Village village) {
		VRConfig.Prosperity cfg = VRConfig.get().prosperity;
		Record r = get(level).record(village);
		int stationLevels = 0;
		for (ProfessionStation p : village.professions().values()) if (p.active()) stationLevels += p.level();
		long now = level.getGameTime();
		double memory = 0;
		for (Memory m : r.memories) memory += m.now(now);
		return village.name() + ": score " + score(level, village) + " (" + MOODS[mood(level, village)] + ")"
				+ " = base " + (int) cfg.base
				+ " + stations " + Math.round(Math.min(cfg.stationMax, stationLevels * cfg.perStationLevel))
				+ " (levels " + stationLevels + ")"
				+ " + golems " + Math.round(Math.min(cfg.maxGolems, r.golems) * cfg.perGolem) + " (" + r.golems + ")"
				+ " + memory " + Math.round(Math.max(cfg.memoryMin, Math.min(cfg.memoryMax, memory)))
				+ "; raids defended " + r.defended + "; production x" + production(level, village);
	}

	/** Pretends an event happened (for testing). Returns false for an unknown kind. */
	public static boolean debugEvent(ServerLevel level, Village village, String kind) {
		VRConfig.Prosperity cfg = VRConfig.get().prosperity;
		Prosperity data = get(level);
		switch (kind) {
			case "cure" -> data.remember(village, "cure", cfg.cure, cfg.cureDays, level);
			case "raid" -> {
				data.record(village).defended++;
				data.record(village).memories.removeIf(m -> m.kind().equals("raid"));
				data.remember(village, "raid", cfg.raidDefended, cfg.raidDays, level);
			}
			case "death" -> data.remember(village, "death", cfg.villagerDeath, cfg.villagerDeathDays, level);
			case "golem_death" -> data.remember(village, "golem_death", cfg.golemDeath, cfg.golemDeathDays, level);
			case "hurt" -> data.remember(village, "hurt", cfg.villagerHurt, cfg.villagerHurtDays, level);
			case "zombified" -> data.remember(village, "zombified", cfg.zombified, cfg.zombifiedDays, level);
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Forgets everything the village remembers (not its raid count). */
	public static void clearMemories(ServerLevel level, Village village) {
		get(level).record(village).memories.clear();
		get(level).setDirty();
	}

	// ---------------------------------------------------------------- remembering

	private void remember(Village village, String kind, double points, double days, ServerLevel level) {
		if (points == 0) return;
		Record r = record(village);
		long now = level.getGameTime();
		r.memories.removeIf(m -> m.now(now) == 0 && now - m.time() > 24000L);
		r.memories.add(new Memory(kind, points, now, days));
		setDirty();
		dev.villagerework.perk.StationLooks.changed();
	}

	private static Optional<Village> villageAt(ServerLevel level, BlockPos pos) {
		return VillageRegistry.get(level).containing(pos, VRConfig.get().village.radius).filter(v -> !v.dormant());
	}

	/** Villager deaths are counted a tick later, so a villager turned into a zombie counts once (as zombified). */
	private record PendingDeath(ServerLevel level, BlockPos pos, UUID id) {
	}

	private static final List<PendingDeath> PENDING = new ArrayList<>();
	private static final Set<UUID> CONVERTED = new HashSet<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level) || !VRConfig.get().prosperity.enabled) return;
			if (entity instanceof Villager) {
				PENDING.add(new PendingDeath(level, entity.blockPosition(), entity.getUUID()));
			} else if (entity instanceof IronGolem) {
				VRConfig.Prosperity cfg = VRConfig.get().prosperity;
				villageAt(level, entity.blockPosition()).ifPresent(v ->
						get(level).remember(v, "golem_death", cfg.golemDeath, cfg.golemDeathDays, level));
			}
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) -> {
			if (!(previous.level() instanceof ServerLevel level) || !VRConfig.get().prosperity.enabled) return;
			VRConfig.Prosperity cfg = VRConfig.get().prosperity;
			if (previous instanceof Villager && converted instanceof ZombieVillager) {
				CONVERTED.add(previous.getUUID());
				villageAt(level, previous.blockPosition()).ifPresent(v ->
						get(level).remember(v, "zombified", cfg.zombified, cfg.zombifiedDays, level));
			} else if (previous instanceof ZombieVillager && converted instanceof Villager) {
				villageAt(level, previous.blockPosition()).ifPresent(v -> {
					Prosperity data = get(level);
					Record r = data.record(v);
					long now = level.getGameTime();
					if (now - r.lastCure < (long) (cfg.cureCooldownDays * 24000)) return; // cure loops gain nothing
					r.lastCure = now;
					data.remember(v, "cure", cfg.cure, cfg.cureDays, level);
				});
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
			if (!(entity instanceof Villager) || !(entity.level() instanceof ServerLevel level)) return;
			if (taken <= 0 || !(source.getEntity() instanceof Player) || !VRConfig.get().prosperity.enabled) return;
			VRConfig.Prosperity cfg = VRConfig.get().prosperity;
			long now = level.getGameTime();
			Long last = LAST_HURT.get(entity.getUUID());
			if (last != null && now - last < cfg.hurtCooldownSeconds * 20L) return;
			LAST_HURT.put(entity.getUUID(), now);
			villageAt(level, entity.blockPosition()).ifPresent(v ->
					get(level).remember(v, "hurt", cfg.villagerHurt, cfg.villagerHurtDays, level));
		});
		ServerTickEvents.END_LEVEL_TICK.register(Prosperity::tick);
	}

	// ---------------------------------------------------------------- every second

	private static void tick(ServerLevel level) {
		if (!PENDING.isEmpty()) {
			VRConfig.Prosperity cfg = VRConfig.get().prosperity;
			for (PendingDeath d : new ArrayList<>(PENDING)) {
				if (d.level() != level) continue;
				PENDING.remove(d);
				if (CONVERTED.remove(d.id())) continue; // counted as zombified instead
				villageAt(level, d.pos()).ifPresent(v ->
						get(level).remember(v, "death", cfg.villagerDeath, cfg.villagerDeathDays, level));
			}
		}
		if (level.getGameTime() % 5 == 0 && !PARTYING.isEmpty()) dance(level);
		if (level.getGameTime() % 20 != 0) return;
		CONVERTED.clear();
		if (level.getGameTime() % 1200 == 0) LAST_HURT.clear();
		if (!VRConfig.get().prosperity.enabled) return;
		Prosperity data = get(level);
		for (Village village : VillageRegistry.get(level).all()) {
			if (village.dormant() || !level.isLoaded(village.bellPos())) continue;
			checkRaid(level, data, village);
			if (level.getGameTime() % 200 == 0) countGolems(level, data, village);
			celebrate(level, data, village);
		}
	}

	/** A raid near the bell that ended in victory counts once: "defended", and a fresh memory (not stacked). */
	private static void checkRaid(ServerLevel level, Prosperity data, Village village) {
		int radius = VRConfig.get().village.radius;
		Raid raid = level.getRaids().getNearbyRaid(village.bellPos(), radius * radius);
		if (raid == null || !raid.isVictory()) return;
		int id = level.getRaids().getId(raid).orElse(-1);
		Record r = data.record(village);
		if (id < 0 || id == r.lastRaid) return;
		r.lastRaid = id;
		r.defended++;
		VRConfig.Prosperity cfg = VRConfig.get().prosperity;
		// Not stacking: an older raid memory that's still fresh is replaced by the new one.
		r.memories.removeIf(m -> m.kind().equals("raid"));
		data.remember(village, "raid", cfg.raidDefended, cfg.raidDays, level);
	}

	private static void countGolems(ServerLevel level, Prosperity data, Village village) {
		int radius = VRConfig.get().village.radius;
		int golems = level.getEntitiesOfClass(IronGolem.class, VillageManager.column(level, village.bellPos(), radius),
				g -> g.isAlive() && village.contains(g.blockPosition(), radius)).size();
		Record r = data.record(village);
		if (r.golems != golems) {
			r.golems = golems;
			data.setDirty();
		}
	}

	/** Villages partying right now (refreshed every second), for the dance that runs every 5 ticks. */
	private static final Set<UUID> PARTYING = new HashSet<>();
	/** The day each village last rang its bell for the gathering (not saved: a missed chime doesn't matter). */
	private static final Map<UUID, Long> CHIMED = new HashMap<>();

	/**
	 * The mood, out loud (0.18.3):
	 * - miserable / unhappy: angry clouds now and then, and at the gathering grumbling ("no" sounds, more clouds);
	 * - content / abundant: the bell chimes when the gathering starts, golems hand out poppies more often (see
	 *   OfferFlowerGoalMixin), children get bursts of energy while playing;
	 * - abundant: happy particles, and on about a third of days (partyChancePerDay) a party at the gathering:
	 *   villagers dance (jump, cheer, set off fireworks) and the bell plays Lava Chicken. The bell screen's party
	 *   switch and the config can stop parties.
	 */
	private static void celebrate(ServerLevel level, Prosperity data, Village village) {
		BlockPos bell = village.bellPos();
		PARTYING.remove(village.id());
		if (level.getNearestPlayer(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5, 64, false) == null) return;
		int mood = mood(level, village);
		RandomSource random = level.getRandom();
		long timeOfDay = Math.floorMod(level.getOverworldClockTime(), 24000L);
		boolean gathering = timeOfDay >= 9000 && timeOfDay < 11000;
		boolean daytime = timeOfDay < 12000;
		VRConfig.Prosperity cfg = VRConfig.get().prosperity;
		long today = WorkCycle.today(level);

		if (mood <= 1) {
			// Discontent: clouds now and then; at the gathering they grumble at the bell.
			int chance = gathering ? (mood == 0 ? 2 : 3) : (mood == 0 ? 4 : 8);
			if (random.nextInt(chance) != 0) return;
			randomResident(level, village, random).ifPresent(v -> {
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, v.getX(), v.getEyeY() + 0.4, v.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
				if (gathering) level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.9F);
			});
			return;
		}
		if (mood < 3) return;

		// Content and up: the bell rings three times at the gathering (BellRings, 0.20.0).
		// Children with energy to spare: short bursts of speed while they play.
		if (daytime && random.nextInt(mood == ABUNDANT ? 3 : 5) == 0) {
			randomResident(level, village, random).filter(Villager::isBaby).ifPresent(kid -> kid.addEffect(
					new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 60, 0, false, false)));
		}
		if (mood < ABUNDANT) return;

		if (random.nextInt(6) == 0) {
			randomResident(level, village, random).ifPresent(v ->
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getEyeY() + 0.4, v.getZ(), 3, 0.3, 0.2, 0.3, 0.0));
		}
		// Is today a party day? Decided once a day, the first time the village is abundant.
		Record r = data.record(village);
		if (r.lastSongDay != today) {
			r.lastSongDay = today;
			if (random.nextDouble() < cfg.partyChancePerDay) r.partyDay = today;
			data.setDirty();
		}
		if (!gathering || !r.party || !cfg.parties || r.partyDay != today) return;
		PARTYING.add(village.id());
		if (cfg.song && r.songDay != today) {
			r.songDay = today;
			data.setDirty();
			level.playSound(null, bell, SoundEvents.MUSIC_DISC_LAVA_CHICKEN.value(), SoundSource.RECORDS, 4.0F, 1.0F);
		}
	}

	/** The party dance, like vanilla's after a won raid: residents near the bell jump, cheer and set off fireworks. */
	private static void dance(ServerLevel level) {
		VRConfig.Prosperity cfg = VRConfig.get().prosperity;
		RandomSource random = level.getRandom();
		for (Village village : VillageRegistry.get(level).all()) {
			if (!PARTYING.contains(village.id()) || !level.isLoaded(village.bellPos())) continue;
			BlockPos bell = village.bellPos();
			for (Villager v : VillageManager.villagersInside(level, village)) {
				if (v.isSleeping() || v.blockPosition().distSqr(bell) > 16 * 16) continue;
				if (v.onGround() && random.nextInt(4) == 0) v.getJumpControl().jump();
				if (random.nextInt(25) == 0) level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
				if (cfg.fireworks && random.nextInt(50) == 0 && level.canSeeSky(v.blockPosition())) launchFirework(level, v, random);
			}
		}
	}

	private static Optional<Villager> randomResident(ServerLevel level, Village village, RandomSource random) {
		List<Villager> residents = VillageManager.villagersInside(level, village);
		return residents.isEmpty() ? Optional.empty() : Optional.of(residents.get(random.nextInt(residents.size())));
	}

	private static final int[] COLORS = {0xFFD83E, 0x17DD62, 0xF9BD23, 0x82F6AD, 0xFFFFFF, 0xE84E3C, 0x8D6ACC};

	private static void launchFirework(ServerLevel level, Villager from, RandomSource random) {
		FireworkExplosion.Shape[] shapes = {FireworkExplosion.Shape.SMALL_BALL, FireworkExplosion.Shape.LARGE_BALL,
				FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST};
		FireworkExplosion explosion = new FireworkExplosion(shapes[random.nextInt(shapes.length)],
				new IntArrayList(new int[] {COLORS[random.nextInt(COLORS.length)], COLORS[random.nextInt(COLORS.length)]}),
				new IntArrayList(new int[] {COLORS[random.nextInt(COLORS.length)]}), random.nextBoolean(), random.nextBoolean());
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + random.nextInt(2), List.of(explosion)));
		Entity firework = new FireworkRocketEntity(level, from, from.getX(), from.getEyeY(), from.getZ(), rocket);
		level.addFreshEntity(firework);
	}

	/** Forgets the in-memory bits when the server stops (singleplayer: the next world starts clean). */
	public static void reset() {
		PENDING.clear();
		CONVERTED.clear();
		LAST_HURT.clear();
		PARTYING.clear();
		CHIMED.clear();
	}
}
