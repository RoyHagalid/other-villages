package dev.villagerework.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Set;
import java.util.function.BiPredicate;

/** A vanilla behaviour that only starts when a check on the entity passes (0.18.3: children's play by mood). */
public final class MoodGatedBehavior<E extends LivingEntity> implements BehaviorControl<E> {
	private final BehaviorControl<E> inner;
	private final BiPredicate<ServerLevel, E> allowed;

	public MoodGatedBehavior(BehaviorControl<E> inner, BiPredicate<ServerLevel, E> allowed) {
		this.inner = inner;
		this.allowed = allowed;
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
		return allowed.test(level, entity) && inner.tryStart(level, entity, gameTime);
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
		return "MoodGated(" + inner.debugString() + ")";
	}
}
