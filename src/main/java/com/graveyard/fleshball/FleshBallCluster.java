package com.graveyard.fleshball;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;

public class FleshBallCluster {
    private final Entity centerCore;
    private final List<DisplayCorpse> clusterCorpses = new ArrayList<>();
    private final double maxRadius;
    private final int targetCount;

    private double currentAngle = 0.0;
    private double rotationSpeed = 0.0;
    private double damageTransferCoefficient = 0.0;

    // Shield ability tracking variables
    private final java.util.List<ShieldInstance> activeShields = new java.util.ArrayList<>();
    private double originalRotationSpeed = 0.0;
    private boolean shieldsEnabled = false;

    public FleshBallCluster(Entity centerCore, int targetCount, double maxRadius) {
        this.centerCore = centerCore;
        this.targetCount = targetCount;
        this.maxRadius = maxRadius;
        generateBowlMatrix(this.targetCount, this.maxRadius); 
    }

    public void generateBowlMatrix(int targetCount, double maxRadius) {
        this.clusterCorpses.clear(); 
        double k = 2.0 / (maxRadius * maxRadius);
        double goldenRatioPhi = Math.PI * (3.0 - Math.sqrt(5.0)); 

        for (int i = 0; i < targetCount; i++) {
            double fraction = (double) i / (targetCount - 1);
            double currentRadius = maxRadius * Math.sqrt(fraction);
            double theta = i * goldenRatioPhi;

            double mappedX = currentRadius * Math.cos(theta);
            double mappedZ = currentRadius * Math.sin(theta);
            double mappedY = k * (currentRadius * currentRadius) - 2.0;

            Vector structuralOffset = new Vector(mappedX, mappedY, mappedZ);

            double nX = 2.0 * k * mappedX;
            double nY = -1.0;
            double nZ = 2.0 * k * mappedZ;
            Vector outwardNormal = new Vector(nX, nY, nZ).normalize();

            clusterCorpses.add(new DisplayCorpse(centerCore, structuralOffset, outwardNormal));
        }
    }

    public Entity getCenterCore() { return this.centerCore; }
    public double getRotationSpeed() { return this.rotationSpeed; }
    public void setRotationSpeed(double rotationSpeed) { this.rotationSpeed = rotationSpeed; }
    public double getDamageTransferCoefficient() { return this.damageTransferCoefficient; }
    public void setDamageTransferCoefficient(double damageTransferCoefficient) { this.damageTransferCoefficient = damageTransferCoefficient; }

    // UPDATED: Integrates directional shield vectors dynamically down to the children
    public void tickCluster(Vector currentCoreVelocity) {
        // 1. Advance the accumulation angle by our rotation speed
        // (Note: Since rotationSpeed freezes to 0 when shielding, currentAngle stops changing,
        // locking the 1/4 idle ammo corpses into a frozen, tight inner protective core!)
        this.currentAngle += this.rotationSpeed;

        // 2. Continually update real-time player coordinates for active shields
        if (shieldsEnabled) {
            updateShieldDirections();
        }

        // 3. Process individual positioning targets
        for (DisplayCorpse corpse : clusterCorpses) {
            if (shieldsEnabled) {
                ShieldInstance shield = findShieldForCorpse(corpse);
                if (shield != null) {
                    // This corpse belongs to a shield! Extract its specific index within that group
                    int subIndex = shield.getAssignedCorpses().indexOf(corpse);
                    Vector gridOffset = calculateShieldGridOffset(subIndex, shield.getDirection());
                    corpse.setShieldOverride(gridOffset);
                } else {
                    // This corpse is a part of our 1/4 reserved idle ammo pool
                    corpse.clearShieldOverride();
                }
            } else {
                // Ensure no leftover states exist when shield is offline
                corpse.clearShieldOverride();
            }

            // 4. Pass parameters down to the physics math engine
            corpse.tickPhysics(currentCoreVelocity, this.currentAngle);
        }
    }
    
    public void spawn() {
        generateBowlMatrix(this.targetCount, this.maxRadius);
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.spawn();
        }
    }

    public void despawn() {
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.despawn();
        }
    }

    // UPDATED: Implements the 1/4 Idle Ammo vs 3/4 Shield Partition Matrix
    public void activateMultiShields() {
        if (shieldsEnabled || centerCore == null || !centerCore.isValid()) return;

        // 1. Scan for up to 3 nearby survival players within 30 blocks
        java.util.List<org.bukkit.entity.Player> targets = centerCore.getWorld()
            .getNearbyEntities(centerCore.getLocation(), 30, 30, 30).stream()
            .filter(e -> e instanceof org.bukkit.entity.Player)
            .map(e -> (org.bukkit.entity.Player) e)
            .filter(p -> p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE)
            .limit(3)
            .collect(java.util.stream.Collectors.toList());

        if (targets.isEmpty()) return; 

        // 2. Freeze the rotation engine
        this.originalRotationSpeed = this.rotationSpeed;
        this.rotationSpeed = 0.0;
        this.shieldsEnabled = true;
        this.activeShields.clear();

        // 3. Shuffle a cloned list to randomize assignments (Fixed collection name compile error)
        java.util.List<DisplayCorpse> shuffled = new java.util.ArrayList<>(this.clusterCorpses);
        java.util.Collections.shuffle(shuffled);

        int totalCount = shuffled.size();
        int idleAmmoCount = totalCount / 4; // Reserves exactly 25% of bodies as idle ammo

        // Extract the remaining 75% pool for defensive deployment
        java.util.List<DisplayCorpse> shieldPool = shuffled.subList(idleAmmoCount, totalCount);
        int shieldCount = targets.size();

        if (shieldCount > 0 && !shieldPool.isEmpty()) {
            int corpsesPerShield = shieldPool.size() / shieldCount;

            // 4. Slice the shield pool into even segments per target player
            for (int i = 0; i < shieldCount; i++) {
                int startIdx = i * corpsesPerShield;
                int endIdx = (i == shieldCount - 1) ? shieldPool.size() : startIdx + corpsesPerShield;

                java.util.List<DisplayCorpse> subList = new java.util.ArrayList<>(shieldPool.subList(startIdx, endIdx));
                ShieldInstance shield = new ShieldInstance(targets.get(i), subList);

                activeShields.add(shield);
            }
        }

        updateShieldDirections();
    }

    private org.bukkit.util.Vector calculateShieldGridOffset(int subIndex, org.bukkit.util.Vector dir) {
        org.bukkit.util.Vector upAbsolute = new org.bukkit.util.Vector(0, 1, 0);
        org.bukkit.util.Vector right;

        if (Math.abs(dir.getY()) > 0.99) {
            right = new org.bukkit.util.Vector(1, 0, 0);
        } else {
            right = dir.clone().crossProduct(upAbsolute).normalize();
        }
        org.bukkit.util.Vector upLocal = right.clone().crossProduct(dir).normalize();

        int maxColumns = 3;
        double spacing = 0.75; 

        int col = subIndex % maxColumns;
        int row = subIndex / maxColumns;

        double xOffset = (col - (maxColumns - 1) / 2.0) * spacing;
        double yOffset = (row - 1.0) * spacing;

        return dir.clone().multiply(2.5)
                .add(right.multiply(xOffset))
                .add(upLocal.multiply(yOffset));
    }

    // UPDATED: Cleanses the child overrides completely on shutdown
    public void deactivateMultiShields() {
        if (!shieldsEnabled) return;
        
        this.rotationSpeed = this.originalRotationSpeed;
        this.shieldsEnabled = false;
        this.activeShields.clear();

        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.clearShieldOverride();
        }
    }

    public void updateShieldDirections() {
        if (!shieldsEnabled || centerCore == null || !centerCore.isValid()) return;

        for (ShieldInstance shield : activeShields) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(shield.getTargetPlayerUuid());
            if (player != null && player.isValid()) {
                org.bukkit.util.Vector dir = player.getLocation().toVector().subtract(centerCore.getLocation().toVector());
                if (dir.lengthSquared() > 0) {
                    shield.setDirection(dir.normalize());
                }
            }
        }
    }

    private ShieldInstance findShieldForCorpse(DisplayCorpse corpse) {
        for (ShieldInstance shield : activeShields) {
            if (shield.getAssignedCorpses().contains(corpse)) {
                return shield;
            }
        }
        return null;
    }

    public static class ShieldInstance {
        // FIXED: Swapped UUID for a concrete Player entity reference
        private final org.bukkit.entity.Player targetPlayer;
        private final java.util.List<DisplayCorpse> assignedCorpses;
        private org.bukkit.util.Vector targetDirection;

        public ShieldInstance(org.bukkit.entity.Player targetPlayer, java.util.List<DisplayCorpse> assignedCorpses) {
            this.targetPlayer = targetPlayer;
            this.assignedCorpses = assignedCorpses;
            this.targetDirection = new org.bukkit.util.Vector(1, 0, 0);
        }

        public org.bukkit.entity.Player getTargetPlayer() { return targetPlayer; }
        public java.util.List<DisplayCorpse> getAssignedCorpses() { return assignedCorpses; }
        public org.bukkit.util.Vector getDirection() { return targetDirection; }
        public void setDirection(org.bukkit.util.Vector direction) { this.targetDirection = direction; }
    }
}