package dev.villagerework.perk;

import dev.villagerework.config.VRConfig;
import dev.villagerework.village.ProfessionStation;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Leatherworker's cauldron: items thrown into its water are soaked.
 *  - Level II: dirt, coarse dirt and rooted dirt become mud.
 *  - Level III: also concrete powder becomes concrete (any color).
 *
 * Water: a full cauldron (3 levels) soaks {@code perks.itemsPerFullCauldron} items (64), so one level is
 * a third of that. The station remembers how much of the top level is used, so small throws add up
 * correctly. If there isn't enough water for the whole stack, only part of it converts; the rest stays
 * as it was and drops out as its own stack. Counted in thirds of an item so one level is exact.
 */
public final class CauldronPerks {
	private CauldronPerks() {
	}

	private static final List<Item> DIRT = List.of(Items.DIRT, Items.COARSE_DIRT, Items.ROOTED_DIRT);
	private static final Map<Item, Item> CONCRETE = new HashMap<>();

	static {
		List<Item> powder = Items.CONCRETE_POWDER.asList();
		List<Item> concrete = Items.CONCRETE.asList();
		for (int i = 0; i < Math.min(powder.size(), concrete.size()); i++) CONCRETE.put(powder.get(i), concrete.get(i));
	}

	/** Called for every item entity inside a water cauldron. */
	public static void soak(ServerLevel level, BlockPos pos, BlockState state, ItemEntity entity) {
		if (!state.is(Blocks.WATER_CAULDRON) || !entity.isAlive()) return;
		ItemStack stack = entity.getItem();
		if (stack.isEmpty()) return;
		boolean dirt = DIRT.contains(stack.getItem());
		if (!dirt && !CONCRETE.containsKey(stack.getItem())) return;

		int perk = Perks.level(level, pos, Perks.LEATHERWORKER);
		Item result = dirt ? (perk >= 2 ? Items.MUD : null) : (perk >= 3 ? CONCRETE.get(stack.getItem()) : null);
		if (result == null) return;

		// The water budget is kept on the village station, or on the take-home block itself.
		VillageRegistry registry = VillageRegistry.get(level);
		Optional<Village> village = registry.byStation(pos);
		int used = village.isPresent()
				? village.get().profession(Perks.LEATHERWORKER).map(ProfessionStation::soakUsed).orElse(0)
				: TakeHomes.soakUsed(level, pos);

		// Units are thirds of an item: one water level = itemsPerFullCauldron units, one item = 3 units.
		int perLevel = Math.max(1, VRConfig.get().perks.itemsPerFullCauldron);
		int waterLevels = state.getValue(LayeredCauldronBlock.LEVEL);
		used = Math.min(used, perLevel - 1);
		int available = waterLevels * perLevel - used;
		int count = Math.min(stack.getCount(), available / 3);
		if (count <= 0) return;

		// Convert: the soaked part becomes the new item; anything left over drops out as its own stack.
		ItemStack rest = stack.copyWithCount(stack.getCount() - count);
		entity.setItem(new ItemStack(result, count));
		if (!rest.isEmpty()) {
			ItemEntity leftover = new ItemEntity(level, entity.getX(), entity.getY() + 0.2, entity.getZ(), rest);
			leftover.setDefaultPickUpDelay();
			level.addFreshEntity(leftover);
		}

		// Drain the water that was used.
		int spent = used + count * 3;
		int drained = spent / perLevel;
		int remainder = spent % perLevel;
		BlockState current = state;
		for (int i = 0; i < drained && current.is(Blocks.WATER_CAULDRON); i++) {
			LayeredCauldronBlock.lowerFillLevel(current, level, pos);
			current = level.getBlockState(pos);
		}
		if (!current.is(Blocks.WATER_CAULDRON)) remainder = 0; // emptied
		if (village.isPresent()) registry.setSoakUsed(village.get(), Perks.LEATHERWORKER, remainder);
		else TakeHomes.setSoakUsed(level, pos, remainder);

		level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.0F);
		level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
				8, 0.25, 0.05, 0.25, 0.0);
	}
}
