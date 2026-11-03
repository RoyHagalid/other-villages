package dev.villagerework.item;

import dev.villagerework.config.VRConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What an explosive arrow does when it hits something: an explosion where it hit, then it's gone.
 * Block damage and damage to creatures are tuned separately:
 *  - blocks: an explosion of strength {@code explosiveArrows.blockPower} (3, a creeper's; beds are 5), only if
 *    {@code explosiveArrows.breakBlocks} (on by default; /vr explosivearrows breakblocks on|off)
 *  - creatures and players: {@code explosiveArrows.entityDamage} (0.5) of what that explosion would do
 * Ancient debris resists explosions, so blasting netherrack uncovers it, as with beds.
 */
public final class ExplosiveArrows {
	private ExplosiveArrows() {
	}

	/** Explosive arrows in flight (server side), for their fuse trail. Weak, so gone arrows don't linger. */
	private static final java.util.Set<Entity> FLYING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	/** A fresh explosive arrow was shot: give it a fuse trail while it flies (0.17.10). */
	public static void track(Entity arrow) {
		if (arrow.level() instanceof ServerLevel) FLYING.add(arrow);
	}

	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> trail());
	}

	/** Sparks and smoke behind every explosive arrow in the air, like a lit fuse. */
	private static void trail() {
		if (FLYING.isEmpty()) return;
		java.util.Iterator<Entity> it = FLYING.iterator();
		while (it.hasNext()) {
			Entity arrow = it.next();
			if (arrow.isRemoved() || !(arrow.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (arrow.getDeltaMovement().lengthSqr() < 1.0E-4) continue; // lying still: no trail
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
					arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
			if (level.getRandom().nextBoolean()) {
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
						arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.03, 0.03, 0.03, 0.0);
			}
		}
	}

	public static boolean isExplosive(Entity arrow) {
		return arrow.entityTags().contains(ExplosiveArrowItem.TAG);
	}

	public static void explode(AbstractArrow arrow, Vec3 at) {
		if (!(arrow.level() instanceof ServerLevel level)) return;
		VRConfig.ExplosiveArrows cfg = VRConfig.get().explosiveArrows;
		arrow.discard();
		level.explode(arrow, level.damageSources().explosion(arrow, arrow.getOwner()), new Rules(cfg),
				at, (float) cfg.blockPower, false, Level.ExplosionInteraction.TNT);
	}

	private static final class Rules extends ExplosionDamageCalculator {
		private final VRConfig.ExplosiveArrows cfg;

		Rules(VRConfig.ExplosiveArrows cfg) {
			this.cfg = cfg;
		}

		@Override
		public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
			return cfg.breakBlocks && super.shouldBlockExplode(explosion, level, pos, state, power);
		}

		@Override
		public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
			return (float) (super.getEntityDamageAmount(explosion, entity, exposure) * cfg.entityDamage);
		}
	}

	/** Forgets arrows in flight when the server stops. */
	public static void reset() {
		FLYING.clear();
	}
}
