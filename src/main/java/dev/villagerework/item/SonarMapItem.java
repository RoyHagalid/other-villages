package dev.villagerework.item;

import dev.villagerework.perk.SonarMaps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An unused sonar map (cartographer level III). Using it, like an empty map, makes a filled map centered on you,
 * which maps caves at the depth you were standing at (see SonarMaps).
 */
public class SonarMapItem extends Item {
	public SonarMapItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack map = SonarMaps.create(server, serverPlayer);
		stack.consume(1, player);
		if (stack.isEmpty()) {
			player.setItemInHand(hand, map);
		} else if (!player.getInventory().add(map)) {
			net.minecraft.world.Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), map);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
		return InteractionResult.SUCCESS;
	}
}
