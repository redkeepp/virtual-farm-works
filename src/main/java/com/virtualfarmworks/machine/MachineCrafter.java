/*
 * MachineCrafter — the autocrafter of the Entropic Farm Matrix (owner spec, inspired by RFTools' Crafter tier 3): the
 * crafting-table recipes the player set, the CRAFT: ON/OFF switch and the hidden buffer of ingredients waiting for the
 * rest of a recipe. While ON, harvested items a recipe uses are crafted before they reach the output.
 */
package com.virtualfarmworks.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.virtualfarmworks.plant.SoilRules;
import com.virtualfarmworks.sim.DropTally;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Server side only; the menu edits it through the machine.
 *
 * <h2>Recipes</h2>
 * A recipe is the 3x3 grid the player arranged (by hand or with JEI's "+") plus the id of the recipe it made when it
 * was set; the id is only a hint that keeps the same recipe when a modpack has two matching one grid. Only
 * crafting-table recipes a player could place are accepted: special recipes (map cloning, firework stars...) are
 * refused. The grid decides WHICH recipe; crafting then takes, in each cell, any item that recipe accepts there, like a
 * real crafting table (sticks set with oak planks also take birch planks).
 *
 * <h2>Crafting ({@link #plan})</h2>
 * The machine hands over each harvest batch after the replant and before the harvest filter (owner order). Items a
 * recipe uses join the buffer; every recipe then crafts as many times as the buffer allows, in chain order: a result
 * another recipe uses stays in the buffer and goes on crafting (essence, then ingots, then blocks); everything else
 * goes to the output together with the remainders (empty buckets...). Recipes that use each other's results in a circle
 * (ingots to block, block to ingots) never chain into each other, so nothing can go round forever. What waits for the
 * rest of a recipe stays hidden, at most {@code machines.<tier>.crafterBufferLimit} of each item; the rest goes to the
 * output. Crafting costs no time and no energy.
 *
 * <h2>Transactions</h2>
 * {@link #plan} changes nothing: it works on a copy of the buffer. The machine stores the plan's output all-or-nothing
 * and only then {@link #commit commits} the new buffer, so a batch that does not fit leaves the crafter as it was.
 *
 * <h2>Items leaving the buffer</h2>
 * CRAFT turned OFF: the whole buffer goes to the output (owner decision); recipes edited: the items no recipe uses any
 * more. Both are returned to the machine, which stores them before anything else. Breaking the machine deletes the
 * buffer (owner rule for hidden items).
 */
public final class MachineCrafter {
    /** Most recipes a machine can hold, whatever the config says (its upper bound); the menu syncs this many. */
    public static final int MAX_RECIPES = 64;
    public static final int GRID_SIZE = 9;
    /** Item choices tried per recipe and batch; each round uses an item up, so ordinary recipes need one or two. */
    private static final int MAX_ROUNDS = 16;

    /** A recipe as the player set it: the grid (9 cells, one item or empty each) and the recipe it made then. */
    public record Pattern(List<ItemStack> grid, Optional<ResourceKey<Recipe<?>>> recipeId) {
    }

    private static final Codec<Pattern> PATTERN_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("grid").forGetter(Pattern::grid),
            Recipe.KEY_CODEC.optionalFieldOf("recipe").forGetter(Pattern::recipeId))
            .apply(instance, Pattern::new));

    /** Saved form of the buffer: item and amount (amounts can exceed a stack). */
    private static final Codec<List<DropTally.Entry<ItemResource>>> AMOUNTS_CODEC = RecordCodecBuilder
            .<DropTally.Entry<ItemResource>>create(instance -> instance.group(
                    ItemResource.CODEC.fieldOf("item").forGetter(DropTally.Entry::key),
                    Codec.LONG.fieldOf("amount").forGetter(DropTally.Entry::amount))
                    .apply(instance, DropTally.Entry::new))
            .listOf();

    /**
     * What a harvest batch becomes: the items to store, in order, and the buffer after crafting.
     *
     * @param version the crafter version it was planned for; {@link #commit} requires the same
     */
    public record Plan(List<DropTally.Entry<ItemResource>> output, Map<ItemResource, Long> buffer, int version) {
    }

    private final Runnable onChange;
    private final List<Pattern> patterns = new ArrayList<>();
    private boolean enabled = true;
    /** Ingredients waiting for the rest of a recipe, in arrival order. */
    private Map<ItemResource, Long> buffer = new LinkedHashMap<>();
    /** Changes with every edit of the recipes, the switch or the buffer. */
    private int version;

    // --- derived, rebuilt by resolve() --------------------------------------------------------------------------------
    private boolean resolved;
    private int resolvedGeneration = -1;
    private List<Resolved> recipes = List.of();
    /** Recipe indices in chain order: a recipe comes after every recipe whose result it uses. */
    private int[] order = new int[0];
    /** Results of the recipes that exist: what counts as "crafted" for the faces (OUTPUT CRAFTED). */
    private Set<ItemResource> results = Set.of();
    private boolean anyRecipe;
    /** Whether an item is used by some recipe; filled on demand. */
    private final Map<ItemResource, Boolean> ingredients = new HashMap<>();

    /** A pattern matched against the loaded recipes. {@code holder} is null when none matches it any more. */
    private static final class Resolved {
        final List<ItemStack> grid;
        final @Nullable RecipeHolder<CraftingRecipe> holder;
        final ItemStack result;
        /** Cells accepting an item (bit mask), per item; filled on demand. */
        final Map<ItemResource, Integer> cellMasks = new HashMap<>();
        /** The result stays in the buffer for a recipe further down the chain (see {@link #chainOrder}). */
        boolean chainsResult;

        Resolved(List<ItemStack> grid, @Nullable RecipeHolder<CraftingRecipe> holder, ItemStack result) {
            this.grid = grid;
            this.holder = holder;
            this.result = result;
        }
    }

    /** @param onChange called after every change that must be saved */
    public MachineCrafter(Runnable onChange) {
        this.onChange = onChange;
    }

    // =================================================================================================================
    // Recipes and switch (menu actions through the machine)
    // =================================================================================================================

    public int size() {
        return patterns.size();
    }

    /** The grid of a recipe (copies), to load it back into the crafting table. */
    public List<ItemStack> grid(int index) {
        return patterns.get(index).grid().stream().map(ItemStack::copy).toList();
    }

    /** What a recipe makes (a copy with its count), or empty while it is not resolved or no longer exists. */
    public ItemStack result(int index) {
        return resolved && index < recipes.size() ? recipes.get(index).result.copy() : ItemStack.EMPTY;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * CRAFT: ON/OFF. Turning it OFF empties the buffer.
     *
     * @return the items that left the buffer (the machine stores them)
     */
    public List<DropTally.Entry<ItemResource>> setEnabled(boolean on) {
        if (enabled == on) {
            return List.of();
        }
        enabled = on;
        List<DropTally.Entry<ItemResource>> released = on ? List.of() : entries(buffer);
        if (!on) {
            buffer = new LinkedHashMap<>();
        }
        changed();
        return released;
    }

    /** Adds a recipe at the end of the list (ignored beyond {@link #MAX_RECIPES}). Resolve before crafting. */
    public void add(List<ItemStack> grid, @Nullable ResourceKey<Recipe<?>> recipeId) {
        if (patterns.size() < MAX_RECIPES) {
            patterns.add(new Pattern(normalize(grid), Optional.ofNullable(recipeId)));
            recipesChanged();
        }
    }

    /** Replaces a recipe (SET CRAFT with a recipe selected). Resolve before crafting. */
    public void replace(int index, List<ItemStack> grid, @Nullable ResourceKey<Recipe<?>> recipeId) {
        if (index >= 0 && index < patterns.size()) {
            patterns.set(index, new Pattern(normalize(grid), Optional.ofNullable(recipeId)));
            recipesChanged();
        }
    }

    /** Deletes a recipe (double click). Resolve before crafting. */
    public void remove(int index) {
        if (index >= 0 && index < patterns.size()) {
            patterns.remove(index);
            recipesChanged();
        }
    }

    private void recipesChanged() {
        resolved = false;
        changed();
    }

    private void changed() {
        version++;
        onChange.run();
    }

    // =================================================================================================================
    // Resolution
    // =================================================================================================================

    /** Whether {@link #resolve} must run before crafting: the recipes changed, or the datapacks were reloaded. */
    public boolean needsResolve() {
        return !resolved || resolvedGeneration != SoilRules.cacheGeneration();
    }

    /**
     * Matches the grids against the loaded recipes, orders them for chains and takes out of the buffer what no recipe
     * uses any more. Runs only when {@link #needsResolve()}: after an edit and after a datapack reload.
     *
     * @return the items that left the buffer (the machine stores them)
     */
    public List<DropTally.Entry<ItemResource>> resolve(ServerLevel level) {
        List<Resolved> list = new ArrayList<>(patterns.size());
        for (Pattern pattern : patterns) {
            Optional<RecipeHolder<CraftingRecipe>> holder = find(pattern.grid(), pattern.recipeId().orElse(null), level);
            ItemStack result = holder.map(h -> h.value().assemble(input(pattern.grid()))).orElse(ItemStack.EMPTY);
            list.add(new Resolved(pattern.grid(), result.isEmpty() ? null : holder.get(), result));
        }
        recipes = list;
        ingredients.clear();
        Set<ItemResource> resultSet = new HashSet<>();
        anyRecipe = false;
        for (Resolved recipe : list) {
            if (recipe.holder != null) {
                anyRecipe = true;
                resultSet.add(ItemResource.of(recipe.result));
            }
        }
        results = resultSet;
        order = chainOrder(level);
        resolved = true;
        resolvedGeneration = SoilRules.cacheGeneration();

        Map<ItemResource, Long> unused = new LinkedHashMap<>();
        for (Iterator<Map.Entry<ItemResource, Long>> it = buffer.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ItemResource, Long> waiting = it.next();
            if (!isIngredient(waiting.getKey(), level)) {
                unused.put(waiting.getKey(), waiting.getValue());
                it.remove();
            }
        }
        if (!unused.isEmpty()) {
            changed();
        }
        return entries(unused);
    }

    /**
     * The crafting-table recipe a grid makes, if it is one the autocrafter accepts: placeable by a player and not
     * special (map cloning, firework stars, banner copying: their result depends on the exact items, which a stored
     * recipe cannot follow). {@code hint}: preferred while it still matches.
     */
    public static Optional<RecipeHolder<CraftingRecipe>> find(List<ItemStack> grid, @Nullable ResourceKey<Recipe<?>> hint,
                                                              ServerLevel level) {
        List<ItemStack> cells = normalize(grid);
        CraftingInput input = input(cells);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level, hint)
                .filter(holder -> !holder.value().isSpecial() && !holder.value().placementInfo().isImpossibleToPlace());
    }

    /** What a grid would make (the result preview of the crafting table), or empty when it is no accepted recipe. */
    public static ItemStack preview(List<ItemStack> grid, ServerLevel level) {
        List<ItemStack> cells = normalize(grid);
        return find(cells, null, level).map(holder -> holder.value().assemble(input(cells))).orElse(ItemStack.EMPTY);
    }

    /** Exactly 9 cells, each one item or empty. */
    public static List<ItemStack> normalize(List<ItemStack> grid) {
        List<ItemStack> cells = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack stack = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
            cells.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        return List.copyOf(cells);
    }

    private static CraftingInput input(List<ItemStack> cells) {
        return CraftingInput.of(3, 3, cells);
    }

    /**
     * Chain order. Recipe j uses recipe i's result when some cell of j accepts it. The recipes and these links form a
     * graph; recipes that reach each other in a circle form one group (a strongly connected component, Tarjan). Groups
     * are ordered so that every group comes after the groups feeding it, so one pass crafts a whole chain. A result
     * chains (stays in the buffer) only when a recipe of ANOTHER group uses it; inside a group it goes to the output,
     * which is what stops circles.
     */
    private int[] chainOrder(ServerLevel level) {
        int count = recipes.size();
        boolean[][] feeds = new boolean[count][count];
        for (int i = 0; i < count; i++) {
            Resolved from = recipes.get(i);
            if (from.holder == null) {
                continue;
            }
            ItemResource result = ItemResource.of(from.result);
            for (int j = 0; j < count; j++) {
                feeds[i][j] = recipes.get(j).holder != null && cellMask(recipes.get(j), result, level) != 0;
            }
        }
        int[] group = StronglyConnected.groups(feeds);
        for (int i = 0; i < count; i++) {
            boolean chains = false;
            for (int j = 0; j < count && !chains; j++) {
                chains = feeds[i][j] && group[j] != group[i];
            }
            recipes.get(i).chainsResult = chains;
        }
        // Tarjan numbers groups in reverse chain order (a group is numbered after every group it feeds).
        Integer[] indices = new Integer[count];
        for (int i = 0; i < count; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> group[a] != group[b] ? Integer.compare(group[b], group[a]) : Integer.compare(a, b));
        return Arrays.stream(indices).mapToInt(Integer::intValue).toArray();
    }

    /** Cells of a recipe that accept an item (bit mask): its own item, or any item the recipe matches with there. */
    private static int cellMask(Resolved recipe, ItemResource resource, ServerLevel level) {
        Integer cached = recipe.cellMasks.get(resource);
        if (cached != null) {
            return cached;
        }
        int mask = 0;
        if (recipe.holder != null && !resource.isEmpty()) {
            ItemStack candidate = resource.toStack(1);
            for (int cell = 0; cell < GRID_SIZE; cell++) {
                ItemStack own = recipe.grid.get(cell);
                if (own.isEmpty()) {
                    continue;
                }
                if (ItemStack.isSameItemSameComponents(own, candidate)) {
                    mask |= 1 << cell;
                    continue;
                }
                List<ItemStack> substituted = new ArrayList<>(recipe.grid);
                substituted.set(cell, candidate);
                if (recipe.holder.value().matches(input(substituted), level)) {
                    mask |= 1 << cell;
                }
            }
        }
        recipe.cellMasks.put(resource, mask);
        return mask;
    }

    private boolean isIngredient(ItemResource resource, ServerLevel level) {
        Boolean cached = ingredients.get(resource);
        if (cached == null) {
            cached = false;
            for (Resolved recipe : recipes) {
                if (cellMask(recipe, resource, level) != 0) {
                    cached = true;
                    break;
                }
            }
            ingredients.put(resource, cached);
        }
        return cached;
    }

    // =================================================================================================================
    // Crafting
    // =================================================================================================================

    /** CRAFT is ON and at least one recipe exists in the loaded data (so harvests go through {@link #plan}). */
    public boolean isActive() {
        return enabled && anyRecipe && resolved;
    }

    /** Whether an item is the result of one of the recipes (the faces' "crafted" items). Cheap: one set lookup. */
    public boolean isResult(ItemResource resource) {
        return !results.isEmpty() && results.contains(resource);
    }

    /**
     * Crafts a harvest batch on a copy of the buffer (see the class doc); changes nothing. Call only while
     * {@link #isActive()} and resolved.
     *
     * @param drops       the batch after the replant
     * @param bufferLimit most items of one kind left waiting (config); the rest goes to the output
     * @return the items to store and the buffer to {@link #commit} once they are stored
     */
    public Plan plan(List<DropTally.Entry<ItemResource>> drops, ServerLevel level, long bufferLimit) {
        Map<ItemResource, Long> work = new LinkedHashMap<>(buffer);
        Map<ItemResource, Long> out = new LinkedHashMap<>();
        for (DropTally.Entry<ItemResource> drop : drops) {
            (isIngredient(drop.key(), level) ? work : out).merge(drop.key(), drop.amount(), Long::sum);
        }
        for (int index : order) {
            Resolved recipe = recipes.get(index);
            if (recipe.holder != null && !work.isEmpty()) {
                craft(recipe, recipe.holder.value(), work, out, level);
            }
        }
        for (Iterator<Map.Entry<ItemResource, Long>> it = work.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ItemResource, Long> waiting = it.next();
            long extra = waiting.getValue() - Math.max(0, bufferLimit);
            if (extra > 0) {
                out.merge(waiting.getKey(), extra, Long::sum);
                if (bufferLimit > 0) {
                    waiting.setValue(bufferLimit);
                } else {
                    it.remove();
                }
            }
        }
        return new Plan(entries(out), work, version);
    }

    /**
     * Crafts one recipe as many times as {@code work} allows. Each round picks, for every cell, the waiting item with
     * the most units left that the cell accepts, then crafts as often as those items allow; a round ends when one of
     * them runs out, so a mixed stock (oak and birch planks) takes a few rounds.
     */
    private static void craft(Resolved recipe, CraftingRecipe value, Map<ItemResource, Long> work,
                              Map<ItemResource, Long> out, ServerLevel level) {
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<ItemStack> cells = new ArrayList<>(Collections.nCopies(GRID_SIZE, ItemStack.EMPTY));
            Map<ItemResource, Integer> uses = new HashMap<>();
            for (int cell = 0; cell < GRID_SIZE; cell++) {
                if (recipe.grid.get(cell).isEmpty()) {
                    continue;
                }
                ItemResource best = null;
                long bestLeft = 0;
                for (Map.Entry<ItemResource, Long> waiting : work.entrySet()) {
                    long left = waiting.getValue() - uses.getOrDefault(waiting.getKey(), 0);
                    if (left > bestLeft && (cellMask(recipe, waiting.getKey(), level) & (1 << cell)) != 0) {
                        best = waiting.getKey();
                        bestLeft = left;
                    }
                }
                if (best == null) {
                    return; // a cell has nothing left: no more crafts
                }
                cells.set(cell, best.toStack(1));
                uses.merge(best, 1, Integer::sum);
            }
            long crafts = Long.MAX_VALUE;
            for (Map.Entry<ItemResource, Integer> use : uses.entrySet()) {
                crafts = Math.min(crafts, work.get(use.getKey()) / use.getValue());
            }
            CraftingInput input = input(cells);
            if (crafts <= 0 || crafts == Long.MAX_VALUE || !value.matches(input, level)) {
                return; // a mix of items the recipe refuses as a whole (rare shapeless recipes): wait for more
            }
            ItemStack result = value.assemble(input);
            if (result.isEmpty()) {
                return;
            }
            for (Map.Entry<ItemResource, Integer> use : uses.entrySet()) {
                long left = work.get(use.getKey()) - crafts * use.getValue();
                if (left > 0) {
                    work.put(use.getKey(), left);
                } else {
                    work.remove(use.getKey());
                }
            }
            (recipe.chainsResult ? work : out).merge(ItemResource.of(result), crafts * result.getCount(), Long::sum);
            for (ItemStack remainder : value.getRemainingItems(input)) {
                if (!remainder.isEmpty()) {
                    out.merge(ItemResource.of(remainder), crafts * remainder.getCount(), Long::sum);
                }
            }
        }
    }

    /**
     * Keeps a plan's buffer: call right after its output was stored, in the same tick. A plan made for another
     * version is refused (never expected: plans are made and committed in one call).
     */
    public boolean commit(Plan plan) {
        if (plan.version() != version) {
            return false;
        }
        buffer = new LinkedHashMap<>(plan.buffer());
        changed();
        return true;
    }

    /** The waiting ingredients (read-only; for the GUI and game tests). */
    public Map<ItemResource, Long> buffer() {
        return Collections.unmodifiableMap(buffer);
    }

    private static List<DropTally.Entry<ItemResource>> entries(Map<ItemResource, Long> amounts) {
        List<DropTally.Entry<ItemResource>> list = new ArrayList<>(amounts.size());
        amounts.forEach((item, amount) -> {
            if (amount > 0) {
                list.add(new DropTally.Entry<>(item, amount));
            }
        });
        return List.copyOf(list);
    }

    // =================================================================================================================
    // Persistence
    // =================================================================================================================

    public void save(ValueOutput out) {
        out.putBoolean("enabled", enabled);
        out.store("recipes", PATTERN_CODEC.listOf(), patterns);
        if (!buffer.isEmpty()) {
            out.store("buffer", AMOUNTS_CODEC, entries(buffer));
        }
    }

    public void load(ValueInput in) {
        enabled = in.getBooleanOr("enabled", true);
        patterns.clear();
        in.read("recipes", PATTERN_CODEC.listOf()).ifPresent(list -> list.stream().limit(MAX_RECIPES)
                .forEach(pattern -> patterns.add(new Pattern(normalize(pattern.grid()), pattern.recipeId()))));
        buffer = new LinkedHashMap<>();
        in.read("buffer", AMOUNTS_CODEC).ifPresent(list -> list.forEach(entry -> {
            if (!entry.key().isEmpty() && entry.amount() > 0) {
                buffer.merge(entry.key(), entry.amount(), Long::sum);
            }
        }));
        resolved = false;
        version++;
    }

    /** Tarjan's strongly connected components over an adjacency matrix (at most {@link #MAX_RECIPES} nodes). */
    private static final class StronglyConnected {
        private final boolean[][] edges;
        private final int[] index;
        private final int[] low;
        private final boolean[] onStack;
        private final int[] stack;
        private final int[] group;
        private int stackSize;
        private int nextIndex;
        private int nextGroup;

        private StronglyConnected(boolean[][] edges) {
            int count = edges.length;
            this.edges = edges;
            this.index = new int[count];
            this.low = new int[count];
            this.onStack = new boolean[count];
            this.stack = new int[count];
            this.group = new int[count];
            Arrays.fill(index, -1);
        }

        /** Group number per node; a group is numbered after every group it has an edge to. */
        static int[] groups(boolean[][] edges) {
            StronglyConnected tarjan = new StronglyConnected(edges);
            for (int node = 0; node < edges.length; node++) {
                if (tarjan.index[node] < 0) {
                    tarjan.visit(node);
                }
            }
            return tarjan.group;
        }

        private void visit(int node) {
            index[node] = nextIndex;
            low[node] = nextIndex;
            nextIndex++;
            stack[stackSize++] = node;
            onStack[node] = true;
            for (int next = 0; next < edges.length; next++) {
                if (!edges[node][next]) {
                    continue;
                }
                if (index[next] < 0) {
                    visit(next);
                    low[node] = Math.min(low[node], low[next]);
                } else if (onStack[next]) {
                    low[node] = Math.min(low[node], index[next]);
                }
            }
            if (low[node] == index[node]) {
                int member;
                do {
                    member = stack[--stackSize];
                    onStack[member] = false;
                    group[member] = nextGroup;
                } while (member != node);
                nextGroup++;
            }
        }
    }
}
