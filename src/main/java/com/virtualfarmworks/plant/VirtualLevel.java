/*
 * VirtualLevel — an in-memory world (a flat plane of one soil block with air above) that vanilla and modded code can
 * run against without touching the real world: survival rules of plants and the virtual growth of trees.
 */
package com.virtualfarmworks.plant;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;

/**
 * A tiny world that exists only in memory: every block below {@link #ORIGIN} is the same ground block (the soil), every
 * block from {@code ORIGIN} up is air, and whatever code places on top is kept in a hash map. Nothing here reads or
 * writes the real world, loads chunks, spawns entities or makes sounds.
 *
 * <h2>Why it exists</h2>
 * Two pieces of game code only accept a real level type:
 * <ul>
 *   <li>{@code BlockState#canSurvive(LevelReader, BlockPos)}: the survival rule of plants that are not plain
 *       vegetation (kelp, vines, hanging roots...). {@link PlantRules} asks it whether a plant stands on a soil.</li>
 *   <li>{@code ConfiguredFeature#place(WorldGenLevel, ...)}: how a sapling or a fungus grows into a tree. The harvest of
 *       a tree ({@code harvest.TreeGrowth}) grows one here with the tree's own feature and counts its blocks.</li>
 * </ul>
 * Running the real code against this world keeps vanilla and modded plants and trees working without per-mod code.
 *
 * <h2>Two modes</h2>
 * {@link #rules(BlockState)} has no server behind it (plant rules also run on the client, for slot checks): only block
 * states are available, and methods that need registries or the dimension throw {@link UnsupportedOperationException}
 * (callers catch it). {@link #growth(BlockState, ServerLevel, RandomSource)} borrows the server level's registries,
 * seed and chunk generator (read-only, needed by features); the world itself stays virtual.
 *
 * <h2>Ideal conditions</h2>
 * Light is always full and there is no water anywhere, like every virtual plot (see {@link PlantRules}).
 *
 * <p>Safety: a feature may place at most {@link #MAX_PLACEMENTS} blocks, so a broken modded feature cannot run away
 * with memory or time; past that, {@link TooManyBlocks} is thrown and the growth counts as failed. Server thread only
 * for growth; a new level is made for every check (cheap: a few fields and an empty map).
 */
public final class VirtualLevel implements WorldGenLevel {
    /** Where the plant stands: the first air block above the ground plane. */
    public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    /** Upper bound of blocks one feature may place (a big vanilla tree places a few hundred). */
    public static final int MAX_PLACEMENTS = 16_384;

    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final BlockState ground;
    private final @Nullable ServerLevel server;
    private final RandomSource random;
    /** Every block code placed (air included, which can replace ground), keyed by {@link BlockPos#asLong()}. */
    private final Long2ObjectMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
    /** Highest placed non-air Y per column ({@code x, z} packed), for heightmap queries. */
    private final Long2IntOpenHashMap columnTops = new Long2IntOpenHashMap();
    private int placements;
    private long subTickCount;
    private @Nullable BiomeManager biomeManager;

    private VirtualLevel(BlockState ground, @Nullable ServerLevel server, RandomSource random) {
        this.ground = ground;
        this.server = server;
        this.random = random;
        this.columnTops.defaultReturnValue(Integer.MIN_VALUE);
    }

    /** A world for survival checks: {@code ground} below {@link #ORIGIN}, no server (safe on the client). */
    public static VirtualLevel rules(BlockState ground) {
        return new VirtualLevel(ground, null, RandomSource.create(0L));
    }

    /** A world to grow a tree in: {@code ground} below {@link #ORIGIN}, the server's registries for features. */
    public static VirtualLevel growth(BlockState ground, ServerLevel server, RandomSource random) {
        return new VirtualLevel(ground, server, random);
    }

    /** Thrown when a feature places more than {@link #MAX_PLACEMENTS} blocks. */
    public static final class TooManyBlocks extends RuntimeException {
        private TooManyBlocks() {
            super("A feature placed more than " + MAX_PLACEMENTS + " blocks in a virtual level", null, false, false);
        }
    }

    /**
     * Counts every block placed at or above {@link #ORIGIN}'s height, by state, in placement order: what grew on top of
     * the ground. Ground changes (dirt under a trunk, podzol, roots grown into mud) are not part of it.
     */
    public Object2IntMap<BlockState> countGrownBlocks() {
        Object2IntMap<BlockState> counts = new Object2IntLinkedOpenHashMap<>();
        for (Long2ObjectMap.Entry<BlockState> entry : placed.long2ObjectEntrySet()) {
            BlockState state = entry.getValue();
            if (!state.isAir() && BlockPos.getY(entry.getLongKey()) >= ORIGIN.getY()) {
                counts.mergeInt(state, 1, Integer::sum);
            }
        }
        return counts;
    }

    // --- blocks -----------------------------------------------------------------------------------------------------

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = placed.get(pos.asLong());
        if (state != null) {
            return state;
        }
        return pos.getY() < ORIGIN.getY() && pos.getY() >= MIN_Y ? ground : AIR;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        return predicate.test(getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(getFluidState(pos));
    }

    /** No block entities exist here: decorators that fill one (bee nests) simply find none. */
    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    /** Stores the state; no neighbour updates, block entities, drops or lighting (like world generation). */
    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int updateFlags, int updateLimit) {
        if (++placements > MAX_PLACEMENTS) {
            throw new TooManyBlocks();
        }
        placed.put(pos.asLong(), state);
        if (!state.isAir()) {
            long column = columnKey(pos.getX(), pos.getZ());
            if (pos.getY() > columnTops.get(column)) {
                columnTops.put(column, pos.getY());
            }
        }
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
        return setBlock(pos, AIR, Block.UPDATE_ALL);
    }

    /** Breaking only removes the block: nothing drops in the virtual world. */
    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropResources, @Nullable Entity breaker, int updateLimit) {
        return !getBlockState(pos).isAir() && setBlock(pos, AIR, Block.UPDATE_ALL, updateLimit);
    }

    /** No entities in the virtual world. */
    @Override
    public boolean addFreshEntity(Entity entity) {
        return false;
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        Predicate<BlockState> counts = type.isOpaque();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = columnTops.get(columnKey(x, z)); y >= ORIGIN.getY(); y--) {
            if (counts.test(getBlockState(pos.set(x, y, z)))) {
                return y + 1;
            }
        }
        return ORIGIN.getY(); // the top of the ground plane
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // --- ideal conditions -------------------------------------------------------------------------------------------

    /** Full light everywhere (virtual plots ignore light). */
    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15;
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return true;
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        throw new UnsupportedOperationException("A virtual level has no light engine (light is always full)");
    }

    /** No face shading (only rendering asks; nothing renders a virtual level). */
    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    // --- world shape ------------------------------------------------------------------------------------------------

    /**
     * The world's bottom. Must be overridden: 1.21.1's default asks {@code dimensionType()}, which a rule-check level
     * (no server) cannot answer.
     */
    @Override
    public int getMinBuildHeight() {
        return MIN_Y;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    /** Every chunk "exists" (nothing to load), but none can be handed out: callers fall back to block access. */
    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public @Nullable ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus targetStatus, boolean loadOrGenerate) {
        return null;
    }

    @Override
    public @Nullable BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity source, AABB testArea) {
        return List.of();
    }

    @Override
    public WorldBorder getWorldBorder() {
        return server().getWorldBorder();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public BiomeManager getBiomeManager() {
        if (biomeManager == null) {
            biomeManager = new BiomeManager(this, 0L); // asks getUncachedNoiseBiome, since no chunk is ever returned
        }
        return biomeManager;
    }

    /** Plains everywhere: tree features do not look at biomes, but the method must answer. */
    @Override
    public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        return registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
    }

    // --- borrowed from the server (growth mode only) -----------------------------------------------------------------

    private ServerLevel server() {
        if (server == null) {
            throw new UnsupportedOperationException("A rule-check virtual level has no server");
        }
        return server;
    }

    /** The real level, only for read-only needs of features (its chunk generator). Never written to. */
    @Override
    public ServerLevel getLevel() {
        return server();
    }

    @Override
    public RegistryAccess registryAccess() {
        return server().registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return server != null ? server.enabledFeatures() : FeatureFlags.DEFAULT_FLAGS;
    }

    @Override
    public DimensionType dimensionType() {
        return server().dimensionType();
    }

    @Override
    public long getSeed() {
        return server != null ? server.getSeed() : 0L;
    }

    @Override
    public LevelData getLevelData() {
        return server().getLevelData();
    }

    @Override
    public @Nullable MinecraftServer getServer() {
        return server != null ? server.getServer() : null;
    }

    @Override
    public ChunkSource getChunkSource() {
        return server().getChunkSource();
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return new DifficultyInstance(server != null ? server.getDifficulty() : Difficulty.NORMAL, 0L, 0L, 0.0F);
    }

    @Override
    public RandomSource getRandom() {
        return random;
    }

    // --- nothing happens outside the blocks -------------------------------------------------------------------------

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public long nextSubTickCount() {
        return subTickCount++;
    }

    @Override
    public void playSound(@Nullable Player except, BlockPos pos, SoundEvent sound, SoundSource source, float volume,
                          float pitch) {
    }

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
    }

    @Override
    public void levelEvent(@Nullable Player source, int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context) {
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity except, AABB bb, @Nullable Predicate<? super Entity> selector) {
        return List.of();
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB bb,
                                                  Predicate<? super T> selector) {
        return List.of();
    }

    @Override
    public List<Player> players() {
        return List.of();
    }
}
