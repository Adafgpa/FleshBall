package com.graveyard.fleshball;

import com.comphenix.protocol.wrappers.WrappedDataValue;
import java.util.concurrent.ThreadLocalRandom;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CustomCorpse {
    private final int fakeEntityId;
    private final UUID uuid;
    private final Entity centralCore;
    private final Vector nominalOffset;
    private final EntityType type;
    
    private Vector currentPos;
    private Vector velocity = new Vector(0, 0, 0);
    
    private final double omega0 = 7.0;
    private final double zeta = 0.4;
    private double limbPhase = 0.0;

    private float yaw;
    private float pitch;

    private final ProtocolManager protocolManager;

    public CustomCorpse(Entity centralCore, Vector nominalOffset, EntityType type, float yaw, float pitch) {
        this.fakeEntityId = ThreadLocalRandom.current().nextInt(1000000, 2000000);
        this.uuid = UUID.randomUUID();
        this.centralCore = centralCore;
        this.nominalOffset = nominalOffset;
        this.type = type;
        this.yaw = yaw;
        this.pitch = pitch;
        this.currentPos = centralCore.getLocation().toVector().add(nominalOffset);
        
        // Grab the ProtocolLib manager
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    private void updatePose(Player player) {
        PacketContainer metaPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metaPacket.getIntegers().write(0, fakeEntityId);

        // 33% chance for FALL_FLYING, 66% chance for SWIMMING
        boolean useFallFlying = ThreadLocalRandom.current().nextDouble() < 0.33;
        EnumWrappers.EntityPose selectedPose = useFallFlying ? EnumWrappers.EntityPose.FALL_FLYING : EnumWrappers.EntityPose.SWIMMING;
        
        // Get the serializer for the Pose (Type 20 in the protocol)
        WrappedDataWatcher.Serializer poseSerializer = WrappedDataWatcher.Registry.get(EnumWrappers.getEntityPoseClass());
        
        // In 1.19.3+, we MUST use a List of WrappedDataValue instead of a DataWatcher
        List<WrappedDataValue> dataValues = new ArrayList<>();
        
        dataValues.add(new WrappedDataValue(
            6, // The Index (Slot 6 for Pose)
            poseSerializer, 
            selectedPose.toNms() // The actual value
        ));

        // Write the list to the modern DataValue modifier
        metaPacket.getDataValueCollectionModifier().write(0, dataValues);
        
        sendPacket(player, metaPacket);
    }

    public void tickPhysics(Player player, Vector coreVelocity) {
        Location coreLoc = centralCore.getLocation();
        Vector targetPos = coreLoc.toVector().add(nominalOffset);
        
        Vector z = currentPos.clone().subtract(targetPos);
        Vector relVelocity = this.velocity.clone().subtract(coreVelocity);
        
        Vector acceleration = relVelocity.multiply(-2.0 * zeta * omega0)
                                .add(z.multiply(-omega0 * omega0));
        
        double dt = 0.05; 
        this.velocity.add(acceleration.multiply(dt));
        this.currentPos.add(this.velocity.clone().multiply(dt));

        double physicalIntensity = this.velocity.length();
        limbPhase += 0.2 + (physicalIntensity * 0.6);
        
        // 2. Create the Teleport Packet using ProtocolLib
        PacketContainer teleportPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
        teleportPacket.getIntegers().write(0, fakeEntityId);
        teleportPacket.getDoubles().write(0, currentPos.getX());
        teleportPacket.getDoubles().write(1, currentPos.getY());
        teleportPacket.getDoubles().write(2, currentPos.getZ());
        
        teleportPacket.getBytes().write(0, (byte) (yaw * 256.0F / 360.0F));
        teleportPacket.getBytes().write(1, (byte) (pitch * 256.0F / 360.0F));
        teleportPacket.getBooleans().write(0, false); // On ground

        sendPacket(player, teleportPacket);
    }

    public void spawnForPlayer(Player player) {
        Location loc = centralCore.getLocation().add(nominalOffset);
        
        // 1. THE SPAWN PACKET
        PacketContainer spawnPacket = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, fakeEntityId);
        spawnPacket.getUUIDs().write(0, uuid);
        spawnPacket.getEntityTypeModifier().write(0, type); 
        spawnPacket.getDoubles().write(0, loc.getX());
        spawnPacket.getDoubles().write(1, loc.getY());
        spawnPacket.getDoubles().write(2, loc.getZ());
        // We send pitch/yaw here just to satisfy the packet structure, 
        // even though the client usually ignores it on frame 1.
        spawnPacket.getBytes().write(0, (byte) (pitch * 256.0F / 360.0F));
        spawnPacket.getBytes().write(1, (byte) (yaw * 256.0F / 360.0F));
        sendPacket(player, spawnPacket);
        
        // 2. THE POSE PACKET (Unlocks the torso)
        updatePose(player);

        // 3. THE TELEPORT PACKET (Forces the body to physically rotate)
        PacketContainer teleportPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
        teleportPacket.getIntegers().write(0, fakeEntityId);
        teleportPacket.getDoubles().write(0, loc.getX());
        teleportPacket.getDoubles().write(1, loc.getY());
        teleportPacket.getDoubles().write(2, loc.getZ());
        // This is where the magic happens:
        teleportPacket.getBytes().write(0, (byte) (yaw * 256.0F / 360.0F));
        teleportPacket.getBytes().write(1, (byte) (pitch * 256.0F / 360.0F));
        teleportPacket.getBooleans().write(0, false); // Not on ground
        sendPacket(player, teleportPacket);

        // 4. THE HEAD ROTATION PACKET (Aligns the head to the body)
        PacketContainer headPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        headPacket.getIntegers().write(0, fakeEntityId);
        headPacket.getBytes().write(0, (byte) (yaw * 256.0F / 360.0F));
        sendPacket(player, headPacket);
    }

    public void despawnForPlayer(Player player) {
        PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntLists().write(0, List.of(fakeEntityId));
        sendPacket(player, destroyPacket);
    }

    private void sendPacket(Player player, PacketContainer packet) {
        try {
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}