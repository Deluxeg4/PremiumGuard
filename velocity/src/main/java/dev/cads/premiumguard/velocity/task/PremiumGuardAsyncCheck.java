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

import dev.cads.premiumguard.core.shared.PremiumGuardJoinManagement;
import dev.cads.premiumguard.core.shared.event.PremiumGuardPreLoginEvent;
import dev.cads.premiumguard.core.storage.StoredProfile;
import dev.cads.premiumguard.velocity.PremiumGuardVelocity;
import dev.cads.premiumguard.velocity.VelocityLoginSession;
import dev.cads.premiumguard.velocity.VelocityLoginSource;
import dev.cads.premiumguard.velocity.event.VelocityPremiumGuardPreLoginEvent;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;

import java.util.concurrent.ExecutionException;

/**
 * PremiumGuard Async Check for Velocity
 * 
 * ตรวจสอบว่า username เป็น premium หรือไม่ และบังคับให้ verify session
 * ถ้าเป็น premium แต่ verify ไม่ผ่าน → kick
 */
public class PremiumGuardAsyncCheck extends PremiumGuardJoinManagement<Player, CommandSource, VelocityLoginSource>
        implements Runnable {

    private final PremiumGuardVelocity plugin;
    private final String username;
    private final PreLoginEvent preLoginEvent;
    private final InboundConnection connection;

    public PremiumGuardAsyncCheck(PremiumGuardVelocity plugin, InboundConnection connection, String username,
                             PreLoginEvent preLoginEvent) {
        super(plugin.getCore(), plugin.getCore().getAuthPluginHook(), plugin.getBedrockService());
        this.plugin = plugin;
        this.connection = connection;
        this.username = username;
        this.preLoginEvent = preLoginEvent;
    }

    @Override
    public void run() {
        plugin.getSession().remove(connection.getRemoteAddress());
        super.onLogin(username, new VelocityLoginSource(connection, preLoginEvent));
    }

    @Override
    public PremiumGuardPreLoginEvent callPremiumGuardPreLoginEvent(String username, VelocityLoginSource source,
                                                             StoredProfile profile) {
        VelocityPremiumGuardPreLoginEvent event = new VelocityPremiumGuardPreLoginEvent(username, source, profile);
        try {
            return plugin.getProxy().getEventManager().fire(event).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupt flag
            return event;
        } catch (ExecutionException e) {
            core.getPlugin().getLog().error("Error firing event", e);
            return event;
        }
    }

    @Override
    public void requestPremiumLogin(VelocityLoginSource source, StoredProfile profile,
                                    String username, boolean registered) {
        source.enableOnlinemode();
        VelocityLoginSession session = new VelocityLoginSession(username, registered, profile);
        plugin.getLog().info("Putting PremiumGuard session for: {}", source.getConnection());
        plugin.getSession().put(source.getConnection().getRemoteAddress(), session);

        String ip = source.getAddress().getAddress().getHostAddress();
        plugin.getCore().addLoginAttempt(ip, username);
    }

    @Override
    public void startCrackedSession(VelocityLoginSource source, StoredProfile profile, String username) {
        VelocityLoginSession session = new VelocityLoginSession(username, false, profile);
        plugin.getSession().put(source.getConnection().getRemoteAddress(), session);
    }

    @Override
    public boolean isBypassed(String username) {
        // Velocity: Use YML storage for bypass check (no SQL database)
        return plugin.getBypassStorage() != null && plugin.getBypassStorage().isBypassed(username);
    }
}


