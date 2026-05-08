/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
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
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketEvent;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPreLoginEvent;
import com.github.games647.fastlogin.bukkit.listener.protocollib.packet.ClientPublicKey;
import com.github.games647.fastlogin.core.shared.PremiumGuardJoinManagement;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.security.PublicKey;
import java.util.Random;

/**
 * PremiumGuard NameCheckTask สำหรับ Bukkit
 * 
 * หน้าที่:
 * 1. ตรวจสอบว่า username เป็น premium หรือไม่
 * 2. ถ้าเป็น premium → ขอ encryption (ต้อง verify session)
 * 3. ถ้าไม่ใช่ premium → ให้ผ่านแบบ cracked
 * 4. Bedrock ผ่านได้เลย
 */
public class PremiumGuardNameCheckTask extends PremiumGuardJoinManagement<Player, CommandSender, ProtocolLibLoginSource>
    implements Runnable {

    private final FastLoginBukkit plugin;
    private final PacketEvent packetEvent;

    private final ClientPublicKey clientKey;
    private final PublicKey serverKey;

    private final Random random;

    private final Player player;
    private final String username;

    public PremiumGuardNameCheckTask(FastLoginBukkit plugin, Random random, Player player, PacketEvent packetEvent,
                         String username, ClientPublicKey clientKey, PublicKey serverKey) {
        super(plugin.getCore(), plugin.getCore().getAuthPluginHook(), plugin.getBedrockService());

        this.plugin = plugin;
        this.packetEvent = packetEvent;
        this.clientKey = clientKey;
        this.serverKey = serverKey;
        this.random = random;
        this.player = player;
        this.username = username;
    }

    @Override
    public void run() {
        try {
            super.onLogin(username, new ProtocolLibLoginSource(player, random, serverKey, clientKey));
        } finally {
            ProtocolLibrary.getProtocolManager().getAsynchronousManager().signalPacketTransmission(packetEvent);
        }
    }

    @Override
    public FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, ProtocolLibLoginSource source,
                                                             StoredProfile profile) {
        BukkitFastLoginPreLoginEvent event = new BukkitFastLoginPreLoginEvent(username, source, profile);
        plugin.getServer().getPluginManager().callEvent(event);
        return event;
    }

    /**
     * เรียกเมื่อตรวจพบว่าเป็น premium username
     * ส่ง encryption request ให้ client verify session
     */
    @Override
    public void requestPremiumLogin(ProtocolLibLoginSource source, StoredProfile profile,
                                    String username, boolean registered) {
        try {
            source.enableOnlinemode();
        } catch (Exception ex) {
            plugin.getLog().error("Cannot send encryption packet. Kicking player: {}", profile, ex);
            source.kick(plugin.getCore().getMessage("error-kick") != null ? 
                plugin.getCore().getMessage("error-kick") : "Login error. Please try again.");
            return;
        }

        String ip = player.getAddress().getAddress().getHostAddress();
        core.addLoginAttempt(ip, username);

        byte[] verify = source.getVerifyToken();
        ClientPublicKey clientKey = source.getClientKey();

        BukkitLoginSession playerSession = new BukkitLoginSession(username, verify, clientKey, registered, profile);
        plugin.putSession(player.getAddress(), playerSession);
        
        // ยกเลิก packet เดิม รอให้ VerifyResponseTask จัดการต่อ
        synchronized (packetEvent.getAsyncMarker().getProcessingLock()) {
            packetEvent.setCancelled(true);
        }
    }

    /**
     * เรียกเมื่อเป็น cracked (ไม่ใช่ premium)
     * ให้ผ่านได้เลยโดยไม่ต้อง verify session
     */
    @Override
    public void startCrackedSession(ProtocolLibLoginSource source, StoredProfile profile, String username) {
        plugin.putSession(source.getAddress(), new BukkitLoginSession(username, profile));
    }

    @Override
    public boolean isBypassed(String username) {
        // Bukkit: Use SQL storage for bypass check
        return core.getStorage() != null && core.getStorage().isBypassed(username);
    }
}
