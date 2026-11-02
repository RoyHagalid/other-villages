package dev.villagerework.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Explosive arrow (fletcher level III). Shot from a bow, crossbow or dispenser it is an ordinary arrow
 * with a marker tag; on impact it explodes instead of sticking (see ExplosiveArrows).
 */
public class ExplosiveArrowItem extends ArrowItem {
	public static final String TAG = "villagerework_explosive";

	public ExplosiveArrowItem(Properties properties) {
		super(properties);
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
		AbstractArrow arrow = super.createArrow(level, ammo, shooter, weapon);
		arrow.addTag(TAG);
		ExplosiveArrows.track(arrow);
		return arrow;
	}

	@Override
	public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
		Projectile projectile = super.asProjectile(level, pos, stack, direction);
		projectile.addTag(TAG);
		ExplosiveArrows.track(projectile);
		return projectile;
	}
}
