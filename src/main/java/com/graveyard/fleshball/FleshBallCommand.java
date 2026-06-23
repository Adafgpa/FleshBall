package com.graveyard.fleshball;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FleshBallCommand implements CommandExecutor {
    private final FleshBallPlugin plugin;

    public FleshBallCommand(FleshBallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 1. SECURITY CHECK: Only enforce permissions if the sender is a real player.
        // Command blocks, plugins, and console automatically bypass this check.
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.isOp() && !player.hasPermission("fleshball.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to execute this command!");
                return true;
            }
        }

        if (args.length < 1) {
            sendUsageMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "spawn":
                return handleSpawn(sender, args);
            case "spin":
                return handleSpin(sender, args);
            case "damage":
                return handleDamageCoefficient(sender, args);
            default:
                sendUsageMessage(sender);
                return true;
        }
    }

    private boolean handleSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /fleshball spawn <me|PlayerName|EntityType> [density] [radius]");
            return true;
        }

        Entity coreEntity = null;
        String targetArg = args[1];
        Location originLocation = getSenderLocation(sender);

        // 1. Check if targeting 'me' (Only valid if run by a player)
        if (targetArg.equalsIgnoreCase("me")) {
            if (sender instanceof Player) {
                coreEntity = (Player) sender;
            } else {
                sender.sendMessage(ChatColor.RED + "The 'me' argument can only be used by players in-game.");
                return true;
            }
        } else {
            // 2. Check if targeting an active online player name (Great for Console/Command Blocks)
            Player targetPlayer = org.bukkit.Bukkit.getPlayer(targetArg);
            if (targetPlayer != null) {
                coreEntity = targetPlayer;
            } else {
                // 3. Otherwise, treat it as a standalone EntityType to spawn fresh
                try {
                    EntityType type = EntityType.valueOf(targetArg.toUpperCase());
                    if (originLocation == null) {
                        sender.sendMessage(ChatColor.RED + "The console cannot spawn an EntityType directly because it lacks a world location. Please specify an online player's name to link the cluster instead.");
                        return true;
                    }
                    
                    coreEntity = originLocation.getWorld().spawnEntity(originLocation, type);
                    if (coreEntity instanceof LivingEntity) {
                        LivingEntity living = (LivingEntity) coreEntity;
                        living.setAI(false);
                        living.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid target parameter! Provide 'me', an online player's name, or a valid EntityType.");
                    return true;
                }
            }
        }

        int density = 14;
        if (args.length >= 3) {
            try { density = Integer.parseInt(args[2]); } catch (NumberFormatException e) { /* fallback */ }
        }

        double radius = 4.0;
        if (args.length >= 4) {
            try { radius = Double.parseDouble(args[3]); } catch (NumberFormatException e) { /* fallback */ }
        }

        FleshBallCluster cluster = new FleshBallCluster(coreEntity, density, radius);
        cluster.spawn();
        plugin.registerCluster(coreEntity.getUniqueId(), cluster);

        sender.sendMessage(ChatColor.GREEN + "Swarm cleanly initialized onto core: " + coreEntity.getType().name());
        return true;
    }

    private boolean handleSpin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /fleshball spin <speed> [near_player]");
            return true;
        }

        try {
            double speed = Double.parseDouble(args[1]);
            FleshBallCluster cluster = null;

            // Optional argument lets you point directly to a player's cluster from the console
            if (args.length >= 3) {
                Player targetedPlayer = org.bukkit.Bukkit.getPlayer(args[2]);
                if (targetedPlayer != null) {
                    cluster = findNearestCluster(targetedPlayer.getLocation());
                } else {
                    sender.sendMessage(ChatColor.RED + "Target player '" + args[2] + "' not found online.");
                    return true;
                }
            } else {
                Location loc = getSenderLocation(sender);
                if (loc == null) {
                    sender.sendMessage(ChatColor.RED + "Console must explicitly provide a nearby player name parameter: /fleshball spin <speed> <player>");
                    return true;
                }
                cluster = findNearestCluster(loc);
            }

            if (cluster == null) {
                sender.sendMessage(ChatColor.RED + "No active fleshball clusters were found matching that location context.");
                return true;
            }

            cluster.setRotationSpeed(speed);
            sender.sendMessage(ChatColor.GREEN + "Updated rotation speed to: " + ChatColor.AQUA + speed);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid speed value. Must be a valid decimal number.");
        }
        return true;
    }

    private boolean handleDamageCoefficient(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /fleshball damage <coefficient> [near_player]");
            return true;
        }

        try {
            double coefficient = Double.parseDouble(args[1]);
            FleshBallCluster cluster = null;

            if (args.length >= 3) {
                Player targetedPlayer = org.bukkit.Bukkit.getPlayer(args[2]);
                if (targetedPlayer != null) {
                    cluster = findNearestCluster(targetedPlayer.getLocation());
                } else {
                    sender.sendMessage(ChatColor.RED + "Target player '" + args[2] + "' not found online.");
                    return true;
                }
            } else {
                Location loc = getSenderLocation(sender);
                if (loc == null) {
                    sender.sendMessage(ChatColor.RED + "Console must explicitly provide a nearby player name parameter: /fleshball damage <coefficient> <player>");
                    return true;
                }
                cluster = findNearestCluster(loc);
            }

            if (cluster == null) {
                sender.sendMessage(ChatColor.RED + "No active fleshball clusters were found matching that location context.");
                return true;
            }

            cluster.setDamageTransferCoefficient(coefficient);
            int percentage = (int) (coefficient * 100);
            sender.sendMessage(ChatColor.GREEN + "Shield damage transfer modified! Core now receives " 
                    + ChatColor.GOLD + percentage + "%" + ChatColor.GREEN + " of raw proxy damage.");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coefficient. Please provide a valid decimal value.");
        }
        return true;
    }

    /**
     * Resolves spatial context positions depending on what source fired the execution thread.
     */
    private Location getSenderLocation(CommandSender sender) {
        if (sender instanceof Player) {
            return ((Player) sender).getLocation();
        } else if (sender instanceof BlockCommandSender) {
            return ((BlockCommandSender) sender).getBlock().getLocation();
        } else if (sender instanceof org.bukkit.entity.Entity) {
            return ((org.bukkit.entity.Entity) sender).getLocation();
        }
        return null; // Signals ConsoleCommandSender or Remote Plugins
    }

    private FleshBallCluster findNearestCluster(Location loc) {
        if (loc == null) return null;
        FleshBallCluster closestCluster = null;
        double closestDistance = 60.0;

        for (FleshBallCluster cluster : plugin.getActiveClusters().values()) {
            Entity core = cluster.getCenterCore();
            if (core != null && core.isValid() && core.getWorld().equals(loc.getWorld())) {
                double distance = core.getLocation().distance(loc);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestCluster = cluster;
                }
            }
        }
        return closestCluster;
    }

    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== FleshBall External Control Usage ===");
        sender.sendMessage(ChatColor.YELLOW + "/fleshball spawn <me|PlayerName|EntityType> [density] [radius]");
        sender.sendMessage(ChatColor.YELLOW + "/fleshball spin <speed> [near_player]");
        sender.sendMessage(ChatColor.YELLOW + "/fleshball damage <coefficient> [near_player]");
    }
}