package cat.zelather64.autoharvest.Mode;

import cat.zelather64.autoharvest.Config.AutoHarvestConfig;
import cat.zelather64.autoharvest.Utils.BoxUtil;
import cat.zelather64.autoharvest.Utils.InteractionHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class HarvestMode implements AutoMode{

    private static final Set<Block> HARVEST_CROPS;
    private static final Set<Block> FAST_BREAK_BLOCKS; // 快速破坏方块集合

    static {
        Set<Block> crops = new HashSet<>();
        crops.add(Blocks.WHEAT);
        crops.add(Blocks.CARROTS);
        crops.add(Blocks.POTATOES);
        crops.add(Blocks.BEETROOTS);
        crops.add(Blocks.NETHER_WART);
        crops.add(Blocks.SUGAR_CANE);
        crops.add(Blocks.SWEET_BERRY_BUSH);
        // 1.20.1
        crops.add(Blocks.TORCHFLOWER);
        crops.add(Blocks.PITCHER_CROP);
        crops.add(Blocks.MELON);
        crops.add(Blocks.PUMPKIN);
        crops.add(Blocks.KELP);
        crops.add(Blocks.BAMBOO);
        crops.add(Blocks.COCOA);
        HARVEST_CROPS = Set.copyOf(crops);

        // 需要快速破坏的方块
        Set<Block> fastBreakBlocks = new HashSet<>();
        fastBreakBlocks.add(Blocks.BAMBOO);
        fastBreakBlocks.add(Blocks.MELON);
        fastBreakBlocks.add(Blocks.PUMPKIN);
        fastBreakBlocks.add(Blocks.COCOA);
        FAST_BREAK_BLOCKS = Set.copyOf(fastBreakBlocks);
    }

    // 缓存半径计算，避免重复计算
    private double cachedRadius = -1;
    private int cachedRadiusInt = -1;
    private double radiusSquared = -1;

    // 缓存玩家和世界引用，减少方法调用
    private ClientPlayerEntity cachedPlayer = null;
    private ClientWorld cachedWorld = null;
    private Vec3d cachedPlayerPos = null;

    // 缓存判断函数
    private final Predicate<Block> isHarvestCrop = HARVEST_CROPS::contains;
    private final Predicate<Block> isFastBreakBlock = FAST_BREAK_BLOCKS::contains;

    @Override
    public void tick() {
        // 获取世界和玩家，并缓存
        ClientWorld world = BoxUtil.getWorld();
        ClientPlayerEntity player = BoxUtil.getPlayer();
        if (world == null || player == null) {
            resetCache();
            return;
        }

        Vec3d playerPos = BoxUtil.getPlayerPos();
        if (playerPos == null) {
            resetCache();
            return;
        }

        // 更新缓存
        cachedWorld = world;
        cachedPlayer = player;
        cachedPlayerPos = playerPos;

        // 获取配置半径，检查是否需要更新缓存
        double currentRadius = AutoHarvestConfig.getRadius();
        if (currentRadius != cachedRadius) {
            updateRadiusCache(currentRadius);
        }

        // 检查半径是否有效
        if (cachedRadiusInt <= 0) return;

        // 执行收获逻辑
        performHarvest();
    }

    /**
     * 更新半径缓存
     */
    private void updateRadiusCache(double newRadius) {
        cachedRadius = newRadius;
        cachedRadiusInt = (int) Math.ceil(newRadius);
        radiusSquared = newRadius * newRadius;
    }

    /**
     * 重置缓存
     */
    private void resetCache() {
        cachedPlayer = null;
        cachedWorld = null;
        cachedPlayerPos = null;
    }

    /**
     * 执行收获逻辑
     */
    private void performHarvest() {
        BlockPos playerBlockPos = BlockPos.ofFloored(cachedPlayerPos);

        // 使用局部变量减少字段访问
        ClientWorld world = cachedWorld;
        ClientPlayerEntity player = cachedPlayer;
        Vec3d playerPos = cachedPlayerPos;
        int radiusInt = cachedRadiusInt;

        // 遍历搜索范围内的方块
        for (BlockPos pos : BlockPos.iterateOutwards(playerBlockPos, radiusInt, radiusInt, radiusInt)) {
            // 快速距离检查
            if (!isWithinRadius(pos, playerPos)) {
                continue;
            }

            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            // 快速白名单检查
            if (!isHarvestCrop.test(block)) {
                continue;
            }

            // 处理不同类型的作物
            if (tryHandleCrop(player, pos, state, block)) {
                return; // 成功收获一个方块后立即返回
            }
        }
    }

    /**
     * 检查方块是否在半径范围内（使用平方距离）
     */
    private boolean isWithinRadius(BlockPos pos, Vec3d playerPos) {
        double dx = pos.getX() + 0.5 - playerPos.x;
        double dy = pos.getY() + 0.5 - playerPos.y;
        double dz = pos.getZ() + 0.5 - playerPos.z;
        return (dx * dx + dy * dy + dz * dz) <= radiusSquared;
    }

    /**
     * 尝试处理作物
     */
    private boolean tryHandleCrop(ClientPlayerEntity player, BlockPos pos, BlockState state, Block block) {
        // 特殊处理甘蔗
        if (block == Blocks.SUGAR_CANE) {
            return handleSugarCane(player, pos);
        }

        // 特殊处理竹子
        if (block == Blocks.BAMBOO) {
            return handleBamboo(player, pos);
        }

        // 特殊处理西瓜和南瓜（不需要检查成熟度）
        if (block == Blocks.MELON || block == Blocks.PUMPKIN || (block == Blocks.COCOA && block.getDefaultState().get(Properties.AGE_2) >= 2)) {
            return handleUpdateBreakingProgress(player, pos, block);
        }

        // 检查是否成熟（西瓜和南瓜不需要检查）
        if (!isFullyGrown(state, block)) {
            return false;
        }

        // 特殊处理甜浆果丛
        if (block == Blocks.SWEET_BERRY_BUSH) {
            return handleSweetBerryBush(player, pos);
        }

        // 普通作物收获
        return harvestCrop(player, pos, block);
    }

    /**
     * 处理甘蔗
     */
    private boolean handleSugarCane(ClientPlayerEntity player, BlockPos pos) {
        // 甘蔗需要检查下方是否是甘蔗
        if (cachedWorld.getBlockState(pos.down()).isOf(Blocks.SUGAR_CANE)) {
            return false;
        }

        // 检查上方是否有第二段甘蔗
        BlockPos secondPos = pos.up();
        if (cachedWorld.getBlockState(secondPos).isOf(Blocks.SUGAR_CANE)) {
            return tryHarvest(player, secondPos, Blocks.SUGAR_CANE);
        }

        return false;
    }

    /**
     * 处理竹子
     */
    private boolean handleBamboo(ClientPlayerEntity player, BlockPos pos) {
        // 竹子需要检查下方是否是竹子
        if (cachedWorld.getBlockState(pos.down()).isOf(Blocks.BAMBOO)) {
            return false;
        }

        // 检查上方是否有第二段竹子
        BlockPos secondPos = pos.up();
        if (cachedWorld.getBlockState(secondPos).isOf(Blocks.BAMBOO)) {
            return tryHarvest(player, secondPos, Blocks.BAMBOO);
        }

        return false;
    }

    /**
     * 处理西瓜或南瓜
     */
    private boolean handleUpdateBreakingProgress(ClientPlayerEntity player, BlockPos pos, Block block) {
        // 西瓜和南瓜直接收获，不需要检查成熟度
        return tryHarvest(player, pos, block);
    }

    /**
     * 处理甜浆果丛
     */
    private boolean handleSweetBerryBush(ClientPlayerEntity player, BlockPos pos) {
        // 甜浆果丛需要交互而不是破坏
        if (AutoHarvestConfig.autoSwitchFortuneTool()) {
            switchToFortuneTool(player);
        }
        InteractionHelper.interactBlock(player, pos, Hand.MAIN_HAND, Direction.UP);
        return true;
    }

    /**
     * 收获普通作物
     */
    private boolean harvestCrop(ClientPlayerEntity player, BlockPos pos, Block block) {
        return tryHarvest(player, pos, block);
    }

    /**
     * 检查作物是否完全成熟
     */
    private boolean isFullyGrown(BlockState state, Block block) {
        
        if (block == Blocks.COCOA) {
            return state.get(Properties.AGE_2) >= 2;
        }

        // 火炬花没有生长阶段
        if (block == Blocks.TORCHFLOWER) {
            return true;
        }

        // 其他作物的成熟度检查
        if (block == Blocks.NETHER_WART) {
            return state.get(Properties.AGE_3) >= 3;
        } else if (block == Blocks.SWEET_BERRY_BUSH) {
            return state.get(Properties.AGE_3) >= 2;
        } else if (block instanceof CropBlock) {
            if (block == Blocks.BEETROOTS) {
                return state.get(Properties.AGE_3) >= 3;
            } else {
                return state.get(Properties.AGE_7) >= 7;
            }
        } else if (block == Blocks.PITCHER_CROP) {
            return state.get(Properties.AGE_4) >= 4;
        }
        return false;
    }

    /**
     * 尝试收获方块
     */
    private boolean tryHarvest(ClientPlayerEntity player, BlockPos pos, Block block) {
        // 根据方块类型选择合适的工具
        if (isFastBreakBlock.test(block)) {
            // 竹子、西瓜、南瓜使用快速破坏工具
            switchToFastBreakTool(player, block);
            InteractionHelper.updateBlockBreakingProgress(pos, Direction.UP);
            return true;
        } else if (AutoHarvestConfig.autoSwitchFortuneTool()) {
            // 其他作物使用带时运的工具
            switchToFortuneTool(player);
        }

        InteractionHelper.breakBlock(pos, Direction.UP);
        return true;
    }

    /**
     * 切换到时运工具
     */
    private void switchToFortuneTool(ClientPlayerEntity player) {
        int fortuneSlot = findToolSlot(player, true, null);
        if (fortuneSlot != -1 && fortuneSlot != player.getInventory().selectedSlot) {
            player.getInventory().selectedSlot = fortuneSlot;
        }
    }

    /**
     * 切换到快速破坏工具
     */
    private void switchToFastBreakTool(ClientPlayerEntity player, Block block) {
        int toolSlot = findToolSlot(player, false, block);
        if (toolSlot != -1 && toolSlot != player.getInventory().selectedSlot) {
            player.getInventory().selectedSlot =toolSlot;
        }
    }

    /**
     * 查找工具槽位
     * @param requireFortune 是否需要时运附魔
     * @param block 要破坏的方块（用于选择最佳工具类型）
     */
    private int findToolSlot(ClientPlayerEntity player, boolean requireFortune, Block block) {
//        World world = player.getWorld();
        PlayerInventory inventory = player.getInventory();
        int currentSlot = inventory.selectedSlot;
        int bestSlot = -1;
        int highestFortuneLevel = 0;
        float bestSpeed = 0;

        // 获取时运附魔对象（1.20.1：直接获取Enchantment，不再是RegistryEntry）
        Enchantment fortuneEnchantment = null;
        if (requireFortune) {
            // 方法1：通过静态字段获取（如果Enchantments类中有对应字段）
            fortuneEnchantment = Enchantments.FORTUNE;
            // 或者方法2：通过注册表标识符获取（更通用）
            // fortuneEnchantment = Registries.ENCHANTMENT.get(new Identifier("minecraft", "fortune"));

            if (fortuneEnchantment == null) {
                // 如果无法获取附魔，对于需要时运的情况直接返回-1
                return -1;
            }
        }

        // 优先检查当前手持的工具
        ItemStack currentStack = inventory.getStack(currentSlot);
        if (isSuitableTool(currentStack, block, requireFortune, fortuneEnchantment)) {
            if (requireFortune) {
                // 调用修改后的getFortuneLevel，它现在接受Enchantment参数
                int currentFortuneLevel = getFortuneLevel(currentStack, fortuneEnchantment);
                if (currentFortuneLevel > 0) {
                    return currentSlot;
                }
            } else {
                float currentSpeed = getBreakingSpeed(currentStack, block);
                if (currentSpeed > 1.0f) {
                    return currentSlot;
                }
            }
        }

        // 搜索其他槽位
        for (int i = 0; i < 9; i++) {
            if (i == currentSlot) continue;

            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;

            if (isSuitableTool(stack, block, requireFortune, fortuneEnchantment)) {
                if (requireFortune) {
                    int fortuneLevel = getFortuneLevel(stack, fortuneEnchantment);
                    if (fortuneLevel > highestFortuneLevel) {
                        highestFortuneLevel = fortuneLevel;
                        bestSlot = i;
                    }
                } else {
                    float speed = getBreakingSpeed(stack, block);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestSlot = i;
                    }
                }
            }
        }

        return bestSlot;
    }

    /**
     * 检查工具是否适合
     */
    private boolean isSuitableTool(ItemStack stack, Block block, boolean requireFortune, Enchantment fortuneEntry) {
        Item item = stack.getItem();

        // 如果是需要时运的工具
        if (requireFortune) {
            return isHarvestTool(item) && (fortuneEntry == null || getFortuneLevel(stack, fortuneEntry) > 0);
        }

        // 如果是快速破坏工具，根据方块类型选择最佳工具
        if (block == Blocks.BAMBOO) {
            // 竹子：剑或斧子
            return isSword(item) || isAxe(item);
        } else if (block == Blocks.MELON || block == Blocks.PUMPKIN || block == Blocks.COCOA) {
            // 西瓜和南瓜：斧子
            return isAxe(item);
        }

        return false;
    }

    /**
     * 获取破坏速度
     */
    private float getBreakingSpeed(ItemStack stack, Block block) {
        Item item = stack.getItem();

        // 基础速度
        float speed = item.getMiningSpeedMultiplier(stack, block.getDefaultState());

        // 考虑效率附魔
        Enchantment efficiencyEntry = getEnchantmentEntry();
        if (efficiencyEntry != null) {
            int efficiencyLevel = getEnchantmentLevel(stack, efficiencyEntry);
            if (efficiencyLevel > 0) {
                speed += efficiencyLevel * efficiencyLevel + 1;
            }
        }

        return speed;
    }

    /**
     * 检查是否为剑
     */
    private boolean isSword(Item item) {
        if (item == null) return false;
        ItemStack stack = new ItemStack(item);
        return stack.isIn(ItemTags.SWORDS);
    }

    /**
     * 检查是否为斧子
     */
    private boolean isAxe(Item item) {
        if (item == null) return false;
        ItemStack stack = new ItemStack(item);
        return stack.isIn(ItemTags.AXES);
    }

    /**
     * 获取工具的时运等级
     */
    private int getFortuneLevel(ItemStack stack, Enchantment fortuneEnchantment) {
        if (stack.isEmpty() || fortuneEnchantment == null) {
            return 0;
        }
        return EnchantmentHelper.getLevel(fortuneEnchantment, stack);
    }

    /**
     * 获取附魔等级
     */
    private int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        if (stack.isEmpty() || enchantment == null) {
            return 0;
        }
        return EnchantmentHelper.getLevel(enchantment, stack);
    }

    /**
     * 获取附魔注册表条目
     */
    private Enchantment getEnchantmentEntry() {
        try {
            ClientWorld world = BoxUtil.getWorld();
            if (world == null) return null;

            DynamicRegistryManager registryManager = world.getRegistryManager();
            // 获取注册表
            Optional<Registry<Enchantment>> optionalRegistry = registryManager.getOptional(RegistryKeys.ENCHANTMENT);

            if (optionalRegistry.isEmpty()) {
                return null;
            }

            Registry<Enchantment> enchantmentRegistry = optionalRegistry.get();

            // 获取效率附魔的注册表键（RegistryKey）
            RegistryKey<Enchantment> efficiencyKey = RegistryKey.of(RegistryKeys.ENCHANTMENT, new Identifier("minecraft", "efficiency"));

            // 使用注册表键来获取附魔对象
            return enchantmentRegistry.getOrEmpty(efficiencyKey).orElse(null);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查物品是否是收获工具
     */
    private boolean isHarvestTool(Item item) {
        if (item == null) return false;
        ItemStack stack = new ItemStack(item);
        return stack.isIn(ItemTags.AXES) ||
                stack.isIn(ItemTags.SHOVELS) ||
                stack.isIn(ItemTags.HOES) ||
                stack.isIn(ItemTags.PICKAXES);
    }

    @Override
    public String getName() {
        return Text.translatable("autoharvest.mode.harvest").getString();
    }

    @Override
    public void onDisable() {
        resetCache();
        cachedRadius = -1;
        cachedRadiusInt = -1;
        radiusSquared = -1;
    }
}
