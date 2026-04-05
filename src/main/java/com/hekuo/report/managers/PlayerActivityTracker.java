package com.hekuo.report.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayerActivityTracker implements Listener {

    private final HekuoReport plugin;
    
    // Player UUID -> ActivityData
    private final Map<UUID, PlayerActivityData> playerActivities = new ConcurrentHashMap<>();
    
    // Algorithm thresholds
    private static final int HIGH_CPS_THRESHOLD = 16;        // CPS above this is suspicious (autoclicker)
    private static final int EXTREME_CPS_THRESHOLD = 25;     // Very likely autoclicker
    private static final int ATTACK_THRESHOLD = 8;           // Attacks per second threshold (killaura)
    private static final int MINING_THRESHOLD = 12;          // Blocks broken per second (nuker)
    private static final double TELEPORT_THRESHOLD = 50;     // Distance for teleport check (blocks)

    public PlayerActivityTracker(HekuoReport plugin) {
        this.plugin = plugin;
        
        // Track existing online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerActivities.put(player.getUniqueId(), new PlayerActivityData());
        }
        
        // Start periodic analysis task (every 5 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                analyzeAllPlayers();
            }
        }.runTaskTimer(plugin, 100L, 100L); // 5 seconds = 100 ticks
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        
        PlayerActivityData data = getActivityData(attacker);
        long now = System.currentTimeMillis();
        
        data.attackEvents.add(new ActivityEvent(now, 
            "ATTACK", event.getEntityType().name(), 
            String.format("%.1f damage", event.getFinalDamage()),
            formatLocation(attacker.getLocation())));
    }

    @EventHandler
    public void onPlayerArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        
        PlayerActivityData data = getActivityData(event.getPlayer());
        long now = System.currentTimeMillis();
        data.clickTimestamps.add(now);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerActivityData data = getActivityData(player);
        long now = System.currentTimeMillis();
        
        data.miningEvents.add(new ActivityEvent(now,
            "MINE_BLOCK", event.getBlock().getType().name(),
            null,
            formatLocation(event.getBlock().getLocation())));
    }

    @EventHandler  
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        
        Player player = event.getPlayer();
        PlayerActivityData data = getActivityData(player);
        long now = System.currentTimeMillis();
        double distance = event.getFrom().distance(event.getTo());
        
        // Check for teleportation (possible speed/fly hack)
        if (distance > TELEPORT_THRESHOLD && !player.isInsideVehicle() && !player.isGliding()) {
            data.suspiciousEvents.add(new ActivityEvent(now,
                "SUSPICIOUS_MOVE", "TELEPORT",
                String.format("Distance: %.1f blocks", distance),
                formatLocation(event.getTo())));
        }
        
        // Record position for movement pattern analysis
        data.lastPositions.add(new double[]{
            event.getTo().getX(), event.getTo().getY(), event.getTo().getZ()
        });
    }

    private PlayerActivityData getActivityData(Player player) {
        return playerActivities.computeIfAbsent(
            player.getUniqueId(), k -> new PlayerActivityData()
        );
    }

    /**
     * Analyze all players' activity and flag suspicious behavior
     */
    private void analyzeAllPlayers() {
        long currentTime = System.currentTimeMillis();
        long windowMs = 5000; // Analyze last 5 seconds
        
        for (Map.Entry<UUID, PlayerActivityData> entry : playerActivities.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerActivityData data = entry.getValue();
            
            // Calculate CPS (Clicks Per Second)
            data.currentCPS = calculateRate(data.clickTimestamps, currentTime, windowMs);
            
            // Calculate attack rate
            data.currentAttackRate = calculateEventsRate(data.attackEvents, currentTime, windowMs);
            
            // Calculate mining rate  
            data.currentMiningRate = calculateEventsRate(data.miningEvents, currentTime, windowMs);
            
            // Run algorithm checks
            runAlgorithmDetection(data, uuid, currentTime);
            
            // Clean old events (keep only last 10 minutes for report viewing)
            cleanupOldData(data, currentTime - 600000);
        }
    }

    private int calculateRate(Queue<Long> timestamps, long currentTime, long windowMs) {
        long cutoff = currentTime - windowMs;
        while (!timestamps.isEmpty() && timestamps.peek() < cutoff) {
            timestamps.poll();
        }
        return (int) ((timestamps.size() * 1000L) / windowMs);
    }

    private int calculateEventsRate(Queue<ActivityEvent> events, long currentTime, long windowMs) {
        long cutoff = currentTime - windowMs;
        int count = 0;
        for (ActivityEvent event : events) {
            if (event.timestamp >= cutoff) count++;
        }
        return count;
    }

    private void runAlgorithmDetection(PlayerActivityData data, UUID playerUuid, long currentTime) {
        data.detections.clear();
        
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return;
        
        // Check CPS - Autoclicker detection
        if (data.currentCPS >= EXTREME_CPS_THRESHOLD) {
            data.detections.add(new DetectionResult(
                "AUTOCLICKER",
                "EXTREME",
                "CPS: " + data.currentCPS + " (threshold: " + EXTREME_CPS_THRESHOLD + ")",
                "Extremely high click rate detected. Likely using autoclicker.",
                true
            ));
        } else if (data.currentCPS >= HIGH_CPS_THRESHOLD) {
            data.detections.add(new DetectionResult(
                "AUTOCLICKER",
                "SUSPICIOUS",
                "CPS: " + data.currentCPS + " (threshold: " + HIGH_CPS_THRESHOLD + ")",
                "High click rate detected. May be using autoclicker.",
                false
            ));
        }
        
        // Check attacks - Kill Aura detection
        if (data.currentAttackRate > ATTACK_THRESHOLD) {
            data.detections.add(new DetectionResult(
                "KILL_AURA",
                "DETECTED",
                "Attacks/5s: " + data.currentAttackRate,
                "Abnormal attack frequency. Possible kill aura or combat hacks.",
                true
            ));
        }
        
        // Check mining - Nuker detection
        if (data.currentMiningRate > MINING_THRESHOLD) {
            data.detections.add(new DetectionResult(
                "NUKER",
                "DETECTED",
                "Blocks/5s: " + data.currentMiningRate,
                "Abnormal block breaking rate. Possible nuke/mining hack.",
                true
            ));
        }
        
        // Check suspicious movement events
        List<ActivityEvent> recentSuspicious = new ArrayList<>();
        for (ActivityEvent event : data.suspiciousEvents) {
            if (event.timestamp >= currentTime - 15000) { // Last 15 seconds
                recentSuspicious.add(event);
            }
        }
        if (recentSuspicious.size() >= 3) {
            data.detections.add(new DetectionResult(
                "SPEED_FLY",
                "DETECTED",
                "Teleports in 15s: " + recentSuspicious.size(),
                "Multiple teleport-like movements detected. Possible speed/fly hacks.",
                true
            ));
        }
    }

    private void cleanupOldData(PlayerActivityData data, long cutoffTime) {
        // Clean click timestamps
        while (!data.clickTimestamps.isEmpty() && data.clickTimestamps.peek() < cutoffTime) {
            data.clickTimestamps.poll();
        }
        
        // Clean activity events
        data.attackEvents.removeIf(e -> e.timestamp < cutoffTime);
        data.miningEvents.removeIf(e -> e.timestamp < cutoffTime);
        data.suspiciousEvents.removeIf(e -> e.timestamp < cutoffTime);
        
        // Limit position history size
        while (data.lastPositions.size() > 2000) {
            data.lastPositions.poll();
        }
    }

    /**
     * Get formatted activity log for a player (for admin viewing)
     */
    public PlayerActivityData getPlayerData(UUID uuid) {
        return playerActivities.get(uuid);
    }

    public PlayerActivityData getPlayerData(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            return getPlayerData(player.getUniqueId());
        }
        return null;
    }

    public void removePlayer(UUID uuid) {
        playerActivities.remove(uuid);
    }

    private String formatLocation(org.bukkit.Location loc) {
        return String.format("[%d,%d,%d]", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // ==================== Data Classes ====================

    public static class PlayerActivityData {
        public Queue<Long> clickTimestamps = new ConcurrentLinkedQueue<>();
        public Queue<ActivityEvent> attackEvents = new ConcurrentLinkedQueue<>();
        public Queue<ActivityEvent> miningEvents = new ConcurrentLinkedQueue<>();
        public Queue<ActivityEvent> suspiciousEvents = new ConcurrentLinkedQueue<>();
        public Queue<double[]> lastPositions = new ConcurrentLinkedQueue<>();
        
        public List<DetectionResult> detections = new ArrayList<>();
        
        public int currentCPS = 0;
        public int currentAttackRate = 0;
        public int currentMiningRate = 0;
    }

    public static class ActivityEvent {
        public final long timestamp;
        public final String type;      // ATTACK, MINE_BLOCK, SUSPICIOUS_MOVE
        public final String target;
        public final String details;
        public final String location;

        public ActivityEvent(long timestamp, String type, String target, String details, String location) {
            this.timestamp = timestamp;
            this.type = type;
            this.target = target;
            this.details = details;
            this.location = location;
        }
    }

    public static class DetectionResult {
        public final String category;     // AUTOCLICKER, KILL_AURA, etc.
        public final String severity;     // DETECTED, SUSPICIOUS, EXTREME
        public final String value;        // Actual measured value
        public final String description;  Human-readable explanation
        public final boolean isFlagged;   // True if should be highlighted red

        public DetectionResult(String category, String severity, String value, String description, boolean isFlagged) {
            this.category = category;
            this.severity = severity;
            this.value = value;
            this.description = description;
            this.isFlagged = isFlagged;
        }
    }
}
