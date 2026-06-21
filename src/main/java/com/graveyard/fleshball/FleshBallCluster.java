package com.graveyard.fleshball;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;

public class FleshBallCluster {
    private final Entity centerCore;
    private final List<DisplayCorpse> clusterCorpses = new ArrayList<>();
    private final double sphereRadius = 2.5;
    private final int targetCount;

    public FleshBallCluster(Entity centerCore, int targetCount) {
        this.centerCore = centerCore;
        this.targetCount = targetCount;
        generateSphericalMatrix(targetCount, sphereRadius);
    }

    public void generateSphericalMatrix(int targetCount, double sphereRadius) {
        this.clusterCorpses.clear(); 
        
        // Fibonacci sphere generation for optimal surface distribution
        double goldenRatioPhi = Math.PI * (3.0 - Math.sqrt(5.0)); 

        for (int i = 0; i < targetCount; i++) {
            double y = 1.0 - ((double) i / (targetCount - 1)) * 2.0;
            double radiusAtY = Math.sqrt(1.0 - y * y);
            double theta = i * goldenRatioPhi;

            double x = Math.cos(theta) * radiusAtY;
            double z = Math.sin(theta) * radiusAtY;

            Vector structuralOffset = new Vector(x, y, z).multiply(sphereRadius);

            // We no longer need to calculate static yaw/pitch here. 
            // The DisplayCorpse handles its own dynamic rotation based on velocity.
            clusterCorpses.add(new DisplayCorpse(centerCore, structuralOffset));
        }
    }

    public Entity getCenterCore() {
        return this.centerCore;
    }

    // Executes the control loop for the physics engine
    public void tickCluster(Vector currentCoreVelocity) {
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.tickPhysics(currentCoreVelocity);
        }
    }
    
    // Instantiates the structural matrix
    public void spawn() {
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.spawn();
        }
    }

    // Crucial for memory management: cleans up all child entities
    public void despawn() {
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.despawn();
        }
    }
}