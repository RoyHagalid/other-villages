package dev.villagerework.perk;

import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Weaponsmith's grindstone, level III: honing. A sword or axe plus a piece of flint gets a sharp edge,
 * +{@code perks.honedAttackBonus} attack damage (2) for the next {@code perks.honedHits} hits; a pickaxe,
 * shovel or hoe gets +{@code perks.honedMiningBonus} mining efficiency (5, about Efficiency II) for the next
 * {@code perks.honedBlocks} blocks. Meant for the early game: a stone or iron tool feels it, a diamond tool
 * with Sharpness or Efficiency V barely does (and needs honing again every time).
 * The item shows "Honed (n)" and counts down; at 0 the edge is gone. Honing again starts the count over
 * (it never stacks). The edge is an ordinary attribute modifier on the item, so it works with Sharpness.
 */
public final class Honing {
	private Honing() {
	}

	private static final Identifier MODIFIER = VillageRework.id("honed");
	private static final String KEY = "villagerework_honed";
	private static final String LINE_PREFIX = "Honed (";

	public static boolean isWeapon(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
	}

	public static boolean isTool(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
	}

	public static boolean honable(ItemStack stack) {
		return stack.getCount() == 1 && (isWeapon(stack) || isTool(stack));
	}

	/** Uses left on a honed item, 0 if it isn't honed. */
	public static int usesLeft(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(KEY, 0);
	}

	/** A honed copy of {@code stack} with a full count. */
	public static ItemStack hone(ItemStack stack) {
		ItemStack out = stack.copyWithCount(1);
		VRConfig.Perks cfg = VRConfig.get().perks;
		strip(out);
		boolean weapon = isWeapon(out);
		Holder<Attribute> attribute = weapon ? Attributes.ATTACK_DAMAGE : Attributes.MINING_EFFICIENCY;
		AttributeModifier modifier = new AttributeModifier(MODIFIER,
				weapon ? cfg.honedAttackBonus : cfg.honedMiningBonus, AttributeModifier.Operation.ADD_VALUE);
		ItemAttributeModifiers current = out.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		out.set(DataComponents.ATTRIBUTE_MODIFIERS, current.withModifierAdded(attribute, modifier, EquipmentSlotGroup.MAINHAND));
		setUses(out, weapon ? cfg.honedHits : cfg.honedBlocks);
		return out;
	}

	/** One hit (weapon) or one block (tool) used up. Removes the edge at 0. */
	public static void use(ItemStack stack) {
		int left = usesLeft(stack);
		if (left <= 0) return;
		if (left <= 1) {
			strip(stack);
		} else {
			setUses(stack, left - 1);
		}
	}

	private static void setUses(ItemStack stack, int uses) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KEY, uses));
		List<Component> lines = new ArrayList<>(withoutOurLine(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()));
		lines.add(0, Component.literal(LINE_PREFIX + uses + ")")
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
		stack.set(DataComponents.LORE, new ItemLore(lines));
	}

	/**
	 * Takes the edge off: our modifier, our count and our tooltip line. The item's own attribute modifiers
	 * are put back exactly as the item defines them (so it shows and behaves like an un-honed item again);
	 * modifiers some other source added are kept.
	 */
	private static void strip(ItemStack stack) {
		ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		ItemAttributeModifiers prototype = stack.getPrototype().get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (current != null && current.modifiers().stream().anyMatch(e -> e.modifier().id().equals(MODIFIER))) {
			List<ItemAttributeModifiers.Entry> foreign = new ArrayList<>();
			for (ItemAttributeModifiers.Entry e : current.modifiers()) {
				boolean ours = e.modifier().id().equals(MODIFIER);
				boolean own = prototype != null && prototype.modifiers().stream()
						.anyMatch(p -> p.modifier().id().equals(e.modifier().id()) && p.attribute().equals(e.attribute()));
				if (!ours && !own) foreign.add(e);
			}
			if (prototype == null) prototype = ItemAttributeModifiers.EMPTY;
			if (foreign.isEmpty()) {
				stack.set(DataComponents.ATTRIBUTE_MODIFIERS, prototype); // same as the item's default: no override left
			} else {
				List<ItemAttributeModifiers.Entry> all = new ArrayList<>(prototype.modifiers());
				all.addAll(foreign);
				stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(all));
			}
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.copyTag().contains(KEY)) {
			var tag = data.copyTag();
			tag.remove(KEY);
			if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
			else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			List<Component> lines = withoutOurLine(lore.lines());
			if (lines.isEmpty()) stack.remove(DataComponents.LORE);
			else stack.set(DataComponents.LORE, new ItemLore(lines));
		}
	}

	private static List<Component> withoutOurLine(List<Component> lines) {
		List<Component> out = new ArrayList<>();
		for (Component c : lines) if (!c.getString().startsWith(LINE_PREFIX)) out.add(c);
		return out;
	}
}
