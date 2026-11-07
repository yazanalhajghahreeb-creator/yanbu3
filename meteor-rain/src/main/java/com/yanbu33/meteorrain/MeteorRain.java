package com.yanbu33.meteorrain;

import com.mojang.brigadier.CommandDispatcher;
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
    private static final int TNT_COUNT = 120;
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
            Vec3d offset = randomCircle(random, TNT_RADIUS);
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
        public LuckyMeteorBlock(Settings settings) { super(settings); }
        @Override
        public int getColor(BlockState state, net.minecraft.world.BlockView world, BlockPos pos) { return 0xFFE600; }
        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (world.isClient) return ActionResult.SUCCESS;
            reward((ServerWorld) world, pos, player);
            world.breakBlock(pos, false);
            return ActionResult.SUCCESS;
        }
        private static void reward(ServerWorld world, BlockPos pos, PlayerEntity player) {
            Random random = world.getRandom();
            int roll = random.nextInt(12);
            switch (roll) {
                case 0 -> player.giveItemStack(new ItemStack(Items.DIAMOND, 2 + random.nextInt(5)));
                case 1 -> player.giveItemStack(new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(3)));
                case 2 -> player.giveItemStack(new ItemStack(Items.ENDER_PEARL, 2 + random.nextInt(5)));
                case 3 -> player.giveItemStack(new ItemStack(Items.IRON_INGOT, 8 + random.nextInt(9)));
                case 4 -> player.giveItemStack(new ItemStack(Items.TOTEM_OF_UNDYING));
                case 5 -> player.giveItemStack(new ItemStack(Items.NETHERITE_SCRAP));
                case 6 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 20, 1));
                case 7 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 20, 1));
                case 8 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 25, 1));
                case 9 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 10, 1));
                case 10 -> player.giveItemStack(new ItemStack(Items.BREAD, 12));
                case 11 -> { TntEntity tnt = new TntEntity(world, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, player); tnt.setFuse(70); world.spawnEntity(tnt); }
            }
            player.sendMessage(Text.literal(roll == 9 || roll == 11 ? "§cUnlucky!" : "§aLucky block opened!"), true);
        }
    }
}
