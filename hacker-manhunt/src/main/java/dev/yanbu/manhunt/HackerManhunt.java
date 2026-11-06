package dev.yanbu.manhunt;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.TypedActionResult;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.util.Identifier;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.EntityType;
import java.util.Map;
import java.util.HashMap;
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
    // NOTE: markers are world entities and currently visible to all players, not just hacker.
    private static final Map<BlockPos, ArmorStandEntity> oreMarkers = new HashMap<>();
    private static final Identifier RAPID_ATTACK = Identifier.of("hackermanhunt", "rapid_attack");
    private static final List<TntEntity> boostedTnt = new ArrayList<>();

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
                    ServerPlayerEntity current = hacker == null ? null : c.getSource().getServer().getPlayerManager().getPlayer(hacker);
                    if (current != null && current.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED) != null)
                        current.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED).removeModifier(RAPID_ATTACK);
                    clearOreMarkers();
                    hacker = null; hunters.clear(); active = false; ticks = 0; stage = 0;
                    c.getSource().sendFeedback(() -> Text.literal("Manhunt reset"), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("blast").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!p.getUuid().equals(hacker) || stage < 4 || blastCooldown > 0) return 0;
                    launchBlast(p);
                    return Command.SINGLE_SUCCESS;
                }))
        ));
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack held = player.getStackInHand(hand);
            if (world.isClient || !(player instanceof ServerPlayerEntity p)
                || hacker == null || !p.getUuid().equals(hacker) || stage < 4
                || !held.isOf(Items.FIRE_CHARGE)) return TypedActionResult.pass(held);
            if (blastCooldown > 0) return TypedActionResult.fail(held);
            launchBlast(p);
            return TypedActionResult.success(held);
        });
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
            if (h != null) {
                grantFlight(h);
                if (stage >= 2) h.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 2, false, false));
                if (stage >= 4 && !hasCharge(h)) h.giveItemStack(new ItemStack(Items.FIRE_CHARGE));
            }
            if (blastCooldown > 0) blastCooldown--;
            boostedTnt.removeIf(tnt -> {
                if (!tnt.isAlive()) return true;
                if (tnt.getFuse() <= 1) {
                    tnt.getWorld().createExplosion(tnt, tnt.getX(), tnt.getY(), tnt.getZ(),
                        4.8f, net.minecraft.world.World.ExplosionSourceType.TNT);
                    tnt.discard();
                    return true;
                }
                return false;
            });

            if (h != null) {
                var attack = h.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
                if (attack != null) {
                    if (stage >= 3 && !attack.hasModifier(RAPID_ATTACK)) {
                        attack.addPersistentModifier(new EntityAttributeModifier(RAPID_ATTACK, 100.0,
                            EntityAttributeModifier.Operation.ADD_VALUE));
                    } else if (stage < 3 && attack.hasModifier(RAPID_ATTACK)) {
                        attack.removeModifier(RAPID_ATTACK);
                    }
                }
            }
            // Tracking runs immediately, even before the first hacker kill.
            if (h != null) updateCompasses(server, h);
            if (!active) { clearOreMarkers(); return; }
            ticks++;
            if (ticks >= intervalSeconds * 20 && stage < 5) {
                ticks = 0; stage++;
                progress(server, h);
            }
            if (++scanCooldown >= 40) {
                scanCooldown = 0;
                if (h != null && stage >= 1) scan(h);

            }
        });
    }

    private static void updateCompasses(MinecraftServer server, ServerPlayerEntity h) {
        for (UUID id : hunters) {
            ServerPlayerEntity hunter = server.getPlayerManager().getPlayer(id);
            if (hunter == null || hunter.getWorld() != h.getWorld()) continue;
            // Only update when target moves at least two blocks, to avoid jitter
            // caused by changing the lodestone target every frame.
            GlobalPos target = GlobalPos.create(h.getWorld().getRegistryKey(), h.getBlockPos());
            for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
                ItemStack stack = hunter.getInventory().getStack(slot);
                if (!stack.isOf(Items.COMPASS)) continue;
                var tracker = stack.get(DataComponentTypes.LODESTONE_TRACKER);
                if (tracker == null || tracker.target().isEmpty()
                    || tracker.target().get().pos().getSquaredDistance(h.getBlockPos()) >= 4.0) {
                    stack.set(DataComponentTypes.LODESTONE_TRACKER,
                        new LodestoneTrackerComponent(Optional.of(target), false));
                }
            }
        }
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
        String[] powers = {"", "X-ray scan unlocked", "Speed unlocked", "Rapid attacks unlocked automatically", "Infinite explosive fire charge (right click)", "Final hunter gear"};
        announce(server, "STAGE " + stage + " - " + powers[stage]);
        if (h != null && stage >= 4) h.giveItemStack(new ItemStack(Items.FIRE_CHARGE));
        if (stage != 4) for (UUID id : hunters) {
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
        if (level >= 3) {
            p.giveItemStack(new ItemStack(level >= 5 ? Items.NETHERITE_AXE : Items.DIAMOND_AXE));
            p.giveItemStack(new ItemStack(Items.BOW));
            p.giveItemStack(new ItemStack(Items.ARROW, 64));
        }
    }
    private static void clearOreMarkers() {
        for (ArmorStandEntity marker : oreMarkers.values()) marker.discard();
        oreMarkers.clear();
    }
    private static boolean hasCharge(ServerPlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++)
            if (p.getInventory().getStack(i).isOf(Items.FIRE_CHARGE)) return true;
        return false;
    }
    private static void launchBlast(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f);
        TntEntity tnt = new TntEntity(p.getWorld(), p.getX() + look.x * 2, p.getEyeY(), p.getZ() + look.z * 2, p);
        tnt.setFuse(22);
        tnt.setVelocity(look.multiply(2.5));
        p.getWorld().spawnEntity(tnt);
        boostedTnt.add(tnt); // 4.8 = 20% above vanilla TNT power 4.0
        blastCooldown = 50;
    }
    private static void scan(ServerPlayerEntity h) {
        for (ServerPlayerEntity p : h.getServer().getPlayerManager().getPlayerList())
            if (p != h && p.getWorld() == h.getWorld() && p.squaredDistanceTo(h) < 4096)
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0, false, false));
        h.getWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, h.getBoundingBox().expand(24), e -> true)
            .forEach(e -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0, false, false)));
        BlockPos center = h.getBlockPos();
        Map<BlockPos, String> found = new HashMap<>();
        // Limit the scan to reduce lag and entity spam.
        for (BlockPos pos : BlockPos.iterate(center.add(-10,-8,-10), center.add(10,8,10))) {
            var block = h.getWorld().getBlockState(pos).getBlock();
            String ore = null;
            if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) ore = "Diamond";
            else if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) ore = "Iron";
            else if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) ore = "Coal";
            else if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) ore = "Emerald";
            else if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) ore = "Gold";
            else if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) ore = "Redstone";
            else if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) ore = "Lapis";
            if (ore != null && found.size() < 80) found.put(pos.toImmutable(), ore);
        }
        oreMarkers.entrySet().removeIf(e -> {
            if (found.containsKey(e.getKey()) && e.getValue().isAlive()) return false;
            e.getValue().discard();
            return true;
        });
        for (var e : found.entrySet()) {
            if (oreMarkers.containsKey(e.getKey())) continue;
            ArmorStandEntity marker = new ArmorStandEntity(EntityType.ARMOR_STAND, h.getWorld());
            marker.refreshPositionAndAngles(e.getKey().getX() + .5, e.getKey().getY() + .5, e.getKey().getZ() + .5, 0, 0);
            marker.setInvisible(true);
            marker.setInvulnerable(true);
            marker.setNoGravity(true);
            marker.setGlowing(true);
            marker.setCustomName(Text.literal(e.getValue() + " ore"));
            marker.setCustomNameVisible(true);
            h.getWorld().spawnEntity(marker);
            oreMarkers.put(e.getKey(), marker);
        }
    }
}
