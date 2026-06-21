package com.graveyard.fleshball;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LimbNode {
    private final Display displayEntity;
    private final Vector3f baseTranslation;

    // Constructor 1: For the Block Limbs (Cobblestone)
    public LimbNode(Location spawnLoc, Material blockMaterial, Vector3f offset, Vector3f scale) {
        // Block displays spawn from the corner. We subtract half the scale to center the pivot point!
        this.baseTranslation = new Vector3f(
                offset.x() - (scale.x() / 2f),
                offset.y() - (scale.y() / 2f),
                offset.z() - (scale.z() / 2f)
        );

        this.displayEntity = spawnLoc.getWorld().spawn(spawnLoc, BlockDisplay.class, entity -> {
            entity.setBlock(org.bukkit.Bukkit.createBlockData(blockMaterial));
            applyInitialTransform(entity, scale);
        });
    }

    // Constructor 2: For the Item Head (Player Head)
    public LimbNode(Location spawnLoc, ItemStack visualItem, Vector3f offset, Vector3f scale) {
        this.baseTranslation = offset; // Items generally pivot from their center naturally
        
        this.displayEntity = spawnLoc.getWorld().spawn(spawnLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(visualItem);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            applyInitialTransform(entity, scale);
        });
    }

    private void applyInitialTransform(Display entity, Vector3f scale) {
        entity.setTransformation(new Transformation(
                baseTranslation,
                new Quaternionf(), // Default rotation
                scale,
                new Quaternionf()
        ));
        entity.setInterpolationDuration(3); // Fast, smooth interpolation
        entity.setTeleportDuration(3);
    }

    public Display getEntity() {
        return this.displayEntity;
    }

    // Rotates the limb around its centered pivot point
    public void updateRotation(Quaternionf newRotation) {
        Transformation current = displayEntity.getTransformation();
        displayEntity.setInterpolationDelay(0);
        
        displayEntity.setTransformation(new Transformation(
                baseTranslation, // Keep it attached to the body
                newRotation,     // Apply flailing
                current.getScale(), 
                current.getRightRotation()
        ));
    }

    public void destroy() {
        if (displayEntity != null && displayEntity.isValid()) {
            displayEntity.remove();
        }
    }
}