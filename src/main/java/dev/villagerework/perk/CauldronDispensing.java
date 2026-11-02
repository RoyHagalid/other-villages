package dev.villagerework.perk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.Set;

/**
 * Dispensers fill and empty cauldrons (any cauldron, not only stations), like a player with a bucket:
 *  - water, lava or powder snow bucket into an empty cauldron fills it (water and powder snow also top up
 *    a partly filled one); the dispenser gets the empty bucket back
 *  - an empty bucket takes a full water, lava or powder snow cauldron
 *  - a water bottle adds one water level; a glass bottle takes one
 * Facing anything else, these items do exactly what they do in vanilla. Droppers are not affected.
 */
public final class CauldronDispensing {
	private CauldronDispensing() {
	}

	private static final Set<Item> HANDLED = Set.of(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.POWDER_SNOW_BUCKET,
			Items.BUCKET, Items.POTION, Items.GLASS_BOTTLE);

	/** Wraps the vanilla behavior for the items this handles; anything else is returned unchanged. */
	public static DispenseItemBehavior wrap(ItemStack stack, DispenseItemBehavior vanilla) {
		if (!HANDLED.contains(stack.getItem())) return vanilla;
		return (source, item) -> {
			Action action = action(source, item);
			return action != null ? new Fill(action).dispense(source, item) : vanilla.dispense(source, item);
		};
	}

	/** What happens to the cauldron in front: its new state, what the dispenser gets back, and the sound. */
	private record Action(BlockPos pos, BlockState newState, ItemStack remainder, SoundEvent sound) {
	}

	private static Action action(BlockSource source, ItemStack stack) {
		ServerLevel level = source.level();
		BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
		BlockState state = level.getBlockState(pos);
		boolean empty = state.is(Blocks.CAULDRON);
		int water = state.is(Blocks.WATER_CAULDRON) ? state.getValue(LayeredCauldronBlock.LEVEL) : 0;
		int snow = state.is(Blocks.POWDER_SNOW_CAULDRON) ? state.getValue(LayeredCauldronBlock.LEVEL) : 0;
		int full = LayeredCauldronBlock.MAX_FILL_LEVEL;

		if (stack.is(Items.WATER_BUCKET) && (empty || (water > 0 && water < full))) {
			return new Action(pos, layered(Blocks.WATER_CAULDRON, full), new ItemStack(Items.BUCKET), SoundEvents.BUCKET_EMPTY);
		}
		if (stack.is(Items.LAVA_BUCKET) && empty) {
			return new Action(pos, Blocks.LAVA_CAULDRON.defaultBlockState(), new ItemStack(Items.BUCKET), SoundEvents.BUCKET_EMPTY_LAVA);
		}
		if (stack.is(Items.POWDER_SNOW_BUCKET) && (empty || (snow > 0 && snow < full))) {
			return new Action(pos, layered(Blocks.POWDER_SNOW_CAULDRON, full), new ItemStack(Items.BUCKET),
					SoundEvents.BUCKET_EMPTY_POWDER_SNOW);
		}
		if (stack.is(Items.BUCKET)) {
			BlockState drained = Blocks.CAULDRON.defaultBlockState();
			if (water == full) return new Action(pos, drained, new ItemStack(Items.WATER_BUCKET), SoundEvents.BUCKET_FILL);
			if (state.is(Blocks.LAVA_CAULDRON)) return new Action(pos, drained, new ItemStack(Items.LAVA_BUCKET), SoundEvents.BUCKET_FILL_LAVA);
			if (snow == full) return new Action(pos, drained, new ItemStack(Items.POWDER_SNOW_BUCKET), SoundEvents.BUCKET_FILL_POWDER_SNOW);
			return null;
		}
		if (stack.is(Items.POTION) && isWater(stack) && (empty || (water > 0 && water < full))) {
			return new Action(pos, layered(Blocks.WATER_CAULDRON, empty ? 1 : water + 1), new ItemStack(Items.GLASS_BOTTLE),
					SoundEvents.BOTTLE_EMPTY);
		}
		if (stack.is(Items.GLASS_BOTTLE) && water > 0) {
			BlockState lower = water == 1 ? Blocks.CAULDRON.defaultBlockState() : layered(Blocks.WATER_CAULDRON, water - 1);
			return new Action(pos, lower, PotionContents.createItemStack(Items.POTION, Potions.WATER), SoundEvents.BOTTLE_FILL);
		}
		return null;
	}

	private static boolean isWater(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.is(Potions.WATER);
	}

	private static BlockState layered(net.minecraft.world.level.block.Block block, int level) {
		return block.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level);
	}

	/** Changes the cauldron, hands the dispenser its remainder (or drops it), and plays the bucket sound. */
	private static final class Fill extends DefaultDispenseItemBehavior {
		private final Action action;

		Fill(Action action) {
			this.action = action;
		}

		@Override
		protected ItemStack execute(BlockSource source, ItemStack stack) {
			ServerLevel level = source.level();
			level.setBlockAndUpdate(action.pos(), action.newState());
			level.gameEvent(null, GameEvent.BLOCK_CHANGE, action.pos());
			return consumeWithRemainder(source, stack, action.remainder().copy());
		}

		@Override
		protected void playSound(BlockSource source) {
			source.level().playSound(null, action.pos(), action.sound(), SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}
}
