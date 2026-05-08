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
package dev.cads.premiumguard.bungee.task;

import dev.cads.premiumguard.bungee.BungeeLoginSession;
import dev.cads.premiumguard.bungee.PremiumGuardBungee;
import dev.cads.premiumguard.bungee.event.BungeePremiumGuardAutoLoginEvent;
import dev.cads.premiumguard.core.message.ChannelMessage;
import dev.cads.premiumguard.core.message.LoginActionMessage;
import dev.cads.premiumguard.core.message.LoginActionMessage.Type;
import dev.cads.premiumguard.core.shared.PremiumGuardCore;
import dev.cads.premiumguard.core.shared.ForceLoginManagement;
import dev.cads.premiumguard.core.shared.LoginSession;
import dev.cads.premiumguard.core.shared.event.PremiumGuardAutoLoginEvent;
import dev.cads.premiumguard.core.storage.StoredProfile;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;

import java.util.UUID;

public class ForceLoginTask
        extends ForceLoginManagement<ProxiedPlayer, CommandSender, BungeeLoginSession, PremiumGuardBungee> {

    private final Server server;

    //treat player as if they had a premium account, even when they don't
    //use for Floodgate auto login/register
    private final boolean forcedOnlineMode;

    public ForceLoginTask(PremiumGuardCore<ProxiedPlayer, CommandSender, PremiumGuardBungee> core,
                          ProxiedPlayer player, Server server, BungeeLoginSession session, boolean forcedOnlineMode) {
        super(core, player, session);

        this.server = server;
        this.forcedOnlineMode = forcedOnlineMode;
    }

    public ForceLoginTask(PremiumGuardCore<ProxiedPlayer, CommandSender, PremiumGuardBungee> core, ProxiedPlayer player,
            Server server, BungeeLoginSession session) {
        this(core, player, server, session, false);
    }

    @Override
    public void run() {
        if (session == null) {
            return;
        }

        super.run();

        if (!isOnlineMode()) {
            session.setAlreadySaved(true);
        }
    }

    @Override
    public boolean forceLogin(ProxiedPlayer player) {
        if (session.isAlreadyLogged()) {
            return true;
        }

        session.setAlreadyLogged(true);
        return super.forceLogin(player);
    }

    @Override
    public PremiumGuardAutoLoginEvent callPremiumGuardAutoLoginEvent(LoginSession session, StoredProfile profile) {
        return core.getPlugin().getProxy().getPluginManager()
                .callEvent(new BungeePremiumGuardAutoLoginEvent(session, profile));
    }

    @Override
    public boolean forceRegister(ProxiedPlayer player) {
        return session.isAlreadyLogged() || super.forceRegister(player);
    }

    @Override
    public void onForceActionSuccess(LoginSession session) {
        //sub channel name
        Type type = Type.LOGIN;
        if (session.needsRegistration()) {
            type = Type.REGISTER;
        }

        UUID proxyId = UUID.fromString(ProxyServer.getInstance().getConfig().getUuid());
        ChannelMessage loginMessage = new LoginActionMessage(type, player.getName(), proxyId);

        core.getPlugin().sendPluginMessage(server, loginMessage);
    }

    @Override
    public String getName(ProxiedPlayer player) {
        return player.getName();
    }

    @Override
    public boolean isOnline(ProxiedPlayer player) {
        return player.isConnected();
    }

    @Override
    public boolean isOnlineMode() {
        return forcedOnlineMode || player.getPendingConnection().isOnlineMode();
    }
}


