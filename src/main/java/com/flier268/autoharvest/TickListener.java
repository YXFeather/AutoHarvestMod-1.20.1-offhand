package com.flier268.autoharvest;

import java.util.Collection;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class TickListener {
    private final Configure configure;
    private ClientPlayerEntity p;

    private long fishBitesAt = 0L;
    private ItemStack lastUsedItem = null;

    public TickListener(Configure configure, ClientPlayerEntity player) {
        this.configure = configure;
        this.p = player;
        ClientTickEvents.END_CLIENT_TICK.register(e -> {
            if (AutoHarvest.instance.overlayRemainingTick > 0) {
                AutoHarvest.instance.overlayRemainingTick--;
            }
            if (AutoHarvest.instance.Switch)
                onTick(e.player);
        });
    }

    public void Reset() {
        lastUsedItem = null;
        fishBitesAt = 0L;
    }

    public void onTick(ClientPlayerEntity player) {
        try {
            if (player != p) {
                this.p = player;
                AutoHarvest.instance.Switch = false;
                AutoHarvest.msg("notify.turn.off");
                return;
            }
            if (AutoHarvest.instance.taskManager.Count() > 0) {
                AutoHarvest.instance.taskManager.RunATask();
                return;
            }
            switch (AutoHarvest.instance.mode) {
                case SEED -> seedTick();
                case HARVEST -> harvestTick();
                case PLANT -> {
                    offplantTick();
                    mainplantTick();
                }
                case Farmer -> {
                    harvestTick();
                    offplantTick();
                    mainplantTick();
                }
                case FEED -> feedTick();
                case FISHING -> fishingTick();
                case BONEMEALING -> bonemealingTick();
                case HOEING -> {
                    mainHoeingTick();
                    offHoeingTick();
                }
                case AXEITEMS -> {
                    mainHandStripTick();
                    offHandStripTick();
                }
                case DONTSTEPWHITE -> handTick();
            }
            if (AutoHarvest.instance.mode != AutoHarvest.HarvestMode.FISHING)
                AutoHarvest.instance.taskManager.Add_TickSkip(AutoHarvest.instance.configure.tickSkip.value);
        } catch (Exception ex) {
            AutoHarvest.msg("notify.tick_error");
            AutoHarvest.msg("notify.turn.off");
            ex.printStackTrace();
            AutoHarvest.instance.Switch = false;
        }
    }

    /* clear all grass on land */
    private void seedTick() {
        World w = p.getEntityWorld();
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY());// the "leg block"
        int Z = (int) Math.floor(p.getZ());
        for (int deltaY = 3; deltaY >= -2; --deltaY)
            for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX)
                for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {
                    BlockPos pos = new BlockPos(X + deltaX, Y + deltaY, Z + deltaZ);
                    if (CropManager.isWeedBlock(w, pos) || (AutoHarvest.instance.configure.flowerISseed.value
                            && CropManager.isFlowerBlock(w, pos))) {
                        assert MinecraftClient.getInstance().interactionManager != null;
                        MinecraftClient.getInstance().interactionManager.attackBlock(pos, Direction.UP);
                        return;
                    }
                }
    }

    // 手执行工作
    private void handWork(double X, double Y, double Z, BlockPos pos, Hand hand) {
        BlockHitResult blockHitResult = new BlockHitResult(
                new Vec3d(X, Y, Z), Direction.UP, pos, false);
        assert MinecraftClient.getInstance().interactionManager != null;
        MinecraftClient.getInstance().interactionManager.interactBlock(
                MinecraftClient.getInstance().player, hand, blockHitResult);
    }

    /**
     * 主手锄头耕地
     */
    private void mainHoeingTick() {
        ItemStack MainHandItem = p.getMainHandStack();
        if (MainHandItem == null || !MainHandItem.isIn(ItemTags.HOES)) {
            return;
        }
        World w = p.getEntityWorld();

        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY() -0.2D);// 脚下方块
        int Z = (int) Math.floor(p.getZ());
        for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX){
            if (!MainHandItem.isIn(ItemTags.HOES)){
                return;
            }
            for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {
                BlockPos pos = new BlockPos(X + deltaX, Y, Z + deltaZ);
                BlockState state = w.getBlockState(pos);
                Block block = state.getBlock();
                if ((block == Blocks.DIRT ||
                        block == Blocks.GRASS_BLOCK ||
                        block == Blocks.COARSE_DIRT ||
                        block == Blocks.ROOTED_DIRT)) {
                    if(configure.keepWaterNearBy.value) {
                        if(isWaterNearby(w, pos)) {
                            handWork(X + deltaX + 0.5, Y, Z + deltaZ + 0.5, pos, Hand.MAIN_HAND);
                        }
                    }else {
                        handWork(X + deltaX + 0.5, Y, Z + deltaZ + 0.5, pos, Hand.MAIN_HAND);
                    }
                }
            }
        }
    }

    /**
     * 副手锄头耕地
     */
    private void offHoeingTick() {
        ItemStack OffHandItem = p.getOffHandStack();
        if (OffHandItem == null || !OffHandItem.isIn(ItemTags.HOES)) {
            return;
        }

        World w = p.getEntityWorld();
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY() -0.2D);// 脚下方块
        int Z = (int) Math.floor(p.getZ());
        for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX){
            if (!OffHandItem.isIn(ItemTags.HOES)){
                return;
            }
            for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {

                BlockPos pos = new BlockPos(X + deltaX, Y, Z + deltaZ);
                BlockState state = w.getBlockState(pos);
                Block block = state.getBlock();
                if ((block == Blocks.DIRT ||
                        block == Blocks.GRASS_BLOCK ||
                        block == Blocks.COARSE_DIRT ||
                        block == Blocks.ROOTED_DIRT)) {
                    if(configure.keepWaterNearBy.value) {
                        if(isWaterNearby(w, pos)) {
                            handWork(X + deltaX + 0.5, Y, Z + deltaZ + 0.5, pos, Hand.OFF_HAND);
                        }
                    }else {
                            handWork(X + deltaX + 0.5, Y, Z + deltaZ + 0.5, pos, Hand.OFF_HAND);
                    }
                }
            }
        }
    }


    /**
     * 检测水源
     */
    public static boolean isWaterNearby(World world, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.iterate(pos.add(-4, 0, -4), pos.add(4, 1, 4))) {
            if (world.getFluidState(blockPos).isIn(FluidTags.WATER)) return true;
        }
        return false;
    }

    //主手去皮
    private void mainHandStripTick() {
        ItemStack MainHandItem = p.getMainHandStack();
        World w = p.getEntityWorld();
        if (MainHandItem == null || (!MainHandItem.isIn(ItemTags.AXES) && MainHandItem.getItem() != Items.SHEARS)) {
            return;
        }
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY()); //脚下方块
        int Z = (int) Math.floor(p.getZ());
        for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX) {
            for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ){
                for (int deltaY = 0; deltaY <= configure.effect_radius.value; ++deltaY){
                    BlockPos pos = new BlockPos(X + deltaX, Y + deltaY, Z + deltaZ);
                    if (!canReachBlock(p, pos)) continue;
                    //雕刻南瓜
                    if((CropManager.isWood(w, pos)) && MainHandItem.getItem() == Items.SHEARS) {
                        handWork(X + deltaX + 0.5, Y + deltaY + 0.5, Z + deltaZ + 0.5, pos, Hand.MAIN_HAND);
                    }
                    //去皮
                    if ((CropManager.isWood(w, pos)) && MainHandItem.isIn(ItemTags.AXES)){
                        handWork(X + deltaX + 0.5, Y + deltaY + 0.5, Z + deltaZ + 0.5, pos, Hand.MAIN_HAND);
                    }
                }
            }
        }
    }

    //副手去皮
    private void offHandStripTick() {
        ItemStack OffHandItem = p.getOffHandStack();
        World w = p.getEntityWorld();
        if (OffHandItem == null || (!OffHandItem.isIn(ItemTags.AXES) && OffHandItem.getItem() != Items.SHEARS)) {
            return;
        }
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY()); //脚下方块
        int Z = (int) Math.floor(p.getZ());
        for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX) {
            for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {
                for (int deltaY = 0; deltaY <= configure.effect_radius.value; ++deltaY){
                    BlockPos pos = new BlockPos(X + deltaX, Y + deltaY, Z + deltaZ);
                    if (!canReachBlock(p, pos)) continue;
                    //雕刻南瓜
                    if((CropManager.isWood(w, pos)) && OffHandItem.getItem() == Items.SHEARS) {
                        handWork(X + deltaX + 0.5, Y + deltaY + 0.5, Z + deltaZ + 0.5, pos, Hand.OFF_HAND);
                    }

                    if ((CropManager.isWood(w, pos)) && OffHandItem.isIn(ItemTags.AXES)){
                        handWork(X + deltaX + 0.5, Y + deltaY + 0.5, Z + deltaZ + 0.5, pos, Hand.OFF_HAND);
                    }
                }
            }
        }
    }


    //别踩白块左键
    private void handTick() {
        World w = p.getEntityWorld();
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY()) + 1; //脚下方块
        int Z = (int) Math.floor(p.getZ());
        for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX) {
            for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {
                BlockPos pos = new BlockPos(X + deltaX, Y, Z + deltaZ);
                Block b = w.getBlockState(pos).getBlock();
                if ((b == Blocks.BLACK_WOOL)) {
                    assert MinecraftClient.getInstance().interactionManager != null;
                    MinecraftClient.getInstance().interactionManager.attackBlock(pos, Direction.UP);
                    return;
                }
            }
        }
    }


    /* harvest all mature crops */
    private void harvestTick() {
        World w = p.getEntityWorld();
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY() + 0.2D);// the "leg block", in case in soul sand
        int Z = (int) Math.floor(p.getZ());
        for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX) {
            for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {
                for (int deltaY = -1; deltaY <= 1; ++deltaY) {
                    BlockPos pos = new BlockPos(X + deltaX, Y + deltaY, Z + deltaZ);
                    BlockState state = w.getBlockState(pos);
                    Block block = state.getBlock();
                    if (CropManager.isCropMature(w, pos, state, block)) {
                        if (block == Blocks.SWEET_BERRY_BUSH) {
                            handWork(X + deltaX + 0.5, Y + deltaY - 0.5, Z + deltaZ + 0.5, pos, Hand.MAIN_HAND);
                        } else {
                            assert MinecraftClient.getInstance().interactionManager != null;
                            MinecraftClient.getInstance().interactionManager.attackBlock(pos, Direction.UP);
                        }
                        return;
                    }
                }
            }
        }
    }


    private ItemStack tryFillItemInHand() {
        ItemStack itemStack = p.getMainHandStack();
        if (itemStack.isEmpty()) {
            if (lastUsedItem != null && !lastUsedItem.isEmpty()) {
                DefaultedList<ItemStack> inv = p.getInventory().main;
                for (int idx = 0; idx < 36; ++idx) {
                    ItemStack s = inv.get(idx);
                    if (s.getItem() == lastUsedItem.getItem() &&
                            s.getDamage() == lastUsedItem.getDamage() &&
                            !s.hasNbt()) {
                        AutoHarvest.instance.taskManager.Add_MoveItem(idx, p.getInventory().selectedSlot);
                        return s;
                    }
                }
            }
            return null;
        } else {
            return itemStack;
        }
    }


    /**
     * 副手种植
     **/
    private void offplantTick() {
        ItemStack offHandItem = p.getOffHandStack();
        if (offHandItem == null) {
            return;
        }
        if (!CropManager.isSeed(offHandItem)) {
            if (CropManager.isCocoa(offHandItem)) {
                plantCocoaTick();
            }
            return;
        }

        World w = p.getEntityWorld();
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY() + 0.2D);// the "leg block" , in case in soul sand
        int Z = (int) Math.floor(p.getZ());

        for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX)
            for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {
                BlockPos pos = new BlockPos(X + deltaX, Y, Z + deltaZ);
                if (!CropManager.canPaint(w.getBlockState(pos), offHandItem))
                    continue;
                if (CropManager.canPlantOn(offHandItem.getItem(), w, pos)) {
                    if (w.getBlockState(pos.down()).getBlock() == Blocks.KELP)
                        continue;
                    lastUsedItem = offHandItem.copy();
                    assert MinecraftClient.getInstance().interactionManager != null;
                    BlockPos downPos = pos.down();
                    handWork(X + deltaX + 0.5, Y, Z + deltaZ + 0.5, pos, Hand.OFF_HAND);
                    return;
                }
            }
    }

    /**
     * 主手种植
     **/
    private void mainplantTick() {
        ItemStack HandItem = p.getMainHandStack();
        if (HandItem == null) {
            return;
        }
        if (!CropManager.isSeed(HandItem)) {
            if (CropManager.isCocoa(HandItem)) {
                plantCocoaTick();
            }
            return;
        }

        World w = p.getEntityWorld();
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY() + 0.2D);// the "leg block" , in case in soul sand
        int Z = (int) Math.floor(p.getZ());

        for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX)
            for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {
                BlockPos pos = new BlockPos(X + deltaX, Y, Z + deltaZ);
                if (!CropManager.canPaint(w.getBlockState(pos), HandItem))
                    continue;
                if (CropManager.canPlantOn(HandItem.getItem(), w, pos)) {
                    if (w.getBlockState(pos.down()).getBlock() == Blocks.KELP)
                        continue;
                    lastUsedItem = HandItem.copy();
                    assert MinecraftClient.getInstance().interactionManager != null;
                    BlockPos downPos = pos.down();
                    handWork(X + deltaX + 0.5, Y, Z + deltaZ + 0.5, pos, Hand.MAIN_HAND);
                    return;
                }
            }
    }

    private void plantCocoaTick() {
        ItemStack mhand = p.getMainHandStack();
        if (!CropManager.isCocoa(mhand)) {
            return;
        }
        World w = p.getEntityWorld();
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY() + 0.2D);// the "leg block" , in case in soul sand
        int Z = (int) Math.floor(p.getZ());

        for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX) {
            for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {
                for (int deltaY = 0; deltaY <= 7; ++deltaY) {
                    BlockPos pos = new BlockPos(X + deltaX, Y + deltaY, Z + deltaZ);
                    if (!canReachBlock(p, pos))
                        continue;
                    BlockState jungleBlock = w.getBlockState(pos);
                    if (CropManager.isJungleLog(jungleBlock)) {
                        BlockPos tmpPos;

                        Direction tmpFace = Direction.EAST;
                        tmpPos = pos.add(tmpFace.getVector());
                        if (w.getBlockState(tmpPos).getBlock() == Blocks.AIR) {
                            lastUsedItem = mhand.copy();
                            BlockHitResult blockHitResult = new BlockHitResult(
                                    new Vec3d(X + deltaX + 1, Y + deltaY + 0.5, Z + deltaZ + 0.5), tmpFace, pos, false);
                            assert MinecraftClient.getInstance().interactionManager != null;
                            MinecraftClient.getInstance().interactionManager.interactBlock(
                                    MinecraftClient.getInstance().player, Hand.MAIN_HAND, blockHitResult);
                            return;
                        }

                        tmpFace = Direction.WEST;
                        tmpPos = pos.add(tmpFace.getVector());
                        if (w.getBlockState(tmpPos).getBlock() == Blocks.AIR) {
                            lastUsedItem = mhand.copy();
                            BlockHitResult blockHitResult = new BlockHitResult(
                                    new Vec3d(X + deltaX, Y + deltaY + 0.5, Z + deltaZ + 0.5), tmpFace, pos, false);
                            assert MinecraftClient.getInstance().interactionManager != null;
                            MinecraftClient.getInstance().interactionManager.interactBlock(
                                    MinecraftClient.getInstance().player, Hand.MAIN_HAND, blockHitResult);
                            return;
                        }

                        tmpFace = Direction.SOUTH;
                        tmpPos = pos.add(tmpFace.getVector());
                        if (w.getBlockState(tmpPos).getBlock() == Blocks.AIR) {
                            lastUsedItem = mhand.copy();
                            BlockHitResult blockHitResult = new BlockHitResult(
                                    new Vec3d(X + deltaX + 0.5, Y + deltaY + 0.5, Z + deltaZ + 1), tmpFace, pos, false);
                            assert MinecraftClient.getInstance().interactionManager != null;
                            MinecraftClient.getInstance().interactionManager.interactBlock(
                                    MinecraftClient.getInstance().player, Hand.MAIN_HAND, blockHitResult);
                            return;
                        }

                        tmpFace = Direction.NORTH;
                        tmpPos = pos.add(tmpFace.getVector());
                        if (w.getBlockState(tmpPos).getBlock() == Blocks.AIR) {
                            lastUsedItem = mhand.copy();
                            BlockHitResult blockHitResult = new BlockHitResult(
                                    new Vec3d(X + deltaX + 0.5, Y + deltaY + 0.5, Z + deltaZ), tmpFace, pos, false);
                            assert MinecraftClient.getInstance().interactionManager != null;
                            MinecraftClient.getInstance().interactionManager.interactBlock(
                                    MinecraftClient.getInstance().player, Hand.MAIN_HAND, blockHitResult);
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean canReachBlock(ClientPlayerEntity playerEntity, BlockPos blockpos) {
        double d0 = playerEntity.getX() - ((double) blockpos.getX() + 0.5D);
        double d1 = playerEntity.getY() - ((double) blockpos.getY() + 0.5D) + 1.5D;
        double d2 = playerEntity.getZ() - ((double) blockpos.getZ() + 0.5D);
        double d3 = d0 * d0 + d1 * d1 + d2 * d2;
        return d3 <= 36D;
    }

    private void feedTick() {
        ItemStack handItem = tryFillItemInHand();

        if (handItem == null)
            return;

        // if (animalList.isEmpty()) return;
        Box box = new Box(p.getX() - configure.effect_radius.value, p.getY() - configure.effect_radius.value,
                p.getZ() - configure.effect_radius.value,
                p.getX() + configure.effect_radius.value, p.getY() + configure.effect_radius.value,
                p.getZ() + configure.effect_radius.value);
        Collection<Class<? extends AnimalEntity>> needShearAnimalList = CropManager.SHEAR_MAP.get(handItem.getItem());
        for (Class<? extends AnimalEntity> type : needShearAnimalList) {
            for (AnimalEntity e : p.getEntityWorld().getEntitiesByClass(
                    type,
                    box,
                    animalEntity -> {
                        if (animalEntity instanceof SheepEntity) {
                            return !animalEntity.isBaby() && !((SheepEntity) animalEntity).isSheared();
                        }
                        return false;
                    })) {
                lastUsedItem = handItem.copy();
                assert MinecraftClient.getInstance().interactionManager != null;
                MinecraftClient.getInstance().interactionManager.interactEntity(p, e, Hand.MAIN_HAND);
                return;
            }
        }
        // 繁殖悦灵
        Collection<Class<? extends AllayEntity>> feedAxolotList = CropManager.ALLAY_MAP.get(handItem.getItem());
        for (Class<? extends AllayEntity> type : feedAxolotList) {
            for (AllayEntity e : p.getEntityWorld().getEntitiesByClass(
                    type,
                    box,
                    allayEntity -> !allayEntity.isHoldingItem() && allayEntity.isDancing())) {

                lastUsedItem = handItem.copy();
                assert MinecraftClient.getInstance().interactionManager != null;
                MinecraftClient.getInstance().interactionManager.interactEntity(p, e, Hand.MAIN_HAND);
            }
        }
        // 繁殖普通动物 (不包括美西螈)
        Collection<Class<? extends AnimalEntity>> needFeedAnimalList = CropManager.FEED_MAP.get(handItem.getItem());
        for (Class<? extends AnimalEntity> type : needFeedAnimalList) {
            for (AnimalEntity e : p.getEntityWorld().getEntitiesByClass(
                    type,
                    box,
                    animalEntity -> animalEntity.getBreedingAge() >= 0 && !animalEntity.isInLove())) {

                lastUsedItem = handItem.copy();
                assert MinecraftClient.getInstance().interactionManager != null;
                MinecraftClient.getInstance().interactionManager.interactEntity(p, e, Hand.MAIN_HAND);

            }
        }
    }

    /**
     * @return -1: doesn't have rod; 0: no change; change
     * 若手上不是鱼竿尝试替换成鱼竿
     **/
    private int tryReplacingFishingRod() {
        ItemStack itemStack = p.getMainHandStack();
        if (CropManager.isRod(itemStack)
                && (!configure.keepFishingRodAlive.value || itemStack.getMaxDamage() - itemStack.getDamage() > 1)) {
            return 0;
        } else {
            DefaultedList<ItemStack> inv = p.getInventory().main;
            for (int idx = 0; idx < 36; ++idx) {
                ItemStack s = inv.get(idx);
                if (CropManager.isRod(s)
                        && (!configure.keepFishingRodAlive.value || s.getMaxDamage() - s.getDamage() > 1)) {
                    AutoHarvest.instance.taskManager.Add_MoveItem(idx, p.getInventory().selectedSlot);
                    return 1;
                }
            }
            return -1;
        }
    }

    private long getWorldTime() {
        assert MinecraftClient.getInstance().world != null;
        return MinecraftClient.getInstance().world.getTime();
    }

    private boolean isFishBites(ClientPlayerEntity player) {
        FishingBobberEntity fishEntity = player.fishHook;
        return fishEntity != null && (fishEntity.prevX - fishEntity.getX()) == 0
                && (fishEntity.prevZ - fishEntity.getZ()) == 0 && (fishEntity.prevY - fishEntity.getY()) < -0.05d;
    }

    private void fishingTick() {
        switch (tryReplacingFishingRod()) {
            case -1:
                AutoHarvest.msg("notify.turn.off");
                AutoHarvest.instance.Switch = false;
                break;
            case 0:
                /* Reel */
                if (fishBitesAt == 0 && isFishBites(p)) {
                    fishBitesAt = getWorldTime();
                    assert MinecraftClient.getInstance().interactionManager != null;
                    MinecraftClient.getInstance().interactionManager.interactItem(
                            p,
                            Hand.MAIN_HAND);
                }

                /* Cast */
                if (fishBitesAt != 0 && fishBitesAt + 20 <= getWorldTime()) {
                    assert MinecraftClient.getInstance().interactionManager != null;
                    MinecraftClient.getInstance().interactionManager.interactItem(
                            p,
                            Hand.MAIN_HAND);
                    fishBitesAt = 0;
                }
                break;
            case 1:
        }
    }

    /* 骨粉催熟 */
    private void bonemealingTick() {
        ItemStack handItem = p.getMainHandStack();
        if (handItem == null || !CropManager.isBoneMeal(handItem)) {
            return;
        } else {
            handItem = tryFillItemInHand();
        }

        World w = p.getEntityWorld();
        int X = (int) Math.floor(p.getX());
        int Y = (int) Math.floor(p.getY());
        int Z = (int) Math.floor(p.getZ());
        for (int deltaY = 3; deltaY >= -2; --deltaY) {
            for (int deltaX = -configure.effect_radius.value; deltaX <= configure.effect_radius.value; ++deltaX) {
                for (int deltaZ = -configure.effect_radius.value; deltaZ <= configure.effect_radius.value; ++deltaZ) {
                    BlockPos pos = new BlockPos(X + deltaX, Y + deltaY, Z + deltaZ);
                    BlockState blockState = w.getBlockState(pos);
                    Block block = blockState.getBlock();
                    // 催熟瓶子草
                    if (block instanceof PitcherCropBlock){
                        if (((PitcherCropBlock) block).isFertilizable(w, pos, blockState, true)) {
                            assert handItem != null;
                            lastUsedItem = handItem.copy();
                            handWork(X + deltaX + 0.5, Y + deltaY + 0.5, Z + deltaZ + 0.5, pos, Hand.MAIN_HAND);
                        }
                    }
                    if (block instanceof CropBlock) {
                        if (((CropBlock) block).isFertilizable(w, pos, blockState, true)) {
                            assert handItem != null;
                            lastUsedItem = handItem.copy();
                            handWork(X + deltaX + 0.5, Y + deltaY + 0.5, Z + deltaZ + 0.5, pos, Hand.MAIN_HAND);
                        }
                    }
                }
            }
        }
    }
}