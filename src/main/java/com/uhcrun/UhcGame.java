package com.uhcrun;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.core.BlockPos;
import net.minecraft.server.ServerScoreboard;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lógica de la partida de UHC Run. */
public final class UhcGame {
    private static final String OBJECTIVE = "uhc_vida";
    private static final double DEFAULT_BORDER = 5.9999968E7;

    public static boolean running = false;

    private static MinecraftServer server;
    private static long ticks;
    private static final Set<UUID> participants = new HashSet<>();
    private static final Set<UUID> dead = new HashSet<>();
    private static final Map<UUID, String> unitOf = new HashMap<>();
    private static final Map<UUID, String> names = new HashMap<>();
    private static ServerBossEvent bar;

    private static double currentSize;
    private static long nextShrinkSec;
    private static long shrinkEndSec;
    private static long stageStartSec;
    private static boolean pvpOn;
    private static int startUnits;

    private UhcGame() {}

    // ------------------------------------------------------------------ inicio / fin

    /** @return null si todo bien, o el mensaje de error. */
    public static String start(CommandSourceStack src) {
        if (running) return "Ya hay una partida en curso. Usa /uhc stop primero.";
        server = src.getServer();
        ServerLevel level = server.overworld();

        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.getTags().contains("uhc_espectador")) players.add(p);
        }
        if (players.isEmpty()) return "No hay jugadores para la partida.";

        Vec3 center = src.getPosition();
        if (src.getLevel() != level) center = Vec3.atCenterOf(level.getSharedSpawnPos());

        // Borde
        WorldBorder wb = level.getWorldBorder();
        currentSize = UhcSettings.get("borde_inicial");
        wb.setCenter(center.x, center.z);
        wb.setSize(currentSize);
        wb.setDamagePerBlock(UhcSettings.get("danio_borde"));
        wb.setDamageSafeZone(0.0);
        wb.setWarningBlocks(30);
        wb.setWarningTime(15);

        // Reglas
        server.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        if (server.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            server.setDifficulty(Difficulty.NORMAL, true);
        }
        server.setPvpAllowed(false);
        level.setDayTime(0L);

        // Participantes
        participants.clear();
        dead.clear();
        unitOf.clear();
        names.clear();
        for (ServerPlayer p : players) {
            participants.add(p.getUUID());
            unitOf.put(p.getUUID(), unit(p));
            names.put(p.getUUID(), p.getGameProfile().getName());
        }
        startUnits = new HashSet<>(unitOf.values()).size();

        // Dispersión (los de un mismo equipo salen juntos)
        Map<String, Vec3> spots = new HashMap<>();
        boolean scatter = UhcSettings.get("dispersar") == 1;
        double half = Math.max(10.0, currentSize / 2.0 - 25.0);
        for (ServerPlayer p : players) {
            if (scatter) {
                Vec3 spot = spots.computeIfAbsent(unit(p), k -> randomSpot(level, center, half));
                p.teleportTo(level, spot.x, spot.y, spot.z, p.getYRot(), 0.0F);
            }
            resetPlayer(p);
        }

        // Vida en la lista de jugadores y bajo el nombre
        ServerScoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE);
        if (obj == null) {
            obj = sb.addObjective(OBJECTIVE, ObjectiveCriteria.HEALTH, Component.literal("❤"),
                    ObjectiveCriteria.RenderType.HEARTS, true, null);
        }
        sb.setDisplayObjective(DisplaySlot.LIST, obj);
        sb.setDisplayObjective(DisplaySlot.BELOW_NAME, obj);

        // Barra de estado
        bar = new ServerBossEvent(Component.literal("UHC Run"), BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) bar.addPlayer(p);

        // Cronograma
        ticks = 0;
        pvpOn = false;
        stageStartSec = 0;
        shrinkEndSec = 0;
        nextShrinkSec = UhcSettings.get("minutos_primer_cierre") * 60L;
        running = true;

        for (ServerPlayer p : players) {
            title(p, "§6§lUHC RUN", "§e¡Suerte! PvP en " + UhcSettings.get("minutos_pvp") + " min");
            sound(p, SoundEvents.ENDER_DRAGON_GROWL);
        }
        broadcast(Component.literal("§6[UHC] §fLa partida comenzó con §e" + players.size()
                + "§f jugadores. Borde: §e" + (int) currentSize + "§f bloques."));
        return null;
    }

    public static void stop(boolean announce) {
        if (!running) return;
        running = false;
        ServerLevel level = server.overworld();

        WorldBorder wb = level.getWorldBorder();
        wb.setCenter(0.0, 0.0);
        wb.setSize(DEFAULT_BORDER);
        wb.setDamagePerBlock(0.2);
        wb.setDamageSafeZone(5.0);
        wb.setWarningBlocks(5);
        wb.setWarningTime(15);

        server.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(true, server);
        server.setPvpAllowed(true);

        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        ServerScoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE);
        if (obj != null) sb.removeObjective(obj);

        if (announce) broadcast(Component.literal("§6[UHC] §fLa partida terminó."));
        participants.clear();
        dead.clear();
        unitOf.clear();
        names.clear();
    }

    /** Fuerza que el siguiente cierre del borde empiece ahora mismo. */
    public static boolean forceShrink() {
        if (!running) return false;
        nextShrinkSec = ticks / 20;
        return true;
    }

    public static String status() {
        if (!running) return "No hay ninguna partida en curso.";
        long sec = ticks / 20;
        return "Partida en curso: " + fmt(sec) + " | vivos: " + aliveCount() + "/" + participants.size()
                + " | PvP: " + (pvpOn ? "activado" : "desactivado")
                + " | borde: " + (int) server.overworld().getWorldBorder().getSize();
    }

    // ------------------------------------------------------------------ tick

    public static void tick() {
        if (!running) return;
        ticks++;
        if (ticks % 20 != 0) return;
        long sec = ticks / 20;
        ServerLevel level = server.overworld();

        long pvpAt = UhcSettings.get("minutos_pvp") * 60L;
        int finalSize = UhcSettings.get("borde_final");
        int shrinkSecs = UhcSettings.get("segundos_cierre");
        long stageSecs = Math.max(UhcSettings.get("minutos_entre_cierres") * 60L, shrinkSecs + 30L);
        boolean canShrink = currentSize > finalSize + 0.5;

        // PvP
        if (!pvpOn) {
            if (sec >= pvpAt) {
                pvpOn = true;
                server.setPvpAllowed(true);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    title(p, "§c§lPVP ACTIVADO", "§e¡A pelear!");
                    sound(p, SoundEvents.WITHER_SPAWN);
                }
                broadcast(Component.literal("§4[UHC] §c¡El PvP está activado!"));
            } else if (pvpAt - sec == 60) {
                broadcast(Component.literal("§e[UHC] El PvP se activa en 1 minuto."));
            }
        }

        // Cierre del borde
        if (canShrink) {
            long left = nextShrinkSec - sec;
            double target = Math.max(finalSize, Math.floor(currentSize * UhcSettings.get("porcentaje_cierre") / 100.0));
            if (left == 60) {
                broadcast(Component.literal("§e[UHC] El borde empezará a cerrarse en 1 minuto hasta §6"
                        + (int) target + "§e bloques."));
            } else if (left == 10) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    title(p, "§c¡El borde se cierra!", "§eEn 10 segundos");
                    sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP);
                }
            }
            if (left <= 0) {
                WorldBorder wb = level.getWorldBorder();
                wb.lerpSizeBetween(wb.getSize(), target, shrinkSecs * 1000L);
                currentSize = target;
                stageStartSec = sec;
                shrinkEndSec = sec + shrinkSecs;
                nextShrinkSec = sec + stageSecs;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    title(p, "§c§lBORDE CERRÁNDOSE", "§eHasta " + (int) target + " bloques");
                    sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER);
                }
                broadcast(Component.literal("§4[UHC] §cEl borde se cierra hasta §e" + (int) target
                        + "§c bloques."));
                if (target <= finalSize) {
                    broadcast(Component.literal("§4[UHC] §c¡Zona final!"));
                }
            }
        }

        updateBar(sec, pvpAt, finalSize, shrinkSecs);
        checkWin();
    }

    private static void updateBar(long sec, long pvpAt, int finalSize, int shrinkSecs) {
        if (bar == null) return;
        String text;
        float progress;
        BossEvent.BossBarColor color;
        if (!pvpOn) {
            text = "PvP en " + fmt(pvpAt - sec);
            progress = pvpAt <= 0 ? 1f : (float) sec / pvpAt;
            color = BossEvent.BossBarColor.GREEN;
        } else if (sec < shrinkEndSec) {
            text = "¡Borde cerrándose! → " + (int) currentSize + " bloques";
            progress = 1f - (float) (shrinkEndSec - sec) / Math.max(1, shrinkSecs);
            color = BossEvent.BossBarColor.RED;
        } else if (currentSize > finalSize + 0.5) {
            long left = Math.max(0, nextShrinkSec - sec);
            long span = Math.max(1, nextShrinkSec - stageStartSec);
            text = "Próximo cierre en " + fmt(left) + " (borde: " + (int) currentSize + ")";
            progress = 1f - (float) left / span;
            color = BossEvent.BossBarColor.YELLOW;
        } else {
            text = "Zona final: " + (int) currentSize + " bloques";
            progress = 1f;
            color = BossEvent.BossBarColor.PURPLE;
        }
        bar.setName(Component.literal(text + "  |  Vivos: " + aliveCount()));
        bar.setProgress(Math.max(0f, Math.min(1f, progress)));
        bar.setColor(color);
    }

    // ------------------------------------------------------------------ muertes y victoria

    public static void onDeath(LivingDeathEvent event, ServerPlayer p) {
        if (!running || !participants.contains(p.getUUID()) || dead.contains(p.getUUID())) return;

        event.setCanceled(true);
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.removeAllEffects();
        p.getInventory().dropAll();

        if (UhcSettings.get("cabeza_dorada") == 1) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            GameProfile profile = p.getGameProfile();
            head.set(DataComponents.PROFILE, new ResolvableProfile(profile));
            p.spawnAtLocation(head);
        }

        dead.add(p.getUUID());
        p.setGameMode(GameType.SPECTATOR);

        Component msg = event.getSource().getLocalizedDeathMessage(p);
        broadcast(Component.literal("§c☠ ").append(msg));
        broadcast(Component.literal("§7Quedan §f" + aliveCount() + "§7 jugadores vivos."));
        for (ServerPlayer o : server.getPlayerList().getPlayers()) sound(o, SoundEvents.LIGHTNING_BOLT_THUNDER);
        title(p, "§c§lELIMINADO", "§7Ahora eres espectador");
    }

    public static void onLogin(ServerPlayer p) {
        if (!running) return;
        if (bar != null) bar.addPlayer(p);
        if (!participants.contains(p.getUUID())) {
            p.setGameMode(GameType.SPECTATOR);
            p.sendSystemMessage(Component.literal("§6[UHC] §fHay una partida en curso: entras como espectador."));
        }
    }

    private static void checkWin() {
        if (startUnits < 2) return;
        Set<String> aliveUnits = new HashSet<>();
        List<String> aliveNames = new ArrayList<>();
        for (UUID id : participants) {
            if (dead.contains(id)) continue;
            aliveUnits.add(unitOf.get(id));
            aliveNames.add(names.getOrDefault(id, "?"));
        }
        if (aliveUnits.size() > 1) return;

        String winner = aliveNames.isEmpty() ? "nadie" : String.join(", ", aliveNames);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            title(p, "§6§l¡VICTORIA!", "§e" + winner);
            sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
        }
        broadcast(Component.literal("§6[UHC] §e¡Ganó: §f" + winner + "§e!"));
        stop(false);
    }

    private static int aliveCount() {
        int n = 0;
        for (UUID id : participants) if (!dead.contains(id)) n++;
        return n;
    }

    // ------------------------------------------------------------------ utilidades

    private static String unit(ServerPlayer p) {
        return p.getTeam() != null ? "t:" + p.getTeam().getName() : "p:" + p.getUUID();
    }

    private static void resetPlayer(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20.0F);
        p.setExperienceLevels(0);
        p.setExperiencePoints(0);
        p.clearFire();
        p.fallDistance = 0.0F;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 30, 4));
    }

    private static Vec3 randomSpot(ServerLevel level, Vec3 center, double half) {
        double bx = center.x;
        double bz = center.z;
        for (int i = 0; i < 40; i++) {
            int x = (int) Math.floor(center.x + (level.random.nextDouble() * 2 - 1) * half);
            int z = (int) Math.floor(center.z + (level.random.nextDouble() * 2 - 1) * half);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos below = new BlockPos(x, y - 1, z);
            BlockState st = level.getBlockState(below);
            if (!level.getFluidState(below).isEmpty() || st.is(BlockTags.LEAVES) || st.isAir()) continue;
            if (y <= level.getMinBuildHeight() + 1) continue;
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        int x = (int) Math.floor(bx);
        int z = (int) Math.floor(bz);
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    private static void broadcast(Component c) {
        PlayerList list = server.getPlayerList();
        list.broadcastSystemMessage(c, false);
    }

    private static void title(ServerPlayer p, String title, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub)));
    }

    private static void sound(ServerPlayer p, SoundEvent ev) {
        p.playNotifySound(ev, SoundSource.MASTER, 1.0F, 1.0F);
    }

    private static String fmt(long seconds) {
        long s = Math.max(0, seconds);
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
