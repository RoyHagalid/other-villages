package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.perk.PerkContext;
import dev.villagerework.perk.SonarMaps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A held sonar map draws caves at its depth instead of the surface (see SonarMaps). */
@Mixin(MapItem.class)
public abstract class MapItemMixin {
	@Inject(method = "inventoryTick", at = @At("HEAD"))
	private void villagerework$tickStart(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
		PerkContext.setSubject(stack);
		// Cave Maps made before 0.17.9 get their own icon the first time they're carried.
		if (SonarMaps.isSonar(stack)) SonarMaps.markLook(stack);
	}

	@Inject(method = "inventoryTick", at = @At("RETURN"))
	private void villagerework$tickEnd(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
		PerkContext.clear();
	}

	@WrapOperation(method = "inventoryTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/MapItem;update(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V"))
	private void villagerework$sonar(MapItem self, Level level, Entity holder, MapItemSavedData data, Operation<Void> original) {
		if (PerkContext.subject() instanceof ItemStack stack && SonarMaps.isSonar(stack) && level instanceof ServerLevel server) {
			SonarMaps.update(server, holder, data, SonarMaps.depth(stack));
			return;
		}
		original.call(self, level, holder, data);
	}
}
