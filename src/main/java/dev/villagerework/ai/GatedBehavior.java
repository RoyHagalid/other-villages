package dev.villagerework.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Wraps a vanilla AI behavior so it only starts while {@code enabled} says yes.
 * The check runs every time the behavior tries to start, so config changes
 * apply immediately, without rebuilding villager brains.
 */
public final class GatedBehavior<E extends LivingEntity> implements BehaviorControl<E> {
	private final BehaviorControl<E> inner;
	private final BooleanSupplier enabled;

	public GatedBehavior(BehaviorControl<E> inner, BooleanSupplier enabled) {
		this.inner = inner;
		this.enabled = enabled;
	}

	@Override
	public Behavior.Status getStatus() {
		return inner.getStatus();
	}

	@Override
	public Set<MemoryModuleType<?>> getRequiredMemories() {
		return inner.getRequiredMemories();
	}

	@Override
	public boolean tryStart(ServerLevel level, E entity, long gameTime) {
		return enabled.getAsBoolean() && inner.tryStart(level, entity, gameTime);
	}

	@Override
	public void tickOrStop(ServerLevel level, E entity, long gameTime) {
		inner.tickOrStop(level, entity, gameTime);
	}

	@Override
	public void doStop(ServerLevel level, E entity, long gameTime) {
		inner.doStop(level, entity, gameTime);
	}

	@Override
	public String debugString() {
		return "Gated(" + inner.debugString() + ")";
	}
}
