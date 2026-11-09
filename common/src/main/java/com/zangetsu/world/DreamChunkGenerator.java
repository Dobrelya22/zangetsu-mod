package com.zangetsu.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Земля сна: бесконечные холмистые поля с проплешинами жёсткой земли, гравия и камня, валунами,
 * редкими каменными столбами и бледной травой с цветами. Без воды, пещер и построек.
 */
public class DreamChunkGenerator extends ChunkGenerator {
    public static final MapCodec<DreamChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.REGISTRY_CODEC.fieldOf("biome").forGetter(generator -> generator.biome)
    ).apply(instance, instance.stable(DreamChunkGenerator::new)));

    private static final int BASE = 40;
    private static final int WORLD_HEIGHT = 128;

    private final RegistryEntry<Biome> biome;
    private final SimplexNoiseSampler hills = new SimplexNoiseSampler(Random.create(19870101L));
    private final SimplexNoiseSampler detail = new SimplexNoiseSampler(Random.create(20261026L));
    private final SimplexNoiseSampler patches = new SimplexNoiseSampler(Random.create(77712L));

    public DreamChunkGenerator(RegistryEntry<Biome> biome) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public StructurePlacementCalculator createStructurePlacementCalculator(RegistryWrapper<StructureSet> structureSetRegistry,
            NoiseConfig noiseConfig, long seed) {
        return StructurePlacementCalculator.create(noiseConfig, seed, this.biomeSource, Stream.<RegistryEntry<StructureSet>>empty());
    }

    /** Высота поверхности (верхний сплошной блок) в точке. */
    public int surfaceY(int x, int z) {
        double h = BASE
                + 6.5 * this.hills.sample(x / 110.0, z / 110.0)
                + 2.6 * this.hills.sample(x / 42.0 + 100.0, z / 42.0 - 50.0)
                + 0.55 * this.detail.sample(x / 13.0, z / 13.0);
        // Редкие крутые бугры.
        double bump = this.detail.sample(x / 23.0 + 300.0, z / 23.0 + 300.0);
        if (bump > 0.55) {
            h += (bump - 0.55) * 14.0;
        }
        return MathHelper.floor(h);
    }

    /** Чем покрыт верх: трава, жёсткая земля, гравий или голый камень. */
    private BlockState top(int x, int z, int y) {
        double p = this.patches.sample(x / 17.0, z / 17.0);
        double q = this.patches.sample(x / 7.0 + 40.0, z / 7.0 - 40.0);
        if (y > BASE + 9 || p > 0.62) {
            return q > 0.2 ? Blocks.ANDESITE.getDefaultState() : Blocks.STONE.getDefaultState();
        }
        if (p < -0.55) {
            return Blocks.GRAVEL.getDefaultState();
        }
        if (p > 0.32 || q > 0.72) {
            return Blocks.COARSE_DIRT.getDefaultState();
        }
        return Blocks.GRASS_BLOCK.getDefaultState();
    }

    private BlockState column(int x, int z, int y, int surface) {
        if (y == 0) {
            return Blocks.BEDROCK.getDefaultState();
        }
        if (y == surface) {
            return this.top(x, z, y);
        }
        if (y >= surface - 3) {
            BlockState top = this.top(x, z, surface);
            return top.isOf(Blocks.GRASS_BLOCK) || top.isOf(Blocks.COARSE_DIRT) ? Blocks.DIRT.getDefaultState() : top;
        }
        return Blocks.STONE.getDefaultState();
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        Heightmap ocean = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap surfaceMap = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        ChunkPos chunkPos = chunk.getPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkPos.getStartX() + lx, z = chunkPos.getStartZ() + lz;
                int surface = this.surfaceY(x, z);
                for (int y = 0; y <= surface; y++) {
                    BlockState state = this.column(x, z, y, surface);
                    chunk.setBlockState(pos.set(lx, y, lz), state, false);
                    ocean.trackUpdate(lx, y, lz, state);
                    surfaceMap.trackUpdate(lx, y, lz, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** Валуны, каменные столбы, трава и цветы — уже по готовой земле. */
    @Override
    public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
        super.generateFeatures(world, chunk, structureAccessor);
        ChunkPos chunkPos = chunk.getPos();
        Random random = Random.create(chunkPos.toLong() * 341873128712L + 132897987541L);
        int x0 = chunkPos.getStartX(), z0 = chunkPos.getStartZ();
        if (random.nextFloat() < 0.4F) {
            this.boulder(world, random, x0 + 3 + random.nextInt(10), z0 + 3 + random.nextInt(10));
        }
        if (random.nextFloat() < 0.12F) {
            this.boulder(world, random, x0 + 3 + random.nextInt(10), z0 + 3 + random.nextInt(10));
        }
        if (random.nextFloat() < 0.06F) {
            this.spire(world, random, x0 + 4 + random.nextInt(8), z0 + 4 + random.nextInt(8));
        }
        for (int i = 0; i < 26; i++) {
            int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
            BlockPos ground = new BlockPos(x, this.surfaceY(x, z), z);
            BlockState below = world.getBlockState(ground);
            BlockPos above = ground.up();
            if (!world.getBlockState(above).isAir()) {
                continue;
            }
            BlockState plant = null;
            if (below.isOf(Blocks.GRASS_BLOCK)) {
                float r = random.nextFloat();
                plant = r < 0.66F ? Blocks.SHORT_GRASS.getDefaultState() : r < 0.8F ? Blocks.FERN.getDefaultState()
                        : r < 0.88F ? Blocks.OXEYE_DAISY.getDefaultState() : r < 0.95F ? Blocks.LILY_OF_THE_VALLEY.getDefaultState()
                        : Blocks.WHITE_TULIP.getDefaultState();
            } else if (below.isOf(Blocks.COARSE_DIRT) && random.nextFloat() < 0.4F) {
                plant = Blocks.DEAD_BUSH.getDefaultState();
            }
            if (plant != null && plant.canPlaceAt(world, above)) {
                world.setBlockState(above, plant, Block.NOTIFY_LISTENERS);
            }
        }
    }

    /** Валун: неровный комок камня, булыжника и мшистого булыжника, наполовину в земле. */
    private void boulder(StructureWorldAccess world, Random random, int cx, int cz) {
        int cy = this.surfaceY(cx, cz);
        float rx = 1.4F + random.nextFloat() * 1.3F, ry = 1.1F + random.nextFloat() * 1.1F, rz = 1.4F + random.nextFloat() * 1.3F;
        BlockState[] stones = {Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(),
                Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.ANDESITE.getDefaultState()};
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    float d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
                    if (d <= 1.0F + (random.nextFloat() - 0.5F) * 0.35F) {
                        world.setBlockState(new BlockPos(cx + dx, cy + dy, cz + dz), stones[random.nextInt(stones.length)], Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    /** Каменный столб, сужающийся кверху. */
    private void spire(StructureWorldAccess world, Random random, int cx, int cz) {
        int base = this.surfaceY(cx, cz);
        int height = 4 + random.nextInt(5);
        for (int dy = 0; dy < height; dy++) {
            float r = 1.6F * (1.0F - (float) dy / height) + 0.4F;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        BlockState state = random.nextFloat() < 0.3F ? Blocks.ANDESITE.getDefaultState() : Blocks.STONE.getDefaultState();
                        world.setBlockState(new BlockPos(cx + dx, base + dy, cz + dz), state, Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor,
            Chunk chunk, GenerationStep.Carver carverStep) {
    }

    @Override
    public void populateEntities(ChunkRegion region) {
    }

    @Override
    public int getWorldHeight() {
        return WORLD_HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinimumY() {
        return 0;
    }

    @Override
    public int getSpawnHeight(HeightLimitView world) {
        return BASE + 1;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return this.surfaceY(x, z) + 1;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        int surface = this.surfaceY(x, z);
        BlockState[] states = new BlockState[world.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = world.getBottomY() + i;
            states[i] = y <= surface ? this.column(x, z, y, surface) : Blocks.AIR.getDefaultState();
        }
        return new VerticalBlockSample(world.getBottomY(), states);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
    }
}
