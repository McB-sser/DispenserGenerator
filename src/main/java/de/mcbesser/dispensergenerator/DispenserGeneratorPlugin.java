package de.mcbesser.dispensergenerator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.EnumMap;

public final class DispenserGeneratorPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<LocationKey, GeneratorData> generators = new HashMap<>();
    private final Map<LocationKey, LocationKey> generatedBlocks = new HashMap<>();
    private final Map<String, List<Vector>> cubePatternCache = new HashMap<>();
    private final Map<BlockFace, List<Vector>> cobblerPatternCache = new HashMap<>();
    private final Map<GeneratorType, MaterialPool> materialPools = new EnumMap<>(GeneratorType.class);
    private final Random random = new Random();

    private NamespacedKey itemMarkerKey;
    private NamespacedKey itemTypeKey;
    private NamespacedKey recipeFurnaceKey;
    private NamespacedKey recipeSmokerKey;
    private NamespacedKey recipeBlastKey;
    private NamespacedKey itemKelpChargeKey;
    private NamespacedKey itemBoneChargeKey;
    private NamespacedKey itemLavaChargeKey;
    private NamespacedKey itemSilkLevelKey;
    private NamespacedKey itemFortuneLevelKey;
    private NamespacedKey itemMendingLevelKey;
    private NamespacedKey itemEfficiencyLevelKey;
    private NamespacedKey itemSizeLevelKey;
    private File dataFile;
    private long tickCounter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemMarkerKey = new NamespacedKey(this, "dispenser_generator_item");
        itemTypeKey = new NamespacedKey(this, "dispenser_generator_type");
        recipeFurnaceKey = new NamespacedKey(this, "dispenser_generator_furnace");
        recipeSmokerKey = new NamespacedKey(this, "dispenser_generator_smoker");
        recipeBlastKey = new NamespacedKey(this, "dispenser_generator_blast");
        itemKelpChargeKey = new NamespacedKey(this, "dispenser_generator_kelp_charge");
        itemBoneChargeKey = new NamespacedKey(this, "dispenser_generator_bone_charge");
        itemLavaChargeKey = new NamespacedKey(this, "dispenser_generator_lava_charge");
        itemSilkLevelKey = new NamespacedKey(this, "dispenser_generator_silk_level");
        itemFortuneLevelKey = new NamespacedKey(this, "dispenser_generator_fortune_level");
        itemMendingLevelKey = new NamespacedKey(this, "dispenser_generator_mending_level");
        itemEfficiencyLevelKey = new NamespacedKey(this, "dispenser_generator_efficiency_level");
        itemSizeLevelKey = new NamespacedKey(this, "dispenser_generator_size_level");
        dataFile = new File(getDataFolder(), "generators.yml");

        registerRecipe();
        loadGenerators();
        loadMaterialPools();

        Objects.requireNonNull(getCommand("oregen")).setExecutor(this);
        Objects.requireNonNull(getCommand("oregen")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickGenerators, 1L, 1L);
        for (Player online : Bukkit.getOnlinePlayers()) {
            discoverAllGeneratorRecipes(online);
        }
    }

    @Override
    public void onDisable() {
        saveGenerators();
    }

    private void registerRecipe() {
        Bukkit.addRecipe(createGeneratorRecipe(recipeFurnaceKey, GeneratorType.FURNACE, Material.FURNACE));
        Bukkit.addRecipe(createGeneratorRecipe(recipeSmokerKey, GeneratorType.SMOKER, Material.SMOKER));
        Bukkit.addRecipe(createGeneratorRecipe(recipeBlastKey, GeneratorType.BLAST, Material.BLAST_FURNACE));
    }

    private ShapedRecipe createGeneratorRecipe(NamespacedKey key, GeneratorType type, Material furnaceType) {
        ShapedRecipe recipe = new ShapedRecipe(key, createGeneratorItem(type));
        recipe.shape("MMM", "MDM", "MFM");
        recipe.setIngredient('M', Material.MAGMA_BLOCK);
        recipe.setIngredient('D', Material.DISPENSER);
        recipe.setIngredient('F', furnaceType);
        return recipe;
    }

    private void discoverAllGeneratorRecipes(Player player) {
        player.discoverRecipe(recipeFurnaceKey);
        player.discoverRecipe(recipeSmokerKey);
        player.discoverRecipe(recipeBlastKey);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discoverAllGeneratorRecipes(event.getPlayer());
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        GeneratorType type = detectRecipeType(event.getInventory());
        if (type != null) {
            event.getInventory().setResult(createGeneratorItem(type));
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getInventory() instanceof CraftingInventory inv) {
            GeneratorType type = detectRecipeType(inv);
            if (type != null) {
                event.setCurrentItem(createGeneratorItem(type));
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (!isGeneratorItem(hand) || event.getBlockPlaced().getType() != Material.DISPENSER) {
            return;
        }

        GeneratorType type = getItemGeneratorType(hand);
        LocationKey key = LocationKey.of(event.getBlockPlaced().getLocation());
        GeneratorData data = new GeneratorData();
        data.worldName = key.world;
        data.x = key.x;
        data.y = key.y;
        data.z = key.z;
        data.type = type;
        data.mode = GeneratorMode.CUBE;
        data.particles = getConfig().getBoolean("generator.particle-preview", true);
        data.kelpCharge = getItemCharge(hand, itemKelpChargeKey);
        data.boneCharge = getItemCharge(hand, itemBoneChargeKey);
        data.lavaCharge = getItemCharge(hand, itemLavaChargeKey);
        data.silkTouch = getItemCharge(hand, itemSilkLevelKey);
        data.fortune = getItemCharge(hand, itemFortuneLevelKey);
        data.mending = getItemCharge(hand, itemMendingLevelKey);
        data.efficiency = getItemCharge(hand, itemEfficiencyLevelKey);
        data.sizeUpgrade = Math.max(0, Math.min(3, getItemCharge(hand, itemSizeLevelKey)));
        generators.put(key, data);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        LocationKey key = LocationKey.of(event.getBlock().getLocation());
        GeneratorData removed = generators.remove(key);
        if (removed != null) {
            event.setCancelled(true);
            event.setDropItems(false);
            generatedBlocks.entrySet().removeIf(e -> e.getValue().equals(key));

            List<ItemStack> toDrop = new ArrayList<>();
            toDrop.add(createGeneratorItem(removed));

            if (event.getBlock().getState() instanceof Dispenser dispenser) {
                for (ItemStack content : dispenser.getInventory().getContents()) {
                    if (content == null || content.getType() == Material.AIR) {
                        continue;
                    }
                    toDrop.add(content.clone());
                }
                dispenser.getInventory().clear();
            }

            event.getBlock().setType(Material.AIR, false);
            for (ItemStack stack : toDrop) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), stack);
            }
            return;
        }

        LocationKey blockKey = LocationKey.of(event.getBlock().getLocation());
        LocationKey sourceKey = generatedBlocks.remove(blockKey);
        if (sourceKey != null) {
            GeneratorData source = generators.get(sourceKey);
            if (source != null) {
                source.noSpaceRetryTick = 0L;
                applyBreakBonuses(source, event.getBlock().getType(), event.getBlock().getLocation());
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.DISPENSER) {
            return;
        }
        LocationKey key = LocationKey.of(event.getClickedBlock().getLocation());
        GeneratorData data = generators.get(key);
        if (data == null) {
            return;
        }

        if (event.getPlayer().isSneaking()) {
            ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
            if (hand.getType().isBlock() && hand.getType() != Material.AIR) {
                return;
            }
            event.setCancelled(true);
            if (event.getClickedBlock().getState() instanceof Dispenser dispenser) {
                event.getPlayer().openInventory(dispenser.getInventory());
            }
        } else {
            event.setCancelled(true);
            openMainMenu(event.getPlayer(), key, data);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (top.getHolder() instanceof MainMenuHolder holder) {
            GeneratorData data = generators.get(holder.key);
            if (data == null) {
                player.closeInventory();
                return;
            }
            if (event.getClickedInventory() == null) {
                return;
            }
            if (event.getClickedInventory().equals(top)) {
                event.setCancelled(true);
                handleMainMenuClick(event, player, holder.key, data);
                return;
            }
            if (event.isShiftClick()) {
                event.setCancelled(true);
                handleMainMenuClick(event, player, holder.key, data);
            }
            return;
        }

        if (top.getHolder() instanceof UpgradeMenuHolder holder) {
            GeneratorData data = generators.get(holder.key);
            if (data == null) {
                player.closeInventory();
                return;
            }
            if (event.getClickedInventory() == null) {
                return;
            }
            if (event.getClickedInventory().equals(top)) {
                event.setCancelled(true);
                handleUpgradeClick(event, player, holder.key, data);
            }
            return;
        }
    }

    private void tickGenerators() {
        tickCounter++;
        int interval = Math.max(1, getConfig().getInt("generator.interval-ticks", 10));
        int baseBlocksPerTick = getConfig().getInt("generator.base-blocks-per-cycle", 1);
        int maxBlocksPerTick = getConfig().getInt("generator.max-blocks-per-cycle", 4);
        boolean stopWhenNoFuel = getConfig().getBoolean("generator.stop-when-no-fuel", true);

        for (GeneratorData data : generators.values()) {
            World world = Bukkit.getWorld(data.worldName);
            if (world == null) {
                continue;
            }
            int chunkX = data.x >> 4;
            int chunkZ = data.z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }
            Block block = world.getBlockAt(data.x, data.y, data.z);
            if (block.getType() != Material.DISPENSER) {
                data.active = false;
                continue;
            }

            if (data.particles && hasNearbyParticleViewer(block.getLocation())) {
                showPreview(data, block);
            }
            if (!data.active) {
                continue;
            }
            boolean cycleTick = interval == 1 || Math.floorMod(tickCounter + cycleOffset(data), interval) == 0;
            if (!cycleTick) {
                continue;
            }
            if (!(block.getState() instanceof Dispenser dispenser)) {
                data.active = false;
                continue;
            }

            absorbFuelIntoCharge(data, dispenser);

            if (getConfig().getBoolean("mode-requirements.enabled", false) && !modeRequirementsMet(data, block)) {
                data.active = false;
                continue;
            }
            int blocksPerTick = Math.min(maxBlocksPerTick, Math.max(1, baseBlocksPerTick + Math.max(0, data.efficiency - 1)));
            for (int i = 0; i < blocksPerTick; i++) {
                if (data.remainingCharge <= 0) {
                    if (!tryConsumeCharge(data)) {
                        if (stopWhenNoFuel) {
                            data.active = false;
                        }
                        break;
                    }
                    data.remainingCharge = 64;
                }
                if (!generateOneBlock(data, block)) {
                    break;
                }
            }
        }
    }

    private int cycleOffset(GeneratorData data) {
        int hash = 17;
        hash = 31 * hash + data.worldName.hashCode();
        hash = 31 * hash + data.x;
        hash = 31 * hash + data.y;
        hash = 31 * hash + data.z;
        return hash;
    }
    private boolean generateOneBlock(GeneratorData data, Block dispenserBlock) {
        if (tickCounter < data.noSpaceRetryTick) {
            return false;
        }
        BlockFace face = getOutputFace(dispenserBlock);
        Location output = dispenserBlock.getLocation().add(0.5, 0.5, 0.5).add(face.getModX(), face.getModY(), face.getModZ());
        List<Vector> pattern = getPattern(data, face);
        if (pattern.isEmpty()) {
            return false;
        }

        int startIndex = Math.floorMod(data.cursorIndex, pattern.size());
        for (int checked = 0; checked < pattern.size(); checked++) {
            int index = (startIndex + checked) % pattern.size();
            Vector rel = pattern.get(index);
            Block target = mapLocalToWorld(output, face, rel).getBlock();
            if (!isAir(target.getType())) {
                continue;
            }

            Material generated = pickMaterial(data.type, data.silkTouch > 0);
            target.setType(generated, true);
            generatedBlocks.put(LocationKey.of(target.getLocation()),
                    new LocationKey(data.worldName, data.x, data.y, data.z));
            data.remainingCharge--;
            data.cursorIndex = (index + 1) % pattern.size();
            data.noSpaceRetryTick = 0L;
            return true;
        }
        data.noSpaceRetryTick = tickCounter + Math.max(1L, getConfig().getLong("generator.full-area-retry-ticks", 20L));
        return false;
    }

    private void applyBreakBonuses(GeneratorData data, Material brokenType, Location location) {
        if (!isBonusEligibleBlock(data.type, brokenType)) {
            return;
        }

        if (data.fortune > 0) {
            double chance = Math.min(0.35, 0.05 * data.fortune);
            if (random.nextDouble() < chance) {
                Material drop = switch (data.type) {
                    case FURNACE -> randomPick(Material.COAL, Material.FLINT, Material.IRON_NUGGET);
                    case SMOKER -> randomPick(Material.STICK, Material.WHEAT_SEEDS, Material.MOSS_CARPET);
                    case BLAST -> randomPick(Material.QUARTZ, Material.GOLD_NUGGET, Material.REDSTONE);
                };
                location.getWorld().dropItemNaturally(location, new ItemStack(drop, 1 + random.nextInt(Math.max(1, data.fortune))));
            }
        }

        if (data.mending > 0 && random.nextDouble() < Math.min(0.2, 0.03 * data.mending)) {
            ExperienceOrb orb = location.getWorld().spawn(location, ExperienceOrb.class);
            orb.setExperience(1 + random.nextInt(2));
        }
    }

    private Material randomPick(Material... materials) {
        return materials[random.nextInt(materials.length)];
    }

    private void showPreview(GeneratorData data, Block dispenserBlock) {
        World world = dispenserBlock.getWorld();
        BlockFace face = getOutputFace(dispenserBlock);
        Location output = dispenserBlock.getLocation().add(0.5, 0.5, 0.5).add(face.getModX(), face.getModY(), face.getModZ());

        if (data.mode == GeneratorMode.COBBLER) {
            for (Vector rel : getPattern(data, face)) {
                world.spawnParticle(Particle.END_ROD, mapLocalToWorld(output, face, rel), 1, 0, 0, 0, 0);
            }
            return;
        }

        int size = getCurrentSize(data);
        int min = getCrossMin(size);
        int max = getCrossMax(size);
        int step = Math.max(1, size / 8);

        int frontMin = 0;
        int frontMax = size - 1;
        for (int x = min; x <= max; x += step) {
            spawnPreviewPoint(world, output, face, x, min, frontMin);
            spawnPreviewPoint(world, output, face, x, min, frontMax);
            spawnPreviewPoint(world, output, face, x, max, frontMin);
            spawnPreviewPoint(world, output, face, x, max, frontMax);
        }
        for (int y = min; y <= max; y += step) {
            spawnPreviewPoint(world, output, face, min, y, frontMin);
            spawnPreviewPoint(world, output, face, min, y, frontMax);
            spawnPreviewPoint(world, output, face, max, y, frontMin);
            spawnPreviewPoint(world, output, face, max, y, frontMax);
        }
        for (int z = frontMin; z <= frontMax; z += step) {
            spawnPreviewPoint(world, output, face, min, min, z);
            spawnPreviewPoint(world, output, face, min, max, z);
            spawnPreviewPoint(world, output, face, max, min, z);
            spawnPreviewPoint(world, output, face, max, max, z);
        }

        spawnPreviewPoint(world, output, face, max, max, frontMax);
    }

    private void spawnPreviewPoint(World world, Location origin, BlockFace face, int x, int y, int z) {
        world.spawnParticle(Particle.END_ROD, mapLocalToWorld(origin, face, new Vector(x, y, z)), 1, 0, 0, 0, 0);
    }

    private boolean hasNearbyParticleViewer(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        double maxDistance = getConfig().getDouble("generator.particle-view-distance", 64.0D);
        double maxDistanceSquared = maxDistance * maxDistance;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead() || player.getWorld() != location.getWorld()) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private List<Vector> getPattern(GeneratorData data, BlockFace face) {
        if (data.mode == GeneratorMode.COBBLER) {
            return cobblerPatternCache.computeIfAbsent(face, ignored -> buildCobblerPattern());
        }
        int size = getCurrentSize(data);
        String key = data.mode.name() + ":" + size;
        return cubePatternCache.computeIfAbsent(key, k -> buildCubePattern(size));
    }

    private List<Vector> buildCobblerPattern() {
        List<Vector> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(new Vector(0, 0, i));
        }
        return list;
    }

    private List<Vector> buildCubePattern(int size) {
        int min = getCrossMin(size);
        int max = getCrossMax(size);
        List<Vector> out = new ArrayList<>(size * size * size);
        List<Vector> crossSectionSpiral = buildSquareSpiral(min, max);
        for (int forward = 0; forward < size; forward++) {
            for (Vector v : crossSectionSpiral) {
                out.add(new Vector(v.getBlockX(), v.getBlockZ(), forward));
            }
        }
        return out;
    }

    private Location mapLocalToWorld(Location origin, BlockFace face, Vector local) {
        Basis basis = getBasis(face);
        return origin.clone()
                .add(basis.right().clone().multiply(local.getBlockX()))
                .add(basis.up().clone().multiply(local.getBlockY()))
                .add(basis.forward().clone().multiply(local.getBlockZ()));
    }

    private Vector worldToLocal(Location origin, BlockFace face, Location worldPoint) {
        Basis basis = getBasis(face);
        Vector delta = worldPoint.toVector().subtract(origin.toVector());
        return new Vector(
                delta.dot(basis.right()),
                delta.dot(basis.up()),
                delta.dot(basis.forward())
        );
    }

    private Basis getBasis(BlockFace face) {
        Vector forward = new Vector(face.getModX(), face.getModY(), face.getModZ());
        Vector worldUp = new Vector(0, 1, 0);
        Vector upRef = Math.abs(forward.dot(worldUp)) > 0.999 ? new Vector(0, 0, 1) : worldUp;
        Vector right = forward.clone().crossProduct(upRef).normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        return new Basis(forward, right, up);
    }

    private List<Vector> buildSquareSpiral(int min, int max) {
        int target = (max - min + 1) * (max - min + 1);
        List<Vector> out = new ArrayList<>(target);
        Set<String> seen = new HashSet<>();

        int x = 0;
        int z = 0;
        int step = 1;
        int dir = 0;
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

        addIfInside(min, max, out, seen, x, z);
        while (out.size() < target) {
            for (int r = 0; r < 2; r++) {
                for (int i = 0; i < step; i++) {
                    x += dirs[dir][0];
                    z += dirs[dir][1];
                    addIfInside(min, max, out, seen, x, z);
                    if (out.size() >= target) {
                        return out;
                    }
                }
                dir = (dir + 1) % 4;
            }
            step++;
        }
        return out;
    }

    private void addIfInside(int min, int max, List<Vector> out, Set<String> seen, int x, int z) {
        if (x >= min && x <= max && z >= min && z <= max) {
            String key = x + ":" + z;
            if (seen.add(key)) {
                out.add(new Vector(x, 0, z));
            }
        }
    }

    private List<Integer> buildCenterOutOrder(int min, int max) {
        List<Integer> order = new ArrayList<>(max - min + 1);
        order.add(0);
        for (int offset = 1; order.size() < (max - min + 1); offset++) {
            if (offset <= max) {
                order.add(offset);
            }
            if (-offset >= min && order.size() < (max - min + 1)) {
                order.add(-offset);
            }
        }
        return order;
    }

    private int getCurrentSize(GeneratorData data) {
        int level = Math.max(0, Math.min(3, data.sizeUpgrade));
        return switch (level) {
            case 0 -> 5;
            case 1 -> 10;
            case 2 -> 13;
            default -> 15;
        };
    }

    private int getCrossMin(int size) {
        return -(size / 2);
    }

    private int getCrossMax(int size) {
        return getCrossMin(size) + size - 1;
    }

    private boolean tryConsumeCharge(GeneratorData data) {
        Costs c = getCostsFor(data.type);
        if (data.kelpCharge < c.kelp || data.boneCharge < c.bone || data.lavaCharge < c.lava) {
            return false;
        }
        data.kelpCharge -= c.kelp;
        data.boneCharge -= c.bone;
        data.lavaCharge -= c.lava;
        return true;
    }

    private void absorbFuelIntoCharge(GeneratorData data, Dispenser dispenser) {
        Inventory inventory = dispenser.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) {
                continue;
            }
            if (item.getType() == Material.DRIED_KELP_BLOCK) {
                data.kelpCharge += item.getAmount();
                inventory.setItem(slot, null);
                continue;
            }
            if (item.getType() == Material.BONE_BLOCK) {
                data.boneCharge += item.getAmount();
                inventory.setItem(slot, null);
                continue;
            }
            if (item.getType() == Material.LAVA_BUCKET) {
                int amount = item.getAmount();
                data.lavaCharge += amount;
                inventory.setItem(slot, null);
                addBucketsToInventory(inventory, amount, dispenser.getLocation());
            }
        }
    }

    private Costs getCostsFor(GeneratorType type) {
        String path = "costs." + type.configPath + ".";
        return new Costs(
                Math.max(1, getConfig().getInt(path + "kelp", 1)),
                Math.max(1, getConfig().getInt(path + "bone", 1)),
                Math.max(1, getConfig().getInt(path + "lava", 1))
        );
    }
    private boolean modeRequirementsMet(GeneratorData data, Block dispenserBlock) {
        String path = "mode-requirements." + (data.mode == GeneratorMode.CUBE ? "cube-nearby-any" : "cobbler-nearby-any");
        List<String> list = getConfig().getStringList(path);
        if (list.isEmpty()) {
            return true;
        }
        Set<Material> required = list.stream()
                .map(s -> Material.matchMaterial(s, false))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (required.isEmpty()) {
            return true;
        }
        for (BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN)) {
            if (required.contains(dispenserBlock.getRelative(face).getType())) {
                return true;
            }
        }
        return false;
    }

    private Material pickMaterial(GeneratorType type, boolean silk) {
        MaterialPool pool = materialPools.computeIfAbsent(type, t -> {
            String prefix = "materials." + t.configPath;
            List<Material> common = readMaterials(prefix + "-common", fallbackCommon(t));
            List<Material> rare = readMaterials(prefix + "-rare", fallbackRare(t));
            return new MaterialPool(common, rare);
        });
        List<Material> common = pool.common();
        List<Material> rare = pool.rare();

        boolean useRare = !rare.isEmpty() && random.nextDouble() < 0.08;
        Material result = useRare ? rare.get(random.nextInt(rare.size())) : common.get(random.nextInt(common.size()));
        if (!silk && isFragile(result)) {
            return type == GeneratorType.SMOKER ? Material.DIRT : Material.COBBLESTONE;
        }
        return result;
    }

    private boolean isBonusEligibleBlock(GeneratorType type, Material material) {
        MaterialPool pool = materialPools.get(type);
        if (pool == null) {
            return false;
        }
        return pool.common().contains(material) || pool.rare().contains(material);
    }


    private boolean isFragile(Material material) {
        return material == Material.GLASS || material == Material.ANCIENT_DEBRIS || material == Material.CRYING_OBSIDIAN;
    }

    private List<Material> fallbackCommon(GeneratorType type) {
        return switch (type) {
            case FURNACE -> Arrays.asList(Material.STONE, Material.COBBLESTONE, Material.ANDESITE, Material.DIORITE, Material.GRANITE);
            case SMOKER -> Arrays.asList(Material.DIRT, Material.COARSE_DIRT, Material.MOSS_BLOCK, Material.OAK_LOG, Material.PACKED_MUD);
            case BLAST -> Arrays.asList(Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.BASALT, Material.BLACKSTONE);
        };
    }

    private List<Material> fallbackRare(GeneratorType type) {
        return switch (type) {
            case FURNACE -> Arrays.asList(Material.MOSSY_COBBLESTONE, Material.OBSIDIAN);
            case SMOKER -> Arrays.asList(Material.PODZOL, Material.MUSHROOM_STEM);
            case BLAST -> Arrays.asList(Material.CRYING_OBSIDIAN, Material.POLISHED_BLACKSTONE_BRICKS);
        };
    }

    private List<Material> readMaterials(String path, List<Material> fallback) {
        List<Material> result = new ArrayList<>();
        for (String raw : getConfig().getStringList(path)) {
            Material material = Material.matchMaterial(raw, false);
            if (material != null && material.isBlock() && !isDisallowedGeneratedBlock(material)) {
                result.add(material);
            }
        }
        List<Material> filteredFallback = fallback.stream()
                .filter(m -> !isDisallowedGeneratedBlock(m))
                .toList();
        return result.isEmpty() ? filteredFallback : result;
    }

    private boolean isDisallowedGeneratedBlock(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    private void loadMaterialPools() {
        materialPools.clear();
        for (GeneratorType type : GeneratorType.values()) {
            String prefix = "materials." + type.configPath;
            List<Material> common = readMaterials(prefix + "-common", fallbackCommon(type));
            List<Material> rare = readMaterials(prefix + "-rare", fallbackRare(type));
            materialPools.put(type, new MaterialPool(common, rare));
        }
    }

    private BlockFace getOutputFace(Block dispenserBlock) {
        if (dispenserBlock.getBlockData() instanceof Directional directional) {
            return directional.getFacing();
        }
        return BlockFace.NORTH;
    }

    private boolean isAir(Material type) {
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    private void handleMainMenuClick(InventoryClickEvent event, Player player, LocationKey key, GeneratorData data) {
        int slot = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        Dispenser dispenser = getDispenser(key);
        if (dispenser == null) {
            player.closeInventory();
            return;
        }
        absorbFuelIntoCharge(data, dispenser);

        if (slot == 10 || slot == 12 || slot == 14) {
            Material needed = slot == 10 ? Material.DRIED_KELP_BLOCK : slot == 12 ? Material.BONE_BLOCK : Material.LAVA_BUCKET;
            if (cursor != null && cursor.getType() == needed) {
                int amount = cursor.getAmount();
                addCharge(data, needed, amount);
                if (needed == Material.LAVA_BUCKET) {
                    event.setCursor(null);
                    giveBucketsToPlayer(player, amount, player.getLocation());
                } else {
                    event.setCursor(null);
                }
            } else {
                int requested = event.isShiftClick() ? Integer.MAX_VALUE : (event.isRightClick() ? 64 : 1);
                moveFromPlayerInventoryAsCharge(player, data, needed, requested, event.getClickedInventory() != null
                        ? event.getClickedInventory().getLocation()
                        : player.getLocation());
            }
            openMainMenu(player, key, data);
            return;
        }

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory()) && event.isShiftClick()) {
            ItemStack item = event.getCurrentItem();
            if (item != null) {
                if (item.getType() == Material.DRIED_KELP_BLOCK) {
                    addCharge(data, Material.DRIED_KELP_BLOCK, item.getAmount());
                    item.setAmount(0);
                } else if (item.getType() == Material.BONE_BLOCK) {
                    addCharge(data, Material.BONE_BLOCK, item.getAmount());
                    item.setAmount(0);
                } else if (item.getType() == Material.LAVA_BUCKET) {
                    int amount = item.getAmount();
                    addCharge(data, Material.LAVA_BUCKET, amount);
                    item.setAmount(0);
                    giveBucketsToPlayer(player, amount, event.getClickedInventory() != null
                            ? event.getClickedInventory().getLocation()
                            : player.getLocation());
                }
            }
            openMainMenu(player, key, data);
            return;
        }

        if (slot == 16) {
            data.active = !data.active;
        } else if (slot == 20) {
            data.mode = data.mode == GeneratorMode.CUBE ? GeneratorMode.COBBLER : GeneratorMode.CUBE;
            data.cursorIndex = 0;
        } else if (slot == 22) {
            data.particles = !data.particles;
        } else if (slot == 24) {
            openUpgradeMenu(player, key, data);
            return;
        }
        openMainMenu(player, key, data);
    }

    private void moveFromPlayerInventoryAsCharge(Player player, GeneratorData data, Material material, int requestedAmount, Location dropLocation) {
        int remaining = requestedAmount;
        int consumedLavaBuckets = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != material) {
                continue;
            }

            int take = Math.min(item.getAmount(), remaining);
            addCharge(data, material, take);
            if (material == Material.LAVA_BUCKET) {
                consumedLavaBuckets += take;
            }

            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            } else {
                player.getInventory().setItem(slot, item);
            }
            remaining -= take;
        }
        if (consumedLavaBuckets > 0) {
            giveBucketsToPlayer(player, consumedLavaBuckets, dropLocation);
        }
        player.updateInventory();
    }

    private void addCharge(GeneratorData data, Material material, int amount) {
        if (amount <= 0) {
            return;
        }
        if (material == Material.DRIED_KELP_BLOCK) {
            data.kelpCharge += amount;
            return;
        }
        if (material == Material.BONE_BLOCK) {
            data.boneCharge += amount;
            return;
        }
        if (material == Material.LAVA_BUCKET) {
            data.lavaCharge += amount;
        }
    }

    private void giveBucketsToPlayer(Player player, int amount, Location dropLocation) {
        if (amount <= 0) {
            return;
        }
        int max = Math.max(1, Material.BUCKET.getMaxStackSize());
        int remaining = amount;
        Location target = dropLocation != null ? dropLocation : player.getLocation();
        while (remaining > 0) {
            int part = Math.min(max, remaining);
            remaining -= part;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(Material.BUCKET, part));
            if (!overflow.isEmpty()) {
                for (ItemStack drop : overflow.values()) {
                    target.getWorld().dropItemNaturally(target, drop);
                }
            }
        }
    }

    private void addBucketsToInventory(Inventory inventory, int amount, Location dropLocation) {
        if (amount <= 0) {
            return;
        }
        int max = Math.max(1, Material.BUCKET.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int part = Math.min(max, remaining);
            remaining -= part;
            Map<Integer, ItemStack> overflow = inventory.addItem(new ItemStack(Material.BUCKET, part));
            if (!overflow.isEmpty() && dropLocation != null && dropLocation.getWorld() != null) {
                for (ItemStack drop : overflow.values()) {
                    dropLocation.getWorld().dropItemNaturally(dropLocation, drop);
                }
            }
        }
    }

    private Dispenser getDispenser(LocationKey key) {
        World world = Bukkit.getWorld(key.world);
        if (world == null) {
            return null;
        }
        Block block = world.getBlockAt(key.x, key.y, key.z);
        if (block.getType() != Material.DISPENSER || !(block.getState() instanceof Dispenser dispenser)) {
            return null;
        }
        return dispenser;
    }

    private void handleUpgradeClick(InventoryClickEvent event, Player player, LocationKey key, GeneratorData data) {
        int slot = event.getRawSlot();
        if (slot == 26) {
            openMainMenu(player, key, data);
            return;
        }

        Enchantment expected = getUpgradeEnchantBySlot(slot);
        if (expected == null) {
            return;
        }

        int currentLevel = getUpgradeLevelBySlot(data, slot);
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            if (currentLevel > 0) {
                setUpgradeLevelBySlot(data, slot, 0);
                ItemStack returnedBook = createUpgradeBook(expected, currentLevel);
                event.setCursor(returnedBook);
            }
            openUpgradeMenu(player, key, data);
            return;
        }

        int insertedLevel = readBookLevel(cursor, expected);
        if (insertedLevel > 0) {
            if (currentLevel > 0) {
                giveItemToPlayerOrDrop(player, createUpgradeBook(expected, currentLevel), player.getLocation());
            }
            setUpgradeLevelBySlot(data, slot, insertedLevel);
            consumeSingle(event);
        }
        openUpgradeMenu(player, key, data);
    }

    private Enchantment getUpgradeEnchantBySlot(int slot) {
        return switch (slot) {
            case 10 -> enchant("silk_touch");
            case 12 -> enchant("fortune");
            case 14 -> enchant("mending");
            case 16 -> enchant("efficiency");
            case 22 -> enchant("unbreaking");
            default -> null;
        };
    }

    private int getUpgradeLevelBySlot(GeneratorData data, int slot) {
        return switch (slot) {
            case 10 -> data.silkTouch;
            case 12 -> data.fortune;
            case 14 -> data.mending;
            case 16 -> data.efficiency;
            case 22 -> data.sizeUpgrade;
            default -> 0;
        };
    }

    private void setUpgradeLevelBySlot(GeneratorData data, int slot, int level) {
        int clamped = Math.max(0, level);
        switch (slot) {
            case 10 -> data.silkTouch = clamped;
            case 12 -> data.fortune = clamped;
            case 14 -> data.mending = clamped;
            case 16 -> data.efficiency = clamped;
            case 22 -> data.sizeUpgrade = Math.min(3, clamped);
            default -> {
            }
        }
    }

    private int readBookLevel(ItemStack item, Enchantment enchantment) {
        if (enchantment == null) {
            return 0;
        }
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return 0;
        }
        return Math.max(0, meta.getStoredEnchantLevel(enchantment));
    }

    private ItemStack createUpgradeBook(Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta meta) || enchantment == null || level <= 0) {
            return item;
        }
        meta.addStoredEnchant(enchantment, level, true);
        item.setItemMeta(meta);
        return item;
    }

    private void giveItemToPlayerOrDrop(Player player, ItemStack item, Location dropLocation) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            Location at = dropLocation != null ? dropLocation : player.getLocation();
            for (ItemStack rest : overflow.values()) {
                at.getWorld().dropItemNaturally(at, rest);
            }
        }
    }

    private Enchantment enchant(String key) {
        return Enchantment.getByKey(NamespacedKey.minecraft(key));
    }

    private void consumeSingle(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor == null) {
            return;
        }
        if (cursor.getAmount() <= 1) {
            event.setCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            event.setCursor(cursor);
        }
    }
    private void openMainMenu(Player player, LocationKey key, GeneratorData data) {
        Inventory inv = Bukkit.createInventory(new MainMenuHolder(key), 27, "Werfer Oregen");
        Dispenser dispenser = getDispenser(key);
        if (dispenser != null) {
            absorbFuelIntoCharge(data, dispenser);
        }

        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
        inv.setItem(10, named(Material.DRIED_KELP_BLOCK, "Kelp-Slot", "Ladung: " + data.kelpCharge, "Linksklick: +1", "Rechtsklick: +1 Stack", "Shift-Klick: alles"));
        inv.setItem(12, named(Material.BONE_BLOCK, "Knochen-Slot", "Ladung: " + data.boneCharge, "Linksklick: +1", "Rechtsklick: +1 Stack", "Shift-Klick: alles"));
        inv.setItem(14, named(Material.LAVA_BUCKET, "Lava-Slot", "Ladung: " + data.lavaCharge, "Verbraucht nur Lava", "Leere Eimer bleiben im Werfer"));
        inv.setItem(16, named(data.active ? Material.LIME_DYE : Material.RED_DYE, "Generator " + (data.active ? "AN" : "AUS")));
        inv.setItem(20, named(Material.COMPASS, "Modus: " + (data.mode == GeneratorMode.CUBE ? "W\u00fcrfel" : "5er Linie")));
        inv.setItem(22, named(data.particles ? Material.BLAZE_POWDER : Material.GUNPOWDER, "Partikel: " + (data.particles ? "AN" : "AUS")));
        inv.setItem(24, named(Material.ENCHANTED_BOOK, "Upgrade-Men\u00fc"));
        Costs c = getCostsFor(data.type);
        inv.setItem(26, named(Material.PAPER,
                "Typ: " + data.type.display,
                "Kosten/64: " + c.kelp + " Kelp, " + c.bone + " Knochen, " + c.lava + " Lava",
                "Gr\u00f6\u00dfe: " + getCurrentSize(data)
        ));
        player.openInventory(inv);
    }

    private void openUpgradeMenu(Player player, LocationKey key, GeneratorData data) {
        Inventory inv = Bukkit.createInventory(new UpgradeMenuHolder(key), 27, "Werfer Upgrades");
        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
        inv.setItem(10, upgradeSlotItem("Behutsamkeit Slot", enchant("silk_touch"), data.silkTouch));
        inv.setItem(12, upgradeSlotItem("Gl\u00fcck Slot", enchant("fortune"), data.fortune));
        inv.setItem(14, upgradeSlotItem("Reparatur Slot", enchant("mending"), data.mending));
        inv.setItem(16, upgradeSlotItem("Geschwindigkeit Slot", enchant("efficiency"), data.efficiency));
        inv.setItem(22, upgradeSlotItem("Gr\u00f6\u00dfe Slot", enchant("unbreaking"), data.sizeUpgrade));
        inv.setItem(26, named(Material.ARROW, "Zur\u00fcck"));
        player.openInventory(inv);
    }

    private ItemStack upgradeSlotItem(String name, Enchantment expected, int level) {
        String expectedName = expectedUpgradeName(expected);
        String effect = upgradeEffectText(expected);
        if (level <= 0) {
            return named(
                    Material.GREEN_STAINED_GLASS_PANE,
                    name,
                    "Lege ein passendes Enchanted Book ein.",
                    "Erwartet: " + expectedName,
                    "Effekt: " + effect,
                    "Klick mit leerem Cursor: nichts hinterlegt",
                    "Aktuell: leer"
            );
        }

        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(
                "Upgrade aktiv.",
                "Erwartet: " + expectedName,
                "Effekt: " + effect,
                "Klick mit leerem Cursor: Buch entnehmen",
                "Aktuelles Level: " + level
        ));
        item.setItemMeta(meta);
        return item;
    }

    private String expectedUpgradeName(Enchantment enchantment) {
        if (enchantment == null) {
            return "Unbekannt";
        }
        String key = enchantment.getKey().getKey();
        return switch (key) {
            case "silk_touch" -> "Behutsamkeit";
            case "fortune" -> "Gl\u00fcck";
            case "mending" -> "Reparatur";
            case "efficiency" -> "Effizienz";
            case "unbreaking" -> "Haltbarkeit";
            default -> key;
        };
    }

    private String upgradeEffectText(Enchantment enchantment) {
        if (enchantment == null) {
            return "Kein Effekt verf\u00fcgbar";
        }
        String key = enchantment.getKey().getKey();
        return switch (key) {
            case "silk_touch" -> "Seltene/empfindliche Bl\u00f6cke bleiben erhalten.";
            case "fortune" -> "Erh\u00f6ht die Chance auf Bonus-Drops.";
            case "mending" -> "Erzeugt gelegentlich Erfahrungsorbs.";
            case "efficiency" -> "Erh\u00f6ht Bl\u00f6cke pro Zyklus.";
            case "unbreaking" -> "Vergr\u00f6\u00dfert den Wirkungsbereich.";
            default -> "Spezialeffekt dieses Upgrades.";
        };
    }

    private void fill(Inventory inv, Material material, String name) {
        ItemStack pane = named(material, name);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
    }

    private ItemStack named(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        if (lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private GeneratorType detectRecipeType(CraftingInventory inv) {
        ItemStack[] matrix = inv.getMatrix();
        if (matrix.length < 9) {
            return null;
        }
        for (int i = 0; i < 9; i++) {
            Material m = matrix[i] == null ? Material.AIR : matrix[i].getType();
            if (i == 4 && m != Material.DISPENSER) {
                return null;
            }
            if (i == 7 && m != Material.FURNACE && m != Material.SMOKER && m != Material.BLAST_FURNACE) {
                return null;
            }
            if (i != 4 && i != 7 && m != Material.MAGMA_BLOCK) {
                return null;
            }
        }
        return switch (matrix[7].getType()) {
            case SMOKER -> GeneratorType.SMOKER;
            case BLAST_FURNACE -> GeneratorType.BLAST;
            default -> GeneratorType.FURNACE;
        };
    }

    private ItemStack createGeneratorItem(GeneratorType type) {
        return createGeneratorItem(type, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private ItemStack createGeneratorItem(GeneratorData data) {
        return createGeneratorItem(
                data.type,
                data.kelpCharge, data.boneCharge, data.lavaCharge,
                data.silkTouch, data.fortune, data.mending, data.efficiency, data.sizeUpgrade
        );
    }

    private ItemStack createGeneratorItem(GeneratorType type, int kelpCharge, int boneCharge, int lavaCharge,
                                          int silk, int fortune, int mending, int efficiency, int size) {
        ItemStack item = new ItemStack(Material.DISPENSER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Werfer Oregen [" + type.display + "]");
        meta.setLore(Arrays.asList(
                "Rechtsklick: Generator-Men\u00fc",
                "Shift+Rechtsklick: Werfer-Inventar",
                "Typ: " + type.display,
                "Ladung Kelp: " + kelpCharge,
                "Ladung Knochen: " + boneCharge,
                "Ladung Lava: " + lavaCharge,
                "Upgrades: B:" + silk + " G:" + fortune + " R:" + mending + " E:" + efficiency + " Gr:" + size
        ));
        meta.getPersistentDataContainer().set(itemMarkerKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(itemKelpChargeKey, PersistentDataType.INTEGER, Math.max(0, kelpCharge));
        meta.getPersistentDataContainer().set(itemBoneChargeKey, PersistentDataType.INTEGER, Math.max(0, boneCharge));
        meta.getPersistentDataContainer().set(itemLavaChargeKey, PersistentDataType.INTEGER, Math.max(0, lavaCharge));
        meta.getPersistentDataContainer().set(itemSilkLevelKey, PersistentDataType.INTEGER, Math.max(0, silk));
        meta.getPersistentDataContainer().set(itemFortuneLevelKey, PersistentDataType.INTEGER, Math.max(0, fortune));
        meta.getPersistentDataContainer().set(itemMendingLevelKey, PersistentDataType.INTEGER, Math.max(0, mending));
        meta.getPersistentDataContainer().set(itemEfficiencyLevelKey, PersistentDataType.INTEGER, Math.max(0, efficiency));
        meta.getPersistentDataContainer().set(itemSizeLevelKey, PersistentDataType.INTEGER, Math.max(0, size));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isGeneratorItem(ItemStack item) {
        if (item == null || item.getType() != Material.DISPENSER || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(itemMarkerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private GeneratorType getItemGeneratorType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return GeneratorType.FURNACE;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING);
        if (raw == null) {
            return GeneratorType.FURNACE;
        }
        try {
            return GeneratorType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return GeneratorType.FURNACE;
        }
    }

    private int getItemCharge(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    private void saveGenerators() {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (GeneratorData data : generators.values()) {
            ConfigurationSection s = yaml.createSection("generators." + i++);
            s.set("world", data.worldName);
            s.set("x", data.x);
            s.set("y", data.y);
            s.set("z", data.z);
            s.set("type", data.type.name());
            s.set("mode", data.mode.name());
            s.set("active", data.active);
            s.set("particles", data.particles);
            s.set("kelpCharge", data.kelpCharge);
            s.set("boneCharge", data.boneCharge);
            s.set("lavaCharge", data.lavaCharge);
            s.set("remainingCharge", data.remainingCharge);
            s.set("cursor", data.cursorIndex);
            s.set("silkTouch", data.silkTouch);
            s.set("fortune", data.fortune);
            s.set("mending", data.mending);
            s.set("efficiency", data.efficiency);
            s.set("sizeUpgrade", data.sizeUpgrade);
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Konnte generators.yml nicht speichern: " + e.getMessage());
        }
    }

    private void loadGenerators() {
        generators.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection base = yaml.getConfigurationSection("generators");
        if (base == null) {
            return;
        }
        for (String id : base.getKeys(false)) {
            ConfigurationSection s = base.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            GeneratorData data = new GeneratorData();
            data.worldName = s.getString("world", "");
            data.x = s.getInt("x");
            data.y = s.getInt("y");
            data.z = s.getInt("z");
            data.type = parseEnum(s.getString("type"), GeneratorType.FURNACE);
            data.mode = parseEnum(s.getString("mode"), GeneratorMode.CUBE);
            data.active = s.getBoolean("active", false);
            data.particles = s.getBoolean("particles", true);
            data.kelpCharge = Math.max(0, s.getInt("kelpCharge", 0));
            data.boneCharge = Math.max(0, s.getInt("boneCharge", 0));
            data.lavaCharge = Math.max(0, s.getInt("lavaCharge", 0));
            data.remainingCharge = Math.max(0, s.getInt("remainingCharge", 0));
            data.cursorIndex = Math.max(0, s.getInt("cursor", 0));
            data.silkTouch = Math.max(0, s.getInt("silkTouch", 0));
            data.fortune = Math.max(0, s.getInt("fortune", 0));
            data.mending = Math.max(0, s.getInt("mending", 0));
            data.efficiency = Math.max(0, s.getInt("efficiency", 0));
            data.sizeUpgrade = Math.max(0, Math.min(3, s.getInt("sizeUpgrade", 0)));
            generators.put(new LocationKey(data.worldName, data.x, data.y, data.z), data);
        }
    }

    private <T extends Enum<T>> T parseEnum(String value, T fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler.");
            return true;
        }
        if (!player.hasPermission("dispensergenerator.admin")) {
            player.sendMessage("Keine Rechte.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            GeneratorType type = switch (args[1].toLowerCase()) {
                case "ofen", "furnace" -> GeneratorType.FURNACE;
                case "r\u00e4ucherofen", "smoker" -> GeneratorType.SMOKER;
                case "schmiedeofen", "blast", "blastfurnace" -> GeneratorType.BLAST;
                default -> null;
            };
            if (type == null) {
                player.sendMessage("Nutze: /oregen give <ofen|r\u00e4ucherofen|schmiedeofen>");
                return true;
            }
            player.getInventory().addItem(createGeneratorItem(type));
            player.sendMessage("Erhalten: " + type.display);
            return true;
        }
        player.sendMessage("/oregen give <ofen|r\u00e4ucherofen|schmiedeofen>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("give");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Arrays.asList("ofen", "r\u00e4ucherofen", "schmiedeofen");
        }
        return Collections.emptyList();
    }

    private record MainMenuHolder(LocationKey key) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record UpgradeMenuHolder(LocationKey key) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record MaterialPool(List<Material> common, List<Material> rare) {
    }

    private record Basis(Vector forward, Vector right, Vector up) {
    }

    private record LocationKey(String world, int x, int y, int z) {
        static LocationKey of(Location loc) {
            return new LocationKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
    }

    private enum GeneratorType {
        FURNACE("Normaler Ofen", "furnace"),
        SMOKER("R\u00e4ucherofen", "smoker"),
        BLAST("Schmiedeofen", "blast");

        private final String display;
        private final String configPath;

        GeneratorType(String display, String configPath) {
            this.display = display;
            this.configPath = configPath;
        }
    }

    private enum GeneratorMode {
        CUBE,
        COBBLER
    }

    private static final class GeneratorData {
        String worldName;
        int x;
        int y;
        int z;
        GeneratorType type = GeneratorType.FURNACE;
        GeneratorMode mode = GeneratorMode.CUBE;
        boolean active;
        boolean particles = true;

        int kelpCharge;
        int boneCharge;
        int lavaCharge;
        int remainingCharge;
        int cursorIndex;
        long noSpaceRetryTick;

        int silkTouch;
        int fortune;
        int mending;
        int efficiency;
        int sizeUpgrade;
    }

    private record Costs(int kelp, int bone, int lava) {
    }
}
