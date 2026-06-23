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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DisplayCorpse {
    // =========================================================================
    // CUSTOMIZABLE MATERIAL POOLS 
    // Add, remove, or change any Bukkit Materials here to test different looks!
    // =========================================================================
    public static final Material[] TORSO_POOL = {
        Material.COBBLESTONE,
        Material.NETHERRACK,
        Material.NETHER_WART_BLOCK,
        Material.BONE_BLOCK,
        Material.MUD,
        Material.MAGMA_BLOCK
    };

    public static final Material[] HEAD_POOL = {
        Material.ZOMBIE_HEAD,        // Will spawn as an ItemDisplay skull
        Material.SKELETON_SKULL,     // Will spawn as an ItemDisplay skull
        Material.NETHERRACK,         // Will spawn as a BlockDisplay cube
        Material.BROWN_MUSHROOM_BLOCK,
        Material.BONE_BLOCK
    };

    public static final Material[] LEFT_ARM_POOL = {
        Material.COBBLESTONE,
        Material.NETHERRACK,
        Material.CRIMSON_STEM,
        Material.SOUL_SOIL,
        Material.MUD
    };

    public static final Material[] RIGHT_ARM_POOL = {
        Material.COBBLESTONE,
        Material.NETHERRACK,
        Material.CRIMSON_STEM,
        Material.SOUL_SOIL,
        Material.MUD
    };

    public static final Material[] LEFT_LEG_POOL = {
        Material.COBBLESTONE,
        Material.NETHERRACK,
        Material.BASALT,
        Material.PACKED_MUD,
        Material.SOUL_SAND
    };

    public static final Material[] RIGHT_LEG_POOL = {
        Material.COBBLESTONE,
        Material.NETHERRACK,
        Material.BASALT,
        Material.PACKED_MUD,
        Material.SOUL_SAND
    };
    // =========================================================================

    private final Entity centralCore;
    private final Vector nominalOffset;
    
    // Physics variables
    private Vector currentPos;
    private Vector velocity = new Vector(0, 0, 0);
    private final double omega0 = 6.0;
    private final double zeta = 0.5;
    private double timeElapsed = 0.0;
    private final double randomPhase;
    private final float randomRoll;
    
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
    private final Vector3f jointLeftShoulder  = new Vector3f(-0.35f,  0.30f, 0.0f);
    private final Vector3f jointRightShoulder = new Vector3f( 0.35f,  0.30f, 0.0f);
    private final Vector3f jointLeftHip       = new Vector3f(-0.18f, -0.4f, 0.0f);
    private final Vector3f jointRightHip      = new Vector3f( 0.18f, -0.4f, 0.0f);
    private final Vector3f offsetHead         = new Vector3f( 0.00f,  0.65f, 0.0f);

    public DisplayCorpse(Entity centralCore, Vector nominalOffset, Vector outwardNormal) {
        this.centralCore = centralCore;
        this.nominalOffset = nominalOffset;
        this.outwardNormal = outwardNormal; 
        this.currentPos = centralCore.getLocation().toVector().add(nominalOffset);
        this.randomPhase = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        // Roll a completely random 360-degree angle (in radians) for this specific corpse
        this.randomRoll = (float) ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

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

        Vector3f xAxis = new Vector3f(worldUp).cross(zAxis).normalize();
        Vector3f yAxis = new Vector3f(zAxis).cross(xAxis).normalize();

        Matrix3f rotMatrix = new Matrix3f();
        rotMatrix.setColumn(0, xAxis);
        rotMatrix.setColumn(1, yAxis);
        rotMatrix.setColumn(2, zAxis);
        
        this.baseOutwardRotation = new Quaternionf().setFromNormalized(rotMatrix).rotateZ(randomRoll);
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

        // 1. Pick a random material variant for each limb from our pools
        Material chosenTorso    = getRandomMaterial(TORSO_POOL);
        Material chosenHead     = getRandomMaterial(HEAD_POOL);
        Material chosenLeftArm  = getRandomMaterial(LEFT_ARM_POOL);
        Material chosenRightArm = getRandomMaterial(RIGHT_ARM_POOL);
        Material chosenLeftLeg  = getRandomMaterial(LEFT_LEG_POOL);
        Material chosenRightLeg = getRandomMaterial(RIGHT_LEG_POOL);

        // 2. Instantiate the limbs with their randomized textures
        torso    = new LimbNode(spawnLoc, chosenTorso, new Vector3f(0.5f, 0.75f, 0.25f)); 
        leftArm  = new LimbNode(spawnLoc, chosenLeftArm, new Vector3f(0.25f, 0.75f, 0.25f));
        rightArm = new LimbNode(spawnLoc, chosenRightArm, new Vector3f(0.25f, 0.75f, 0.25f));
        leftLeg  = new LimbNode(spawnLoc, chosenLeftLeg, new Vector3f(0.25f, 0.75f, 0.25f));
        rightLeg = new LimbNode(spawnLoc, chosenRightLeg, new Vector3f(0.25f, 0.75f, 0.25f));

        // 3. Dynamic layout allocation for the head
        if (isSkullMaterial(chosenHead)) {
            // Uses Constructor 2 (ItemDisplay) to render traditional skulls beautifully
            head = new LimbNode(spawnLoc, new ItemStack(chosenHead), new Vector3f(0.5f, 0.5f, 0.5f));
        } else {
            // Uses Constructor 1 (BlockDisplay) if you specify a full block type like Netherrack!
            head = new LimbNode(spawnLoc, chosenHead, new Vector3f(0.5f, 0.5f, 0.5f));
        }

        limbs.addAll(List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg));
        
        // Execute frame immediately on spawn to register world vectors safely
        animateFlailing();
    }

    /**
     * Helper to grab a random entry from a material array pool.
     */
    private Material getRandomMaterial(Material[] pool) {
        if (pool == null || pool.length == 0) return Material.COBBLESTONE;
        int index = ThreadLocalRandom.current().nextInt(pool.length);
        return pool[index];
    }

    /**
     * Detects if a selected head material belongs to a standard item skull interface.
     */
    private boolean isSkullMaterial(Material mat) {
        String name = mat.name();
        return name.endsWith("_HEAD") || name.endsWith("_SKULL");
    }

    public void tickPhysics(Vector coreVelocity) {
        if (centralCore == null || !centralCore.isValid() || anchorVehicle == null || !anchorVehicle.isValid()) {
            despawn();
            return;
        }

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
        if (anchorVehicle == null || !anchorVehicle.isValid()) return;

        float speed = (float) this.velocity.length();
        // We retain a high frequency frequency shift so they shake quickly when moving...
        float wave = (float) Math.sin((timeElapsed * (8.0 + speed * 0.5)) + randomPhase);
    
        // FIX: Dynamic attenuation curve. Instead of growing with speed, 
        // the amplitude shrinks as speed increases, stabilizing the limbs during high-speed runs.
        float swingAngle = wave * (0.2f / (1.0f + speed * 0.6f));

        Quaternionf localWrithe = new Quaternionf().rotateX(swingAngle).rotateZ(swingAngle * 0.3f);
        Quaternionf torsoRotation = new Quaternionf(baseOutwardRotation).mul(localWrithe);

        Vector3f localLeftShoulder  = new Vector3f(jointLeftShoulder).rotate(torsoRotation);
        Vector3f localRightShoulder = new Vector3f(jointRightShoulder).rotate(torsoRotation);
        Vector3f localLeftHip       = new Vector3f(jointLeftHip).rotate(torsoRotation);
        Vector3f localRightHip      = new Vector3f(jointRightHip).rotate(torsoRotation);
        
        torso.updateTransformation(new Vector3f(0f, 0f, 0f), torsoRotation);
        Quaternionf headRotation = new Quaternionf(torsoRotation).rotateY((float) Math.PI);
        head.updateTransformation(new Vector3f(offsetHead).rotate(torsoRotation), headRotation);

        Vector3f forwardDir = new Vector3f(0, 0, 1).rotate(baseOutwardRotation);
        float yawAngle = (float) Math.atan2(forwardDir.x, forwardDir.z);
        
        Quaternionf limbRotation = new Quaternionf()
                .rotateY(yawAngle) 
                .rotateX(swingAngle * 1.2f) 
                .rotateZ(swingAngle * 0.4f);

        Vector3f limbCenterOffset = new Vector3f(0f, -0.3f, 0f).rotate(limbRotation);

        leftArm.updateTransformation(new Vector3f(localLeftShoulder).add(limbCenterOffset), limbRotation);
        rightArm.updateTransformation(new Vector3f(localRightShoulder).add(limbCenterOffset), limbRotation);
        leftLeg.updateTransformation(new Vector3f(localLeftHip).add(limbCenterOffset), limbRotation);
        rightLeg.updateTransformation(new Vector3f(localRightHip).add(limbCenterOffset), limbRotation);
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