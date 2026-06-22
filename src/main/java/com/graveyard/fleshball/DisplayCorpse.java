package com.graveyard.fleshball;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
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
    
    private ArmorStand anchorVehicle; 
    
    private LimbNode torso;
    private LimbNode head;
    private LimbNode leftArm;
    private LimbNode rightArm;
    private LimbNode leftLeg;
    private LimbNode rightLeg;
    private final List<LimbNode> limbs = new ArrayList<>();

    private final Vector outwardNormal;
    private Quaternionf baseOutwardRotation;

    // Joints relative to Torso center
    private final Vector3f jointLeftShoulder  = new Vector3f(-0.40f,  0.10f, 0.0f);
    private final Vector3f jointRightShoulder = new Vector3f( 0.40f,  0.10f, 0.0f);
    private final Vector3f jointLeftHip       = new Vector3f(-0.25f, -0.125f, 0.0f);
    private final Vector3f jointRightHip      = new Vector3f( 0.25f, -0.125f, 0.0f);
    private final Vector3f offsetHead         = new Vector3f( 0.00f,  0.375f, 0.0f);

    public DisplayCorpse(Entity centralCore, Vector nominalOffset, Vector outwardNormal) {
        this.centralCore = centralCore;
        this.nominalOffset = nominalOffset;
        this.outwardNormal = outwardNormal; 
        this.currentPos = centralCore.getLocation().toVector().add(nominalOffset);
        this.randomPhase = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        calculateBaseNormalRotation();
    }

    private void calculateBaseNormalRotation() {
        Vector3f zAxis = new Vector3f(
                (float) outwardNormal.getX(),
                (float) outwardNormal.getY(),
                (float) outwardNormal.getZ()
        ).normalize();

        Vector3f worldUp = new Vector3f(0f, 1f, 0f);
        if (Math.abs(zAxis.dot(worldUp)) > 0.99f) {
            worldUp.set(0f, 0f, 1f); 
        }

        Vector3f xAxis = worldUp.cross(zAxis).normalize();
        Vector3f yAxis = zAxis.cross(xAxis).normalize();

        // Public API alignment safe initialization
        Matrix3f rotMatrix = new Matrix3f();
        rotMatrix.setColumn(0, xAxis);
        rotMatrix.setColumn(1, yAxis);
        rotMatrix.setColumn(2, zAxis);
        
        this.baseOutwardRotation = new Quaternionf().setFromNormalized(rotMatrix);
    }

    public void spawn() {
        Location spawnLoc = centralCore.getLocation().add(nominalOffset);
        spawnLoc.setDirection(new Vector(0, 0, 1));

        anchorVehicle = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setSmall(true); 
            stand.setGravity(false);
            stand.setPersistent(true);
        });

        torso    = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.5f, 0.8f, 0.25f)); 
        head     = new LimbNode(spawnLoc, new ItemStack(Material.ZOMBIE_HEAD), new Vector3f(0.5f, 0.5f, 0.5f));
        leftArm  = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));
        rightArm = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));
        leftLeg  = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));
        rightLeg = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));

        limbs.addAll(List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg));
        
        // Execute frame immediately on spawn to register world vectors safely
        animateFlailing();
    }

    public void tickPhysics(Vector coreVelocity) {
        if (anchorVehicle == null || !anchorVehicle.isValid()) return;

        Location coreLoc = centralCore.getLocation();
        Vector targetPos = coreLoc.toVector().add(nominalOffset);
        
        Vector z = currentPos.clone().subtract(targetPos);
        Vector relVelocity = this.velocity.clone().subtract(coreVelocity);
        
        Vector acceleration = relVelocity.multiply(-2.0 * zeta * omega0).add(z.multiply(-omega0 * omega0));
        
        double dt = 0.05; 
        this.velocity.add(acceleration.multiply(dt));
        this.currentPos.add(this.velocity.clone().multiply(dt));
        timeElapsed += dt;

        Location newLoc = new Location(coreLoc.getWorld(), currentPos.getX(), currentPos.getY(), currentPos.getZ());
        newLoc.setDirection(new Vector(0, 0, 1));
        anchorVehicle.teleport(newLoc);

        animateFlailing();
    }
    
    private void animateFlailing() {
        if (anchorVehicle == null || !anchorVehicle.isValid()) return;

        float speed = (float) this.velocity.length();
        float wave = (float) Math.sin((timeElapsed * (8.0 + speed)) + randomPhase);
        float swingAngle = wave * (0.2f + (speed * 0.15f));

        Quaternionf localWrithe = new Quaternionf().rotateX(swingAngle).rotateZ(swingAngle * 0.3f);
        Quaternionf torsoRotation = new Quaternionf(baseOutwardRotation).mul(localWrithe);

        // --- DO NOT ADD ANCHORPOS TO THESE JOINT VECTORS ---
        // These now represent the LOCAL OFFSET from the anchor vehicle position
        Vector3f localLeftShoulder  = new Vector3f(jointLeftShoulder).rotate(torsoRotation);
        Vector3f localRightShoulder = new Vector3f(jointRightShoulder).rotate(torsoRotation);
        Vector3f localLeftHip       = new Vector3f(jointLeftHip).rotate(torsoRotation);
        Vector3f localRightHip      = new Vector3f(jointRightHip).rotate(torsoRotation);
        
        // Torso is at the exact center of the anchor, so its offset is 0
        torso.updateTransformation(new Vector3f(0f, 0f, 0f), torsoRotation);
        
        // Head offset relative to anchor
        head.updateTransformation(new Vector3f(offsetHead).rotate(torsoRotation), torsoRotation);

        // Dangle Setup
        Quaternionf limbRotation = new Quaternionf()
                .rotateX((float) Math.PI)   
                .rotateX(swingAngle * 1.2f) 
                .rotateZ(swingAngle * 0.4f);

        Vector3f limbDangleLocal = new Vector3f(0f, -0.3f, 0f).rotate(limbRotation);

        // Pass the local offsets directly! 
        // Minecraft will add these offsets to the teleported anchor location perfectly.
        leftArm.updateTransformation(new Vector3f(localLeftShoulder).add(limbDangleLocal), limbRotation);
        rightArm.updateTransformation(new Vector3f(localRightShoulder).add(limbDangleLocal), limbRotation);
        leftLeg.updateTransformation(new Vector3f(localLeftHip).add(limbDangleLocal), limbRotation);
        rightLeg.updateTransformation(new Vector3f(localRightHip).add(limbDangleLocal), limbRotation);
    }

    public void despawn() {
        for (LimbNode limb : limbs) { 
            limb.destroy(); 
        }
        if (anchorVehicle != null) { 
            anchorVehicle.remove(); 
        }
    }
}