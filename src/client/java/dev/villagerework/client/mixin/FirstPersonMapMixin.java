package dev.villagerework.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.villagerework.VillageRework;
import dev.villagerework.perk.SonarMaps;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Cave Map held in first person is drawn on purple-grey paper instead of the tan map paper (0.17.9). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonMapMixin {
	@Unique
	private static RenderType villagerework$caveBackground;
	@Unique
	private static boolean villagerework$drawingCaveMap;

	@Inject(method = "renderMap", at = @At("HEAD"))
	private void villagerework$which(PoseStack pose, SubmitNodeCollector collector, int light, ItemStack stack, boolean mainHand,
			FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		villagerework$drawingCaveMap = SonarMaps.isSonar(stack);
	}

	@WrapOperation(method = "renderMap", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"))
	private void villagerework$caveBackground(SubmitNodeCollector collector, PoseStack pose, RenderType type,
			SubmitNodeCollector.CustomGeometryRenderer renderer, Operation<Void> original) {
		if (villagerework$drawingCaveMap) {
			if (villagerework$caveBackground == null) {
				villagerework$caveBackground = RenderTypes.text(VillageRework.id("textures/map/cave_map_background.png"));
			}
			type = villagerework$caveBackground;
		}
		original.call(collector, pose, type, renderer);
	}
}
