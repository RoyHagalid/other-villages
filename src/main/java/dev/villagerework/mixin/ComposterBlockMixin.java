package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.perk.PerkContext;
import dev.villagerework.perk.Perks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Farmer's composter perks.
 *  - Level II, rich compost: an item that adds no layer gets a second try.
 *  - Level III: rotten flesh and spider eyes are compostable (like vanilla's "low-medium" items),
 *    and emptying a full composter gives two bone meal.
 */
@Mixin(ComposterBlock.class)
public abstract class ComposterBlockMixin {
	/** Created on first use, so the composter's class setup doesn't touch the loot registries. */
	@Unique
	private static Compostable villagerework$flesh;

	// ---------------------------------------------------------------- level II: second try

	@Inject(method = "addLayer", at = @At("HEAD"))
	private static void villagerework$layerStart(Entity entity, BlockState state, ServerLevel level, BlockPos pos,
			Compostable compostable, CallbackInfoReturnable<BlockState> cir) {
		PerkContext.set(Perks.level(level, pos, Perks.FARMER));
	}

	@Inject(method = "addLayer", at = @At("RETURN"))
	private static void villagerework$layerEnd(Entity entity, BlockState state, ServerLevel level, BlockPos pos,
			Compostable compostable, CallbackInfoReturnable<BlockState> cir) {
		PerkContext.clear();
	}

	@WrapOperation(method = "addLayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/storage/loot/providers/number/ints/ResolvableInt;get(Lnet/minecraft/world/level/storage/loot/LootContext;I)I"))
	private static int villagerework$secondTry(ResolvableInt layers, LootContext context, int fallback,
			Operation<Integer> original) {
		int result = original.call(layers, context, fallback);
		if (result <= 0 && PerkContext.get() >= 2) result = original.call(layers, context, fallback);
		return result;
	}

	// ---------------------------------------------------------------- level III: flesh and spider eyes

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void villagerework$useStart(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		PerkContext.set(Perks.level(level, pos, Perks.FARMER));
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void villagerework$useEnd(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		PerkContext.clear();
	}

	@WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private Object villagerework$useCompostable(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
		return villagerework$compostable(stack, type, original.call(stack, type));
	}

	@Inject(method = "insertItem", at = @At("HEAD"))
	private static void villagerework$insertStart(Entity entity, BlockState state, ServerLevel level, ItemStack stack,
			BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		PerkContext.set(Perks.level(level, pos, Perks.FARMER));
	}

	@Inject(method = "insertItem", at = @At("RETURN"))
	private static void villagerework$insertEnd(Entity entity, BlockState state, ServerLevel level, ItemStack stack,
			BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		PerkContext.clear();
	}

	@WrapOperation(method = "insertItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private static Object villagerework$insertCompostable(ItemStack stack, DataComponentType<?> type,
			Operation<Object> original) {
		return villagerework$compostable(stack, type, original.call(stack, type));
	}

	@Unique
	private static Object villagerework$compostable(ItemStack stack, DataComponentType<?> type, Object vanilla) {
		if (vanilla != null || type != DataComponents.COMPOSTABLE || PerkContext.get() < 3) return vanilla;
		if (!stack.is(Items.ROTTEN_FLESH) && !stack.is(Items.SPIDER_EYE)) return null;
		if (villagerework$flesh == null) {
			villagerework$flesh = new Compostable(ResolvableInt.fromKey(ContextIntProviders.COMPOSTABLE_LOW_MEDIUM));
		}
		return villagerework$flesh;
	}

	// ---------------------------------------------------------------- level III: two bone meal

	@Inject(method = "extractProduce", at = @At("HEAD"))
	private static void villagerework$extraBoneMeal(Entity entity, BlockState state, Level level, BlockPos pos,
			CallbackInfoReturnable<BlockState> cir) {
		if (level.isClientSide() || !Perks.has(level, pos, Perks.FARMER, 3)) return;
		Vec3 at = Vec3.atLowerCornerWithOffset(pos, 0.5, 1.01, 0.5).offsetRandomXZ(level.getRandom(), 0.7F);
		ItemEntity extra = new ItemEntity(level, at.x(), at.y(), at.z(), new ItemStack(Items.BONE_MEAL));
		extra.setDefaultPickUpDelay();
		level.addFreshEntity(extra);
	}
}
