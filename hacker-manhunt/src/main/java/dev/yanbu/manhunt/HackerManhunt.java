package dev.yanbu.manhunt;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HackerManhunt implements ModInitializer {
    private static UUID hacker;
    private static final Set<UUID> hunters = new LinkedHashSet<>();
    private static boolean active = false;
    private static int ticks = 0;
    private static int stage = 0;
    private static int intervalSeconds = 300;
    private static int scanCooldown = 0;
    private static int blastCooldown = 0;
    private static int strikeCooldown = 0;

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
            literal("manhunt")
                .then(literal("hacker").requires(s -> s.hasPermissionLevel(2))
                    .then(argument("player", EntityArgumentType.player()).executes(c -> {
                        hacker = EntityArgumentType.getPlayer(c, "player").getUuid();
                        grantFlight(EntityArgumentType.getPlayer(c, "player"));
                        c.getSource().sendFeedback(() -> Text.literal("Hacker selected. Flight enabled."), true);
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(literal("hunter").requires(s -> s.hasPermissionLevel(2))
                    .then(argument("player", EntityArgumentType.player()).executes(c -> {
                        ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
                        hunters.add(p.getUuid());
                        p.giveItemStack(new ItemStack(Items.COMPASS));
                        c.getSource().sendFeedback(() -> Text.literal("Hunter added: " + p.getName().getString()), true);
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(literal("interval").requires(s -> s.hasPermissionLevel(2))
                    .then(argument("seconds", IntegerArgumentType.integer(10, 3600)).executes(c -> {
                        intervalSeconds = IntegerArgumentType.getInteger(c, "seconds");
                        c.getSource().sendFeedback(() -> Text.literal("Stage interval: " + intervalSeconds + " seconds"), true);
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(literal("reset").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    if (hacker != null) {
                        ServerPlayerEntity old = c.getSource().getServer().getPlayerManager().getPlayer(hacker);
                        if (old != null) { old.getAbilities().allowFlying = false; old.getAbilities().flying = false; old.sendAbilitiesUpdate(); }
                    }
                    hacker = null; hunters.clear(); active = false; ticks = 0; stage = 0;
                    c.getSource().sendFeedback(() -> Text.literal("Manhunt reset"), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("strike").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!p.getUuid().equals(hacker) || stage < 3 || strikeCooldown > 0) return 0;
                    ServerPlayerEntity closest = null;
                    double best = 25.0;
                    for (UUID id : hunters) {
                        ServerPlayerEntity target = p.getServer().getPlayerManager().getPlayer(id);
                        if (target != null && target.isAlive() && target.getWorld() == p.getWorld() && p.squaredDistanceTo(target) <= best) {
                            best = p.squaredDistanceTo(target); closest = target;
                        }
                    }
                    if (closest == null) { p.sendMessage(Text.literal("No hunter within 5 blocks"), true); return 0; }
                    closest.damage(p.getDamageSources().playerAttack(p), 8.0f);
                    strikeCooldown = 60;
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("blast").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!p.getUuid().equals(hacker) || stage < 4 || blastCooldown > 0) return 0;
                    Vec3d look = p.getRotationVec(1.0f);
                    TntEntity tnt = new TntEntity(p.getWorld(), p.getX() + look.x * 2, p.getEyeY(), p.getZ() + look.z * 2, p);
                    tnt.setFuse(45);
                    tnt.setVelocity(look.multiply(1.4));
                    p.getWorld().spawnEntity(tnt);
                    blastCooldown = 100;
                    return Command.SINGLE_SUCCESS;
                }))
        ));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (active || !(entity instanceof ServerPlayerEntity victim) || !hunters.contains(victim.getUuid())) return;
            if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker.getUuid().equals(hacker)) {
                active = true; ticks = 0; stage = 0;
                MinecraftServer server = victim.getServer();
                if (server != null) announce(server, "FIRST KILL! Evolution timer started.");
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (hacker == null) return;
            ServerPlayerEntity h = server.getPlayerManager().getPlayer(hacker);
            if (h != null) grantFlight(h);
            if (blastCooldown > 0) blastCooldown--;
            if (strikeCooldown > 0) strikeCooldown--;
            if (!active) return;
            ticks++;
            if (ticks >= intervalSeconds * 20 && stage < 5) {
                ticks = 0; stage++;
                progress(server, h);
            }
            if (++scanCooldown >= 100) {
                scanCooldown = 0;
                if (h != null && stage >= 1) scan(h);
                for (UUID id : hunters) {
                    ServerPlayerEntity hunter = server.getPlayerManager().getPlayer(id);
                    if (hunter != null && h != null && hunter.getWorld() == h.getWorld()
                        && hunter.getMainHandStack().isOf(Items.COMPASS)) {
                        hunter.sendMessage(Text.literal("Hacker: " + h.getBlockX() + ", " + h.getBlockY() + ", " + h.getBlockZ()
                            + " | " + (int)Math.sqrt(hunter.squaredDistanceTo(h)) + " blocks"), true);
                    }
                }
            }
        });
    }

    private static void grantFlight(ServerPlayerEntity player) {
        if (!player.getAbilities().allowFlying) {
            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
        }
    }
    private static void announce(MinecraftServer s, String msg) {
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) p.sendMessage(Text.literal("[Manhunt] " + msg), false);
    }
    private static void progress(MinecraftServer server, ServerPlayerEntity h) {
        String[] powers = {"", "X-ray scan unlocked", "Speed unlocked", "Wall strike: /manhunt strike", "Explosive power: /manhunt blast", "Final hunter gear"};
        announce(server, "STAGE " + stage + " - " + powers[stage]);
        if (h != null && stage >= 2) h.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 999999, 1, false, false));
        for (UUID id : hunters) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) equip(p, stage);
        }
    }
    private static void equip(ServerPlayerEntity p, int level) {
        Item[] armor = switch(level) {
            case 1 -> new Item[]{Items.LEATHER_BOOTS, Items.LEATHER_LEGGINGS, Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET};
            case 2 -> new Item[]{Items.IRON_BOOTS, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE, Items.IRON_HELMET};
            case 3,4 -> new Item[]{Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET};
            default -> new Item[]{Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET};
        };
        p.equipStack(EquipmentSlot.FEET, new ItemStack(armor[0]));
        p.equipStack(EquipmentSlot.LEGS, new ItemStack(armor[1]));
        p.equipStack(EquipmentSlot.CHEST, new ItemStack(armor[2]));
        p.equipStack(EquipmentSlot.HEAD, new ItemStack(armor[3]));
        Item sword = level == 1 ? Items.WOODEN_SWORD : level == 2 ? Items.IRON_SWORD : level <= 4 ? Items.DIAMOND_SWORD : Items.NETHERITE_SWORD;
        Item pick = level == 1 ? Items.WOODEN_PICKAXE : level == 2 ? Items.IRON_PICKAXE : level <= 4 ? Items.DIAMOND_PICKAXE : Items.NETHERITE_PICKAXE;
        p.giveItemStack(new ItemStack(sword));
        p.giveItemStack(new ItemStack(pick));
    }
    private static void scan(ServerPlayerEntity h) {
        for (ServerPlayerEntity p : h.getServer().getPlayerManager().getPlayerList())
            if (p != h && p.getWorld() == h.getWorld() && p.squaredDistanceTo(h) < 4096)
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0, false, false));
        h.getWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, h.getBoundingBox().expand(24), e -> true)
            .forEach(e -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0, false, false)));
        BlockPos center = h.getBlockPos();
        BlockPos closest = null;
        int best = Integer.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(center.add(-12,-12,-12),center.add(12,12,12))) {
            var block = h.getWorld().getBlockState(pos).getBlock();
            if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
                int d = (int)pos.getSquaredDistance(center);
                if (d < best) { best = d; closest = pos.toImmutable(); }
            }
        }
        if (closest != null) h.sendMessage(Text.literal("Ore scan: " + closest.getX() + " " + closest.getY() + " " + closest.getZ()), true);
    }
}
