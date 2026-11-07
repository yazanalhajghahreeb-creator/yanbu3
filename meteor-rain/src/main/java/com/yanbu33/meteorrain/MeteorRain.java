package com.yanbu33.meteorrain;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.MapCodec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static net.minecraft.server.command.CommandManager.literal;

public class MeteorRain implements ModInitializer {
    public static final String ID = "meteorrain";
    public static Block LUCKY_BLOCK;
    private static final Map<UUID, Integer> CLOCKS = new HashMap<>();
    private static boolean active = true;
    private static long ticks = 0;
    private static int lastInterval = 60;
    private static final int TNT_COUNT = 250;
    private static final int TARGETED_TNT_COUNT = 25;
    private static final int LUCKY_COUNT = 72;
    private static final int TNT_RADIUS = 100;
    private static final int LUCKY_RADIUS = 20;

    @Override
    public void onInitialize() {
        LUCKY_BLOCK = Registry.register(Registries.BLOCK, Identifier.of(ID, "lucky_block"),
            new LuckyMeteorBlock(AbstractBlock.Settings.create().mapColor(MapColor.YELLOW).strength(0.7f)));
        Registry.register(Registries.ITEM, Identifier.of(ID, "lucky_block"), new BlockItem(LUCKY_BLOCK, new Item.Settings()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("meteors").requires(source -> source.hasPermissionLevel(2))
            .then(literal("start").executes(ctx -> { active = true; CLOCKS.clear(); ticks = 0;
                ctx.getSource().sendFeedback(() -> Text.literal("Meteor rain started!"), true); return 1; }))
            .then(literal("stop").executes(ctx -> { active = false;
                ctx.getSource().sendFeedback(() -> Text.literal("Meteor rain stopped!"), true); return 1; }))
            .then(literal("reset").executes(ctx -> { CLOCKS.clear(); ticks = 0;
                ctx.getSource().sendFeedback(() -> Text.literal("Meteor timers reset."), true); return 1; }))
            .then(literal("status").executes(ctx -> { int interval = intervalSeconds();
                ctx.getSource().sendFeedback(() -> Text.literal("Meteors: " + (active ? "ON" : "OFF") + ", interval: " + interval + "s, TNT: " + TNT_COUNT + ", Lucky: " + LUCKY_COUNT), false); return 1; })));
    }

    private static int intervalSeconds() {
        return Math.max(30, 60 - (int) (ticks / (5L * 60 * 20)) * 10);
    }

    private void tick(MinecraftServer server) {
        if (!active) return;
        ticks++;
        int interval = intervalSeconds();
        if (interval != lastInterval) {
            lastInterval = interval;
            server.getPlayerManager().broadcast(Text.literal("☄ Meteor interval is now " + interval + " seconds!"), false);
            CLOCKS.replaceAll((uuid, current) -> Math.min(current, interval * 20));
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            int remaining = CLOCKS.getOrDefault(player.getUuid(), interval * 20);
            remaining--;
            if (remaining == 10 * 20) {
                spawnLuckyShower(player);
                player.sendMessage(Text.literal("§e☘ Lucky meteor rain! §cTNT in 10 seconds!"), true);
            }
            if (remaining <= 0) {
                spawnTntShower(player);
                remaining = interval * 20;
                player.sendMessage(Text.literal("§c☄ TNT METEOR RAIN!"), true);
            }
            CLOCKS.put(player.getUuid(), remaining);
        }
        if (ticks % 1200 == 0) CLOCKS.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
    }

    private static void spawnTntShower(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Random random = world.getRandom();
        for (int i = 0; i < TNT_COUNT; i++) {
            Vec3d offset;
            if (i < TARGETED_TNT_COUNT) {
                double angle = (Math.PI * 2 * i / TARGETED_TNT_COUNT) + random.nextDouble() * .06;
                double ringRadius = 8 + random.nextDouble() * 4;
                offset = new Vec3d(Math.cos(angle) * ringRadius, 0, Math.sin(angle) * ringRadius);
            } else {
                offset = randomCircle(random, TNT_RADIUS);
            }
            double x = player.getX() + offset.x;
            double z = player.getZ() + offset.z;
            double y = Math.min(world.getTopY() - 5, player.getY() + 35 + random.nextInt(22));
            TntEntity tnt = new TntEntity(world, x, y, z, player);
            tnt.setFuse(70 + random.nextInt(35));
            world.spawnEntity(tnt);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    private static void spawnLuckyShower(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Random random = world.getRandom();
        for (int i = 0; i < LUCKY_COUNT; i++) {
            Vec3d offset = randomCircle(random, LUCKY_RADIUS);
            int x = (int) Math.floor(player.getX() + offset.x);
            int z = (int) Math.floor(player.getZ() + offset.z);
            int y = (int) Math.min(world.getTopY() - 5, player.getY() + 28 + random.nextInt(13));
            BlockPos spawn = new BlockPos(x, y, z);
            if (world.getBlockState(spawn).isAir()) {
                world.setBlockState(spawn, LUCKY_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                FallingBlockEntity.spawnFromBlock(world, spawn, LUCKY_BLOCK.getDefaultState());
            }
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static Vec3d randomCircle(Random random, int radius) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        return new Vec3d(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
    }

    public static class LuckyMeteorBlock extends FallingBlock {
        public static final MapCodec<LuckyMeteorBlock> CODEC = createCodec(LuckyMeteorBlock::new);
        public LuckyMeteorBlock(Settings settings) { super(settings); }
        @Override
        protected MapCodec<? extends FallingBlock> getCodec() { return CODEC; }
        @Override
        public int getColor(BlockState state, net.minecraft.world.BlockView world, BlockPos pos) { return 0xFFE600; }
        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (world.isClient) return ActionResult.SUCCESS;
            reward((ServerWorld) world, pos, player);
            world.breakBlock(pos, false);
            return ActionResult.SUCCESS;
        }
        @Override
        public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
            if (!world.isClient && world instanceof ServerWorld serverWorld) {
                reward(serverWorld, pos, player);
            }
            return super.onBreak(world, pos, state, player);
        }

        private static void reward(ServerWorld world, BlockPos pos, PlayerEntity player) {
            Random random = world.getRandom();
            int roll = random.nextInt(100);
            if (roll < 70) {
                // 70% of blocks give a helpful resource drop on the ground.
                ItemStack prize = switch (random.nextInt(28)) {
                    case 0 -> new ItemStack(Items.DIAMOND, 1 + random.nextInt(4));
                    case 1 -> new ItemStack(Items.NETHERITE_SCRAP, 1);
                    case 2 -> new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2));
                    case 3 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
                    case 4 -> new ItemStack(Items.ENDER_PEARL, 2 + random.nextInt(5));
                    case 5 -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
                    case 6 -> new ItemStack(Items.IRON_INGOT, 5 + random.nextInt(12));
                    case 7 -> new ItemStack(Items.OBSIDIAN, 4 + random.nextInt(9));
                    case 8 -> new ItemStack(Items.BLAZE_ROD, 2 + random.nextInt(4));
                    case 9 -> new ItemStack(Items.COOKED_BEEF, 6 + random.nextInt(9));
                    case 10 -> new ItemStack(Items.DIAMOND_CHESTPLATE, 1);
                    case 11 -> new ItemStack(Items.SHIELD, 1);
                    case 12 -> new ItemStack(Items.GOLD_INGOT, 8 + random.nextInt(9));
                    case 13 -> new ItemStack(Items.EMERALD, 3 + random.nextInt(8));
                    case 14 -> new ItemStack(Items.LAPIS_LAZULI, 8 + random.nextInt(17));
                    case 15 -> new ItemStack(Items.REDSTONE, 8 + random.nextInt(17));
                    case 16 -> new ItemStack(Items.ARROW, 12 + random.nextInt(21));
                    case 17 -> new ItemStack(Items.BOW, 1);
                    case 18 -> new ItemStack(Items.IRON_PICKAXE, 1);
                    case 19 -> new ItemStack(Items.DIAMOND_SWORD, 1);
                    case 20 -> new ItemStack(Items.DIAMOND_PICKAXE, 1);
                    case 21 -> new ItemStack(Items.NETHERITE_INGOT, 1);
                    case 22 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 5 + random.nextInt(12));
                    case 23 -> new ItemStack(Items.GOLDEN_CARROT, 6 + random.nextInt(9));
                    case 24 -> new ItemStack(Items.WATER_BUCKET, 1);
                    case 25 -> new ItemStack(Items.ENDER_EYE, 1 + random.nextInt(3));
                    case 26 -> new ItemStack(Items.ANCIENT_DEBRIS, 1);
                    default -> new ItemStack(Items.DIAMOND_BOOTS, 1);
                };
                Block.dropStack(world, pos, prize);
                player.sendMessage(Text.literal("§aLucky! You got " + prize.getCount() + " x " + prize.getName().getString()), true);
            } else {
                // 30% of blocks summon strong hostile mobs; no Wither, no traps.
                EntityType<?> mob = switch (random.nextInt(12)) {
                    case 0 -> EntityType.ZOMBIE;
                    case 1 -> EntityType.SKELETON;
                    case 2 -> EntityType.CREEPER;
                    case 3 -> EntityType.ENDERMAN;
                    case 4 -> EntityType.WITHER_SKELETON;
                    case 5 -> EntityType.GHAST;
                    case 6 -> EntityType.PILLAGER;
                    case 7 -> EntityType.VINDICATOR;
                    case 8 -> EntityType.RAVAGER;
                    case 9 -> EntityType.SPIDER;
                    case 10 -> EntityType.BLAZE;
                    default -> EntityType.HUSK;
                };
                int count = mob == EntityType.GHAST || mob == EntityType.RAVAGER ? 1 : 1 + random.nextInt(3);
                for (int i = 0; i < count; i++) {
                    var entity = mob.create(world);
                    if (entity != null) {
                        double x = pos.getX() + .5 + (random.nextDouble() - .5) * 7;
                        double z = pos.getZ() + .5 + (random.nextDouble() - .5) * 7;
                        double y = pos.getY() + (mob == EntityType.GHAST ? 8 : 1);
                        entity.refreshPositionAndAngles(x, y, z, random.nextFloat() * 360, 0);
                        world.spawnEntity(entity);
                        if (entity instanceof MobEntity hostile) {
                            hostile.setTarget(player);
                        }
                    }
                }
                player.sendMessage(Text.literal("§cUNLUCKY! Hostile mobs incoming!"), true);
            }
        }
    }
}
