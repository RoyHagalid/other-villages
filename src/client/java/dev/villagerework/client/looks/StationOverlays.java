package dev.villagerework.client.looks;

import dev.villagerework.VillageRework;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.client.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Tier marks on placed station blocks (0.17.5).
 *
 * The blocks stay vanilla. Their models are wrapped once at load: at a position the server marked as tier II/III
 * (StationLooksClient), every face drawn with a station texture (say {@code smoker_front}) gets a second copy on
 * top, drawn with the overlay {@code villagerework:block/tier/smoker_front_2}. The copy has exactly the same corners,
 * so it follows every block state (lit smoker, open barrel, filled cauldron) and rotation, and resource packs
 * still show underneath. A face without a painted overlay simply stays plain.
 */
public final class StationOverlays {
	/** Blocks whose models get wrapped. */
	private static final Set<Block> BLOCKS = Set.of(
			Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.BREWING_STAND, Blocks.COMPOSTER,
			Blocks.CAULDRON, Blocks.WATER_CAULDRON, Blocks.LAVA_CAULDRON, Blocks.POWDER_SNOW_CAULDRON,
			Blocks.BARREL, Blocks.STONECUTTER, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.LOOM,
			Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.LECTERN);

	/** Vanilla block textures that can carry a tier overlay (same list as the texture kit). */
	public static final List<String> TEXTURES = List.of(
			"smoker_front", "smoker_front_on", "smoker_side", "smoker_top",
			"blast_furnace_front", "blast_furnace_front_on", "blast_furnace_side", "blast_furnace_top",
			"brewing_stand", "brewing_stand_base",
			"composter_side", "composter_top",
			"cauldron_side", "cauldron_top",
			"barrel_side", "barrel_top", "barrel_top_open",
			"stonecutter_side", "stonecutter_top",
			"smithing_table_front", "smithing_table_side", "smithing_table_top",
			"grindstone_side", "grindstone_round", "grindstone_pivot",
			"loom_front", "loom_side", "loom_top",
			"cartography_table_side1", "cartography_table_side2", "cartography_table_side3", "cartography_table_top",
			"fletching_table_front", "fletching_table_side", "fletching_table_top",
			"lectern_front", "lectern_sides", "lectern_top", "lectern_base");

	/** Overlays found at the last model bake: vanilla sprite → overlay per tier (index 2 and 3). */
	private static Map<Identifier, Material.Baked[]> overlays = Map.of();
	private static Object bakedWith;

	private StationOverlays() {
	}

	static void register() {
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, ctx) -> {
			if (!BLOCKS.contains(ctx.state().getBlock())) return model;
			Map<Identifier, Material.Baked[]> found = overlays(ctx.baker());
			return found.isEmpty() ? model : new Wrapped(model, found);
		}));
	}

	/** The overlay textures that exist, looked up once per model bake (missing ones are skipped quietly). */
	private static synchronized Map<Identifier, Material.Baked[]> overlays(ModelBaker baker) {
		if (bakedWith == baker.materials()) return overlays;
		Map<Identifier, Material.Baked[]> found = new HashMap<>();
		var resources = Minecraft.getInstance().getResourceManager();
		for (String texture : TEXTURES) {
			Material.Baked[] tiers = new Material.Baked[4];
			boolean any = false;
			for (int tier = 2; tier <= 3; tier++) {
				String path = "block/tier/" + texture + "_" + tier;
				if (resources.getResource(VillageRework.id("textures/" + path + ".png")).isEmpty()) continue;
				tiers[tier] = baker.materials().get(new Material(VillageRework.id(path)), () -> "villagerework station overlays");
				any = true;
			}
			if (any) found.put(Identifier.withDefaultNamespace("block/" + texture), tiers);
		}
		overlays = Map.copyOf(found);
		bakedWith = baker.materials();
		return overlays;
	}

	/** A station block's model with the tier marks added where the server says so. */
	private static final class Wrapped extends WrapperBlockStateModel {
		private final Map<Identifier, Material.Baked[]> overlays;

		Wrapped(BlockStateModel wrapped, Map<Identifier, Material.Baked[]> overlays) {
			super(wrapped);
			this.overlays = overlays;
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
				Predicate<@Nullable Direction> cullTest) {
			int tier = StationLooksClient.tier(pos);
			if (tier < 2 || tier > 3) {
				wrapped.emitQuads(emitter, level, pos, state, random, cullTest);
				return;
			}
			List<BlockStateModelPart> parts = new ArrayList<>();
			wrapped.collectParts(random, parts);
			for (BlockStateModelPart part : parts) part.emitQuads(emitter, cullTest);
			for (BlockStateModelPart part : parts) emitOverlays(emitter, part, tier, cullTest);
		}

		private void emitOverlays(QuadEmitter emitter, BlockStateModelPart part, int tier, Predicate<@Nullable Direction> cullTest) {
			TriState ao = part.useAmbientOcclusion() ? TriState.DEFAULT : TriState.FALSE;
			for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
				Direction cullFace = ModelHelper.faceFromIndex(i);
				if (cullTest.test(cullFace)) continue;
				for (BakedQuad quad : part.getQuads(cullFace)) {
					TextureAtlasSprite from = quad.materialInfo().sprite();
					Material.Baked[] tiers = overlays.get(from.contents().name());
					if (tiers == null || tiers[tier] == null) continue;
					TextureAtlasSprite to = tiers[tier].sprite();
					emitter.cullFace(cullFace);
					emitter.fromBakedQuad(quad);
					// Same corners, same spot on the texture; only the texture changes.
					float du = from.getU1() - from.getU0(), dv = from.getV1() - from.getV0();
					for (int v = 0; v < 4; v++) {
						float u = du == 0 ? 0 : (emitter.u(v) - from.getU0()) / du;
						float w = dv == 0 ? 0 : (emitter.v(v) - from.getV0()) / dv;
						emitter.uv(v, to.getU0() + u * (to.getU1() - to.getU0()), to.getV0() + w * (to.getV1() - to.getV0()));
					}
					emitter.ambientOcclusion(ao);
					emitter.shadeMode(ShadeMode.VANILLA);
					emitter.postMaterialBake(tiers[tier]);
					emitter.emit();
				}
			}
		}

		@Override
		public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			// A tiered block's geometry differs from a plain one's, so it gets no shared cache key.
			return StationLooksClient.tier(pos) >= 2 ? null : wrapped.createGeometryKey(level, pos, state, random);
		}
	}
}
