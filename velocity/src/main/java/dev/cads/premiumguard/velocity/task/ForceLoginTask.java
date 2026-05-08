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
package dev.cads.premiumguard.velocity.task;

import dev.cads.premiumguard.core.message.ChannelMessage;
import dev.cads.premiumguard.core.message.LoginActionMessage;
import dev.cads.premiumguard.core.message.LoginActionMessage.Type;
import dev.cads.premiumguard.core.shared.PremiumGuardCore;
import dev.cads.premiumguard.core.shared.ForceLoginManagement;
import dev.cads.premiumguard.core.shared.LoginSession;
import dev.cads.premiumguard.core.shared.event.PremiumGuardAutoLoginEvent;
import dev.cads.premiumguard.core.storage.StoredProfile;
import dev.cads.premiumguard.velocity.PremiumGuardVelocity;
import dev.cads.premiumguard.velocity.VelocityLoginSession;
import dev.cads.premiumguard.velocity.event.VelocityPremiumGuardAutoLoginEvent;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ForceLoginTask
        extends ForceLoginManagement<Player, CommandSource, VelocityLoginSession, PremiumGuardVelocity> {

    private final RegisteredServer server;

    //treat player as if they had a premium account, even when they don't use to do auto login for Floodgate
    private final boolean forcedOnlineMode;

    public ForceLoginTask(PremiumGuardCore<Player, CommandSource, PremiumGuardVelocity> core,
                          Player player, RegisteredServer server, VelocityLoginSession session,
                          boolean forcedOnlineMode) {
        super(core, player, session);

        this.server = server;
        this.forcedOnlineMode = forcedOnlineMode;
    }

    public ForceLoginTask(PremiumGuardCore<Player, CommandSource, PremiumGuardVelocity> core, Player player,
                          RegisteredServer server, VelocityLoginSession session) {
        this(core, player, server, session, false);
    }

    @Override
    public void run() {
        if (session == null) {
            core.getPlugin().getLog().info("No active login session on force handling for {}", player);
            return;
        }

        super.run();
        if (!isOnlineMode()) {
            session.setAlreadySaved(true);
        }
    }

    @Override
    public boolean forceLogin(Player player) {
        if (session.isAlreadyLogged()) {
            core.getPlugin().getLog().info("Ignoring second force login attempt for {}", player);
            return true;
        }

        session.setAlreadyLogged(true);
        return super.forceLogin(player);
    }

    @Override
    public PremiumGuardAutoLoginEvent callPremiumGuardAutoLoginEvent(LoginSession session, StoredProfile profile) {
        VelocityPremiumGuardAutoLoginEvent event = new VelocityPremiumGuardAutoLoginEvent(session, profile);
        try {
             return core.getPlugin().getProxy().getEventManager().fire(event).get();
        } catch (InterruptedException interruptedEx) {
            Thread.currentThread().interrupt(); // Set the interrupt flag again
            return event;
        } catch (ExecutionException executionEx) {
            core.getPlugin().getLog().error("Error firing event", executionEx);
            return event;
        }
    }

    @Override
    public boolean forceRegister(Player player) {
        return session.isAlreadyLogged() || super.forceRegister(player);
    }

    @Override
    public void onForceActionSuccess(LoginSession session) {
        //sub channel name
        Type type = Type.LOGIN;
        if (session.needsRegistration()) {
            type = Type.REGISTER;
        }

        core.getPlugin().getLog().info("Sending force {} for {} towards server {}", type, player.getUsername(), server);

        UUID proxyId = core.getPlugin().getProxyId();
        ChannelMessage loginMessage = new LoginActionMessage(type, player.getUsername(), proxyId);
        core.getPlugin().sendPluginMessage(server, loginMessage);
    }

    @Override
    public String getName(Player player) {
        return player.getUsername();
    }

    @Override
    public boolean isOnline(Player player) {
        return player.isActive();
    }

    @Override
    public boolean isOnlineMode() {
        return forcedOnlineMode || player.isOnlineMode();
    }
}


