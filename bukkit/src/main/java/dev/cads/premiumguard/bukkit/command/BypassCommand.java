/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 CADS and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.cads.premiumguard.bukkit.command;

import dev.cads.premiumguard.bukkit.PremiumGuardBukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Bypass Command - จัดการรายชื่อที่ได้รับการยกเว้นจาก PremiumGuard
 * 
 * Subcommands:
 * - /bypass add <name> - เพิ่มชื่อลงใน bypass list (cracked สามารถใช้ชื่อนี้ได้)
 * - /bypass remove <name> - ลบชื่อออกจาก bypass list
 * - /bypass list - แสดงรายชื่อทั้งหมดใน bypass list
 */
public class BypassCommand implements CommandExecutor {

    private final PremiumGuardBukkit plugin;

    public BypassCommand(PremiumGuardBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "add":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /bypass add <username>");
                    return true;
                }
                addBypass(sender, args[1]);
                break;
                
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /bypass remove <username>");
                    return true;
                }
                removeBypass(sender, args[1]);
                break;
                
            case "list":
                listBypass(sender);
                break;
                
            default:
                sendUsage(sender);
                break;
        }
        
        return true;
    }

    private void addBypass(CommandSender sender, String username) {
        if (!isValidUsername(username)) {
            sender.sendMessage("§cInvalid username!");
            return;
        }
        
        plugin.getScheduler().runAsync(() -> {
            boolean added = plugin.getCore().getStorage().addBypass(username);
            plugin.getScheduler().getSyncExecutor().execute(() -> {
                if (added) {
                    String msg = plugin.getCore().getMessage("bypass-add");
                    if (msg != null) {
                        sender.sendMessage(msg.replace("%player", username));
                    } else {
                        sender.sendMessage("§aAdded §e" + username + " §ato the bypass list.");
                    }
                } else {
                    String msg = plugin.getCore().getMessage("bypass-already-exists");
                    if (msg != null) {
                        sender.sendMessage(msg.replace("%player", username));
                    } else {
                        sender.sendMessage("§e" + username + " §cis already in the bypass list!");
                    }
                }
            });
        });
    }

    private void removeBypass(CommandSender sender, String username) {
        plugin.getScheduler().runAsync(() -> {
            boolean removed = plugin.getCore().getStorage().removeBypass(username);
            plugin.getScheduler().getSyncExecutor().execute(() -> {
                if (removed) {
                    String msg = plugin.getCore().getMessage("bypass-remove");
                    if (msg != null) {
                        sender.sendMessage(msg.replace("%player", username));
                    } else {
                        sender.sendMessage("§aRemoved §e" + username + " §afrom the bypass list.");
                    }
                } else {
                    String msg = plugin.getCore().getMessage("bypass-not-found");
                    if (msg != null) {
                        sender.sendMessage(msg.replace("%player", username));
                    } else {
                        sender.sendMessage("§e" + username + " §cis not in the bypass list!");
                    }
                }
            });
        });
    }

    private void listBypass(CommandSender sender) {
        plugin.getScheduler().runAsync(() -> {
            List<String> bypassList = plugin.getCore().getStorage().getBypassList();
            plugin.getScheduler().getSyncExecutor().execute(() -> {
                if (bypassList.isEmpty()) {
                    String msg = plugin.getCore().getMessage("bypass-list-empty");
                    sender.sendMessage(msg != null ? msg : "§7No players in the bypass list.");
                } else {
                    String header = plugin.getCore().getMessage("bypass-list-header");
                    if (header != null) {
                        sender.sendMessage(header.replace("%count%", String.valueOf(bypassList.size())));
                    } else {
                        sender.sendMessage("§a=== Bypass List (" + bypassList.size() + " players) ===");
                    }
                    for (String name : bypassList) {
                        sender.sendMessage("§7- §e" + name);
                    }
                }
            });
        });
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§a=== PremiumGuard Bypass Command ===");
        sender.sendMessage("§e/bypass add <username> §7- Allow cracked player to use a premium name");
        sender.sendMessage("§e/bypass remove <username> §7- Remove player from bypass list");
        sender.sendMessage("§e/bypass list §7- Show all bypassed players");
    }

    private boolean isValidUsername(String username) {
        // Minecraft username validation: 3-16 characters, alphanumeric + underscore
        if (username == null || username.length() < 3 || username.length() > 16) {
            return false;
        }
        return username.matches("^[a-zA-Z0-9_]+$");
    }
}


