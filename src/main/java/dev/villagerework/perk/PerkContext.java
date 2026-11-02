package dev.villagerework.perk;

/**
 * Hands the perk level of the block being worked on from a method's start to a call deep inside it,
 * for mixins where that inner call doesn't know the block's position. Set at the method's HEAD,
 * cleared at RETURN. Per thread, so the client thread never sees a server value.
 */
public final class PerkContext {
	private static final ThreadLocal<int[]> LEVEL = ThreadLocal.withInitial(() -> new int[1]);
	private static final ThreadLocal<Object> SUBJECT = new ThreadLocal<>();

	private PerkContext() {
	}

	public static void set(int level) {
		LEVEL.get()[0] = level;
	}

	public static int get() {
		return LEVEL.get()[0];
	}

	/** Optional extra object for the inner call (e.g. the block entity being ticked). */
	public static void setSubject(Object subject) {
		SUBJECT.set(subject);
	}

	public static Object subject() {
		return SUBJECT.get();
	}

	public static void clear() {
		LEVEL.get()[0] = 0;
		SUBJECT.remove();
	}
}
