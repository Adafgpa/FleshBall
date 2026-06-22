package com.graveyard.fleshball;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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

    // 1. Define VIRTUAL JOINT positions relative to the Torso center (0,0,0)
    // Based on Torso dimensions: Width=0.8, Height=0.25, Depth=0.5
    private final Vector3f jointLeftShoulder  = new Vector3f(-0.40f,  0.10f, 0.0f);
    private final Vector3f jointRightShoulder = new Vector3f( 0.40f,  0.10f, 0.0f);
    private final Vector3f jointLeftHip       = new Vector3f(-0.25f, -0.125f, 0.0f);
    private final Vector3f jointRightHip      = new Vector3f( 0.25f, -0.125f, 0.0f);
    
    // Head offset is rigid (Torso top Y=0.125 + half head height 0.25)
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
        Vector3f normal = new Vector3f(
                (float) outwardNormal.getX(),
                (float) outwardNormal.getY(),
                (float) outwardNormal.getZ()
        ).normalize();

        Vector3f lookDir = new Vector3f(normal).mul(-1.0f);
        Vector3f globalUp = new Vector3f(0f, 1f, 0f);

        if (Math.abs(lookDir.y()) > 0.99f) {
            globalUp = new Vector3f(1f, 0f, 0f);
        }

        this.baseOutwardRotation = new Quaternionf().lookAlong(lookDir, globalUp);
    }

    public void spawn() {
        Location spawnLoc = centralCore.getLocation().add(nominalOffset);
        spawnLoc.setDirection(new Vector(0, 0, 1)); // Clean neutral forward alignment

        anchorVehicle = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setSmall(true); 
            stand.setGravity(false);
        });

        // Instantiate using your direct target dimensions
        torso    = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.8f, 0.25f, 0.5f));
        head     = new LimbNode(spawnLoc, new ItemStack(Material.ZOMBIE_HEAD), new Vector3f(0.5f, 0.5f, 0.5f));
        leftArm  = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));
        rightArm = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));
        leftLeg  = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));
        rightLeg = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));

        limbs.addAll(List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg));
    }

    public void tickPhysics(Vector coreVelocity) {
        if (anchorVehicle == null || !anchorVehicle.isValid()) return;

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
        newLoc.setDirection(new Vector(0, 0, 1));
        anchorVehicle.teleport(newLoc);

        for (LimbNode limb : limbs) {
            limb.getEntity().teleport(newLoc);
        }

        animateFlailing();
    }

    private void animateFlailing() {
        float speed = (float) this.velocity.length();
        float wave = (float) Math.sin((timeElapsed * (8.0 + speed)) + randomPhase);
        float swingAngle = wave * (0.2f + (speed * 0.15f));

        // 1. Calculate Torso Rigid Frame
        Quaternionf localWrithe = new Quaternionf()
                .rotateX(swingAngle)
                .rotateZ(swingAngle * 0.3f);
        Quaternionf torsoRotation = new Quaternionf(baseOutwardRotation).mul(localWrithe);

        // Update Torso & Head (Bound rigidly to torso axes)
        torso.updateTransformation(new Vector3f(0f, 0f, 0f), torsoRotation);
        
        Vector3f posHead = new Vector3f(offsetHead).rotate(torsoRotation);
        head.updateTransformation(posHead, torsoRotation);

        // 2. Calculate Independent Gravity Dangle Rotation around Virtual Joint Axes
        // 180 degrees around X points the local up vector straight down to global -Y
        Quaternionf limbRotation = new Quaternionf()
                .rotateX((float) Math.PI)   // Absolute gravity alignment
                .rotateX(swingAngle * 1.2f) // Momentum drag flail on Joint X
                .rotateZ(swingAngle * 0.4f); // Momentum drag flail on Joint Z

        // 3. Compute the offset from the joint to the middle of the limb block
        // Since limb height is 0.6f, the center sits 0.3f below the socket pivot
        Vector3f localCenterFromJoint = new Vector3f(0f, -0.3f, 0f);
        Vector3f worldCenterFromJoint = new Vector3f(localCenterFromJoint).rotate(limbRotation);

        // 4. Position and transform limbs through their respective sockets
        
        // Left Arm
        Vector3f worldLeftShoulder = new Vector3f(jointLeftShoulder).rotate(torsoRotation);
        Vector3f posLeftArm = new Vector3f(worldLeftShoulder).add(worldCenterFromJoint);
        leftArm.updateTransformation(posLeftArm, limbRotation);

        // Right Arm
        Vector3f worldRightShoulder = new Vector3f(jointRightShoulder).rotate(torsoRotation);
        Vector3f posRightArm = new Vector3f(worldRightShoulder).add(worldCenterFromJoint);
        rightArm.updateTransformation(posRightArm, limbRotation);

        // Left Leg
        Vector3f worldLeftHip = new Vector3f(jointLeftHip).rotate(torsoRotation);
        Vector3f posLeftLeg = new Vector3f(worldLeftHip).add(worldCenterFromJoint);
        leftLeg.updateTransformation(posLeftLeg, limbRotation);

        // Right Leg
        Vector3f worldRightHip = new Vector3f(jointRightHip).rotate(torsoRotation);
        Vector3f posRightLeg = new Vector3f(worldRightHip).add(worldCenterFromJoint);
        rightLeg.updateTransformation(posRightLeg, limbRotation);
    }

    public void despawn() {
        for (LimbNode limb : limbs) { limb.destroy(); }
        if (anchorVehicle != null) { anchorVehicle.remove(); }
    }
}