package com.graveyard.fleshball;

import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;

public class FleshBallCluster {
    private final Entity centerCore;
    private final List<CustomCorpse> clusterCorpses = new ArrayList<>();
    private final double sphereRadius = 2.0; 
    private final int targetCount; // FIX: Added missing variable

    private final EntityType[] corpsePool = {
        EntityType.ZOMBIE, 
        EntityType.SKELETON, 
        EntityType.DROWNED, 
        EntityType.HUSK
    };

    public FleshBallCluster(FleshBallPlugin plugin, Entity centerCore, int targetCount) {
        this.centerCore = centerCore;
        this.targetCount = targetCount;
        generateSphericalMatrix(targetCount, sphereRadius); // FIX: Added sphereRadius argument
    }

    public void generateSphericalMatrix(int targetCount, double sphereRadius) {
        this.clusterCorpses.clear(); // Wipe the previous layout
        
        double goldenRatioPhi = Math.PI * (3.0 - Math.sqrt(5.0)); 

        for (int i = 0; i < targetCount; i++) {
            // Normalize y across the target count
            double y = 1.0 - ((double) i / (targetCount - 1)) * 2.0;
            double radiusAtY = Math.sqrt(1.0 - y * y);
            double theta = i * goldenRatioPhi;

            double x = Math.cos(theta) * radiusAtY;
            double z = Math.sin(theta) * radiusAtY;

            Vector structuralOffset = new Vector(x, y, z).multiply(sphereRadius);
            EntityType chosenType = corpsePool[i % corpsePool.length];

            // Outward-facing look vectors
            float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0f;
            float pitch = (float) Math.toDegrees(Math.asin(-y));

            clusterCorpses.add(new CustomCorpse(centerCore, structuralOffset, chosenType, yaw, pitch));
        }
    }

    // 1. This getter so the Plugin class can check if the core is still alive
    public Entity getCenterCore() {
        return this.centerCore;
    }

    // 2. Signature to accept the Player
    public void tickCluster(Player player, Vector currentCoreVelocity) {
        for (CustomCorpse corpse : clusterCorpses) {
            // Pass the player down into the physics engine
            corpse.tickPhysics(player, currentCoreVelocity);
        }
    }
    
    // 3. A method to initially spawn the boss for a player
    public void spawnForPlayer(Player player) {
        for (CustomCorpse corpse : clusterCorpses) {
            corpse.spawnForPlayer(player);
        }
    }

}
