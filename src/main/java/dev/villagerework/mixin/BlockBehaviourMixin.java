package dev.villagerework.mixin;

import dev.villagerework.perk.LecternMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lecterns notice redstone signals (vanilla lecterns only send them). Used for the librarian's messages. */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
	@Inject(method = "neighborChanged", at = @At("HEAD"))
	private void villagerework$lecternSignal(BlockState state, Level level, BlockPos pos, Block neighbor,
			Orientation orientation, boolean moved, CallbackInfo ci) {
		if ((Object) this instanceof LecternBlock && level instanceof ServerLevel server) {
			LecternMessages.onNeighborChanged(server, pos);
		}
	}

	/**
	 * A take-home workstation drops itself with its tier (0.17.1), whether broken by a player or an explosion.
	 * Village stations still drop a plain block.
	 */
	@Inject(method = "getDrops", at = @At("RETURN"), cancellable = true)
	private void villagerework$takeHomeDrop(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.List<net.minecraft.world.item.ItemStack>> cir) {
		net.minecraft.world.phys.Vec3 origin = params.getOptionalParameter(
				net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
		if (origin == null) return;
		ServerLevel level = params.getLevel();
		BlockPos pos = BlockPos.containing(origin);
		dev.villagerework.perk.TakeHomes.drops(level, pos, state, cir.getReturnValue()).ifPresent(cir::setReturnValue);
	}
}
