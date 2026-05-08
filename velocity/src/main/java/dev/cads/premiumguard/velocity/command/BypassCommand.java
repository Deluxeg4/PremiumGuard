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
package dev.cads.premiumguard.velocity.command;

import dev.cads.premiumguard.velocity.PremiumGuardVelocity;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/**
 * Bypass Command for Velocity - จัดการรายชื่อที่ได้รับการยกเว้นจาก PremiumGuard
 * ใช้ YML file แทน SQL database
 */
public class BypassCommand implements SimpleCommand {

    private final PremiumGuardVelocity plugin;

    public BypassCommand(PremiumGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /bypass add <username>").color(NamedTextColor.RED));
                    return;
                }
                addBypass(sender, args[1]);
                break;

            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /bypass remove <username>").color(NamedTextColor.RED));
                    return;
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
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("premiumguard.admin");
    }

    private void addBypass(CommandSource sender, String username) {
        if (!isValidUsername(username)) {
            sender.sendMessage(Component.text("Invalid username!").color(NamedTextColor.RED));
            return;
        }

        plugin.getScheduler().runAsync(() -> {
            boolean added = plugin.getBypassStorage().addBypass(username);
            if (added) {
                sender.sendMessage(Component.text("Added ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(username).color(NamedTextColor.YELLOW))
                    .append(Component.text(" to the bypass list.").color(NamedTextColor.GREEN)));
            } else {
                sender.sendMessage(Component.text(username)
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text(" is already in the bypass list!").color(NamedTextColor.RED)));
            }
        });
    }

    private void removeBypass(CommandSource sender, String username) {
        plugin.getScheduler().runAsync(() -> {
            boolean removed = plugin.getBypassStorage().removeBypass(username);
            if (removed) {
                sender.sendMessage(Component.text("Removed ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(username).color(NamedTextColor.YELLOW))
                    .append(Component.text(" from the bypass list.").color(NamedTextColor.GREEN)));
            } else {
                sender.sendMessage(Component.text(username)
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text(" is not in the bypass list!").color(NamedTextColor.RED)));
            }
        });
    }

    private void listBypass(CommandSource sender) {
        plugin.getScheduler().runAsync(() -> {
            List<String> bypassList = plugin.getBypassStorage().getBypassList();
            if (bypassList.isEmpty()) {
                sender.sendMessage(Component.text("No players in the bypass list.").color(NamedTextColor.GRAY));
            } else {
                sender.sendMessage(Component.text("=== Bypass List (" + bypassList.size() + " players) ===")
                    .color(NamedTextColor.GREEN));
                for (String name : bypassList) {
                    sender.sendMessage(Component.text("- ").color(NamedTextColor.GRAY)
                        .append(Component.text(name).color(NamedTextColor.YELLOW)));
                }
            }
        });
    }

    private void sendUsage(CommandSource sender) {
        sender.sendMessage(Component.text("=== PremiumGuard Bypass Command ===").color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/bypass add <username> ").color(NamedTextColor.YELLOW)
            .append(Component.text("- Allow cracked player to use a premium name").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/bypass remove <username> ").color(NamedTextColor.YELLOW)
            .append(Component.text("- Remove player from bypass list").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/bypass list ").color(NamedTextColor.YELLOW)
            .append(Component.text("- Show all bypassed players").color(NamedTextColor.GRAY)));
    }

    private boolean isValidUsername(String username) {
        // Minecraft username validation: 3-16 characters, alphanumeric + underscore
        if (username == null || username.length() < 3 || username.length() > 16) {
            return false;
        }
        return username.matches("^[a-zA-Z0-9_]+$");
    }
}


