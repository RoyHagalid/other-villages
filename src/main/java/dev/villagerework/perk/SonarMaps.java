package dev.villagerework.perk;

import dev.villagerework.config.VRConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cartographer, level III: sonar maps. A sonar map is an ordinary filled map with a depth: it's set where the
 * map is first used, and the map draws caves instead of the surface, in a band from {@code sonarBelow} (32) blocks
 * below that depth to {@code sonarAbove} (4) above it: it looks far down and only a little up, so standing at a
 * cliff in a big cave shows the ground below, while ledges and ceilings above you stay out.
 *
 * While held, every half second it floods out from the player through open space (air, plants, water, lava)
 * up to {@code sonarRadius} blocks (16), inside the depth band, stopping at walls. For every column the flood
 * reached it draws the topmost floor inside the band that has reached open space above it, in that block's map
 * color, shaded by depth in four tiers (light near the top of the band, darkest at the bottom); water is shaded
 * the same way, lava is always bright; walls the flood ran into are drawn as a near-black outline. Nothing behind solid rock is revealed, so it can't be used as x-ray.
 */
public final class SonarMaps {
	private SonarMaps() {
	}

	private static final String KEY = "villagerework_sonar_depth";

	public static boolean isSonar(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(KEY);
	}

	/** The Cave Map's own icon (0.17.9; items/cave_map.json). Also given to older Cave Maps when held. */
	public static void markLook(ItemStack stack) {
		if (!stack.has(DataComponents.ITEM_MODEL) || !stack.get(DataComponents.ITEM_MODEL).equals(CAVE_MAP_MODEL)) {
			stack.set(DataComponents.ITEM_MODEL, CAVE_MAP_MODEL);
		}
	}

	private static final net.minecraft.resources.Identifier CAVE_MAP_MODEL = dev.villagerework.VillageRework.id("cave_map");

	public static int depth(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(KEY, 0);
	}

	/** Turns an unused sonar map into a filled map centered on the player, locked to their depth. */
	public static ItemStack create(ServerLevel level, ServerPlayer player) {
		ItemStack map = MapItem.create(level, player.getBlockX(), player.getBlockZ(), (byte) 0, true, false);
		MapItemSavedData data = MapItem.getSavedData(map, level);
		if (data != null) fillUnexplored(data);
		int depth = player.getBlockY();
		CustomData.update(DataComponents.CUSTOM_DATA, map, tag -> tag.putInt(KEY, depth));
		markLook(map);
		map.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Cave map · depth Y " + depth)
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.DARK_AQUA)))));
		return map;
	}

	/** One sonar pass for a held sonar map (called from the map's tick, throttled here). */
	public static void update(ServerLevel level, Entity holder, MapItemSavedData data, int depth) {
		if (data.locked || data.dimension != level.dimension()) return;
		if ((level.getGameTime() + holder.getId()) % 10 != 0) return;
		fillUnexplored(data);
		VRConfig.Perks cfg = VRConfig.get().perks;
		int minY = depth - cfg.sonarBelow;
		int maxY = depth + cfg.sonarAbove;
		BlockPos start = holder.blockPosition();
		if (start.getY() < minY || start.getY() > maxY || !open(level.getBlockState(start))) return;

		int radius = cfg.sonarRadius;
		long r2 = (long) radius * radius;
		java.util.Set<Long> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
		seen.add(start.asLong());
		queue.add(start.asLong());
		Map<Long, Integer> topOpen = new HashMap<>(); // column → highest reached open y with a floor under it
		Map<Long, BlockPos> walls = new HashMap<>();  // column → a wall block the flood ran into
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int budget = 80000;
		while (!queue.isEmpty() && budget-- > 0) {
			BlockPos pos = BlockPos.of(queue.poll());
			long column = BlockPos.asLong(pos.getX(), 0, pos.getZ());
			BlockState below = level.getBlockState(cursor.set(pos.getX(), pos.getY() - 1, pos.getZ()));
			boolean floorInBand = pos.getY() - 1 >= minY && !open(below);
			if (floorInBand || liquid(level.getFluidState(pos))) {
				topOpen.merge(column, pos.getY(), Math::max);
			}
			for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}}) {
				int x = pos.getX() + d[0], y = pos.getY() + d[1], z = pos.getZ() + d[2];
				if (y < minY || y > maxY) continue;
				long dx = x - start.getX(), dz = z - start.getZ();
				if (dx * dx + dz * dz > r2) continue;
				long key = BlockPos.asLong(x, y, z);
				if (seen.contains(key)) continue;
				BlockState state = level.getBlockState(cursor.set(x, y, z));
				if (open(state)) {
					seen.add(key);
					queue.add(key);
				} else if (d[1] == 0) {
					walls.putIfAbsent(BlockPos.asLong(x, 0, z), new BlockPos(x, y, z));
				}
			}
		}

		int scale = 1 << data.scale;
		for (Map.Entry<Long, Integer> e : topOpen.entrySet()) {
			BlockPos column = BlockPos.of(e.getKey());
			int y = e.getValue();
			BlockPos at = new BlockPos(column.getX(), y, column.getZ());
			FluidState fluid = level.getFluidState(at);
			byte color;
			MapColor.Brightness tier = tier(y - 1, minY, maxY);
			if (fluid.is(net.minecraft.tags.FluidTags.LAVA)) {
				color = MapColor.FIRE.getPackedId(MapColor.Brightness.HIGH); // always bright: a warning
			} else if (fluid.is(net.minecraft.tags.FluidTags.WATER)) {
				color = MapColor.WATER.getPackedId(tier);
			} else {
				BlockPos floor = at.below();
				color = level.getBlockState(floor).getMapColor(level, floor).getPackedId(tier);
			}
			paint(data, column.getX(), column.getZ(), scale, color);
		}
		for (Map.Entry<Long, BlockPos> e : walls.entrySet()) {
			if (topOpen.containsKey(e.getKey())) continue;
			BlockPos wall = e.getValue();
			paint(data, wall.getX(), wall.getZ(), scale, MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.HIGH));
		}
	}

	/**
	 * Depth shading: the band is split into four tiers (a map color only has four brightness steps). The top
	 * quarter is drawn light, then normal, dark and darkest for the deepest quarter.
	 */
	private static MapColor.Brightness tier(int floorY, int minY, int maxY) {
		int height = Math.max(1, maxY - minY + 1);
		int fromTop = Math.max(0, Math.min(height - 1, maxY - floorY));
		return switch (fromTop * 4 / height) {
			case 0 -> MapColor.Brightness.HIGH;
			case 1 -> MapColor.Brightness.NORMAL;
			case 2 -> MapColor.Brightness.LOW;
			default -> MapColor.Brightness.LOWEST;
		};
	}

	/**
	 * Unexplored pixels get a faint lavender-grey (clay) grid instead of vanilla's beige paper: 8×8 cells in two shades (at the
	 * default scale a cell is 8 blocks across, so it doubles as a distance grid). Only empty pixels are filled.
	 */
	private static void fillUnexplored(MapItemSavedData data) {
		// 0.17.10: a faint cool lavender-grey (clay) instead of plain grey, leaning toward the purple-grey paper.
		// Map pixels can only use Minecraft's map colours; clay is the nearest to a light purple-grey.
		byte light = MapColor.CLAY.getPackedId(MapColor.Brightness.HIGH);
		byte dark = MapColor.CLAY.getPackedId(MapColor.Brightness.NORMAL);
		for (int z = 0; z < 128; z++) {
			for (int x = 0; x < 128; x++) {
				if (data.colors[x + z * 128] != 0) continue;
				data.setColor(x, z, ((x >> 3) + (z >> 3)) % 2 == 0 ? light : dark);
			}
		}
	}

	private static void paint(MapItemSavedData data, int worldX, int worldZ, int scale, byte color) {
		int px = Math.floorDiv(worldX - data.centerX, scale) + 64;
		int pz = Math.floorDiv(worldZ - data.centerZ, scale) + 64;
		if (px >= 0 && px < 128 && pz >= 0 && pz < 128) data.setColor(px, pz, color);
	}

	/** Open space the sonar can pass through: nothing to collide with (air, plants, water, lava, ...). */
	private static boolean open(BlockState state) {
		return state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	private static boolean liquid(FluidState fluid) {
		return !fluid.isEmpty() && fluid.getType() != Fluids.EMPTY;
	}
}
