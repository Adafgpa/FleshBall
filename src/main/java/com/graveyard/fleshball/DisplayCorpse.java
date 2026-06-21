package com.graveyard.fleshball;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import main.java.com.graveyard.fleshball.LimbNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DisplayCorpse {
    private final Entity centralCore;
    private final Vector nominalOffset;
    
    // Physics variables
    private Vector currentPos;
    private Vector velocity = new Vector(0, 0, 0);
    private final double omega0 = 6.0;
    private final double zeta = 0.5;
    private double timeElapsed = 0.0;
    
    private final double randomPhase;
    private ArmorStand torsoVehicle; 
    private final List<LimbNode> limbs = new ArrayList<>();

    // NEW: Store the base outward-facing rotation for this specific node
    private Quaternionf baseOutwardRotation;

    public DisplayCorpse(Entity centralCore, Vector nominalOffset) {
        this.centralCore = centralCore;
        this.nominalOffset = nominalOffset;
        this.currentPos = centralCore.getLocation().toVector().add(nominalOffset);
        this.randomPhase = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        // Calculate the normal look-at rotation facing directly outward from center
        calculateBaseNormalRotation();
    }

    private void calculateBaseNormalRotation() {
        // The nominalOffset vector IS our surface normal vector pointing outward
        Vector3f normalDirection = new Vector3f(
                (float) nominalOffset.getX(),
                (float) nominalOffset.getY(),
                (float) nominalOffset.getZ()
        ).normalize();

        // Define what "Up" means for the mannequin (global sky direction)
        Vector3f globalUp = new Vector3f(0f, 1f, 0f);

        // LookAlong explicitly forces the entity's FORWARD face (+Z) to align with the normal directional vector,
        // while keeping its spine comfortably oriented upright along the Y axis.
        this.baseOutwardRotation = new Quaternionf().lookAlong(normalDirection, globalUp);
    }

    public void spawn() {
        Location spawnLoc = centralCore.getLocation().add(nominalOffset);

        torsoVehicle = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setSmall(true); 
            stand.setGravity(false);
        });

        // Proportional Cobblestone Mannequin
        LimbNode torso = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0f, 0.7f, 0f), new Vector3f(0.5f, 0.8f, 0.25f));
        LimbNode head = new LimbNode(spawnLoc, new ItemStack(Material.ZOMBIE_HEAD), new Vector3f(0f, 1.3f, 0f), new Vector3f(0.6f, 0.6f, 0.6f));
        LimbNode leftArm = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(-0.4f, 0.7f, 0f), new Vector3f(0.15f, 0.7f, 0.15f));
        LimbNode rightArm = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.4f, 0.7f, 0f), new Vector3f(0.15f, 0.7f, 0.15f));
        LimbNode leftLeg = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(-0.15f, 0.1f, 0f), new Vector3f(0.15f, 0.7f, 0.15f));
        LimbNode rightLeg = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.15f, 0.1f, 0f), new Vector3f(0.15f, 0.7f, 0.15f));

        limbs.addAll(List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg));

        for (LimbNode limb : limbs) {
            torsoVehicle.addPassenger(limb.getEntity());
        }
    }

    public void tickPhysics(Vector coreVelocity) {
        if (torsoVehicle == null || !torsoVehicle.isValid()) return;

        Location coreLoc = centralCore.getLocation();
        Vector targetPos = coreLoc.toVector().add(nominalOffset);
        
        Vector z = currentPos.clone().subtract(targetPos);
        Vector relVelocity = this.velocity.clone().subtract(coreVelocity);
        
        Vector acceleration = relVelocity.multiply(-2.0 * zeta * omega0)
                                .add(z.multiply(-omega0 * omega0));
        
        double dt = 0.05; 
        this.velocity.add(acceleration.multiply(dt));
        this.currentPos.add(this.velocity.clone().multiply(dt));
        timeElapsed += dt;

        Location newLoc = new Location(coreLoc.getWorld(), currentPos.getX(), currentPos.getY(), currentPos.getZ());
        
        // Align the vehicle direction vector with the surface normal as well
        Vector normal = nominalOffset.clone().normalize();
        newLoc.setDirection(normal);
        torsoVehicle.teleport(newLoc);

        animateFlailing();
    }

    private void animateFlailing() {
        float speed = (float) this.velocity.length();
        
        // Base writhing frequency/amplitude calculations
        float wave = (float) Math.sin((timeElapsed * (8.0 + speed)) + randomPhase);
        float swingAngle = wave * (0.2f + (speed * 0.15f));

        // Create a local offset transformation (writhing/trembling)
        Quaternionf localWrithe = new Quaternionf()
                .rotateX(swingAngle)
                .rotateZ(swingAngle * 0.3f);

        // Combine operations: Apply the local flail rotation ON TOP OF the base outward-facing matrix orientation
        Quaternionf finalCalculatedRotation = new Quaternionf(baseOutwardRotation).mul(localWrithe);

        for (LimbNode limb : limbs) {
            limb.updateRotation(finalCalculatedRotation);
        }
    }

    public void despawn() {
        for (LimbNode limb : limbs) {
            limb.destroy();
        }
        if (torsoVehicle != null) {
            torsoVehicle.remove();
        }
    }
}