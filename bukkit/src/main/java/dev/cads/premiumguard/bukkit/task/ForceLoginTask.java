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
package dev.cads.premiumguard.bukkit.task;

import dev.cads.premiumguard.bukkit.BukkitLoginSession;
import dev.cads.premiumguard.bukkit.PremiumGuardBukkit;
import dev.cads.premiumguard.bukkit.event.BukkitPremiumGuardAutoLoginEvent;
import dev.cads.premiumguard.core.PremiumStatus;
import dev.cads.premiumguard.core.message.SuccessMessage;
import dev.cads.premiumguard.core.shared.PremiumGuardCore;
import dev.cads.premiumguard.core.shared.ForceLoginManagement;
import dev.cads.premiumguard.core.shared.LoginSession;
import dev.cads.premiumguard.core.shared.event.PremiumGuardAutoLoginEvent;
import dev.cads.premiumguard.core.storage.StoredProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.concurrent.ExecutionException;

public class ForceLoginTask extends ForceLoginManagement<Player, CommandSender, BukkitLoginSession, PremiumGuardBukkit> {

    public ForceLoginTask(PremiumGuardCore<Player, CommandSender, PremiumGuardBukkit> core, Player player,
                          BukkitLoginSession session) {
        super(core, player, session);
    }

    @Override
    public void run() {
        // block this target player for BungeeCord ID brute force attacks
        PremiumGuardBukkit plugin = core.getPlugin();
        player.setMetadata(core.getPlugin().getName(), new FixedMetadataValue(plugin, true));

        if (session != null && !session.getUsername().equals(player.getName())) {
            String playerName = player.getName();
            plugin.getLog().warn("Player username {} is not matching session {}", playerName, session.getUsername());
            return;
        }

        super.run();

        PremiumStatus status = PremiumStatus.CRACKED;
        if (isOnlineMode()) {
            status = PremiumStatus.PREMIUM;
        }

        plugin.getPremiumPlayers().put(player.getUniqueId(), status);
    }

    @Override
    public PremiumGuardAutoLoginEvent callPremiumGuardAutoLoginEvent(LoginSession session, StoredProfile profile) {
        BukkitPremiumGuardAutoLoginEvent event = new BukkitPremiumGuardAutoLoginEvent(session, profile);
        core.getPlugin().getServer().getPluginManager().callEvent(event);
        return event;
    }

    @Override
    public void onForceActionSuccess(LoginSession session) {
        if (core.getPlugin().getBungeeManager().isEnabled()) {
            core.getPlugin().getBungeeManager().sendPluginMessage(player, new SuccessMessage());
        }
    }

    @Override
    public String getName(Player player) {
        return player.getName();
    }

    @Override
    public boolean isOnline(Player player) {
        try {
            //the player-list isn't thread-safe
            return Bukkit.getScheduler().callSyncMethod(core.getPlugin(), player::isOnline).get();
        } catch (InterruptedException | ExecutionException ex) {
            core.getPlugin().getLog().error("Failed to perform thread-safe online check for {}", player, ex);
            return false;
        }
    }

    @Override
    public boolean isOnlineMode() {
        return session.isVerifiedPremium();
    }
}


