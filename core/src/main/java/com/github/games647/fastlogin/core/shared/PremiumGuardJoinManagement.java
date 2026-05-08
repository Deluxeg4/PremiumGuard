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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.craftapi.resolver.RateLimitException;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * PremiumGuard - Join Management
 * 
 * หลักการทำงาน:
 * 1. ตรวจสอบทุก username ว่าเป็น premium หรือไม่ผ่าน Mojang API
 * 2. ถ้าเป็น premium username แต่ไม่มี valid session → kick (ป้องกันแอบอ้าง)
 * 3. ถ้าเป็น premium และมี valid session → อนุญาตให้เข้า
 * 4. ถ้าไม่ใช่ premium → อนุญาตให้เข้า (cracked)
 * 5. Bedrock/Floodgate ผ่านได้เลยโดยไม่ตรวจสอบ
 * 6. ตรวจสอบ name change: ถ้า UUID เดิมเปลี่ยนชื่อใหม่ → อัพเดต database
 */
public abstract class PremiumGuardJoinManagement<P extends C, C, S extends LoginSource> {

    protected final FastLoginCore<P, C, ?> core;
    protected final AuthPlugin<P> authHook;
    private final BedrockService<?> bedrockService;

    public PremiumGuardJoinManagement(FastLoginCore<P, C, ?> core, AuthPlugin<P> authHook, BedrockService<?> bedrockService) {
        this.core = core;
        this.authHook = null; // PremiumGuard: No auth plugin integration
        this.bedrockService = bedrockService;
    }

    public void onLogin(String username, S source) {
        // 1. ตรวจสอบ Bedrock/Floodgate ก่อน - ให้ผ่านได้เลย
        if (bedrockService != null && bedrockService.isBedrockConnection(username)) {
            core.getPlugin().getLog().info("Bedrock player detected, allowing: {}", username);
            performBedrockChecks(username, source);
            return;
        }

        String ip = source.getAddress().getAddress().getHostAddress();

        // 2. ตรวจสอบ Bypass List - ถ้าชื่อนี้อยู่ใน bypass list ให้ผ่านได้เลยแบบ cracked
        // PremiumGuard: Use platform-specific bypass check (SQL for Bukkit, YML for Velocity)
        if (isBypassed(username)) {
            core.getPlugin().getLog().info("Player {} is in bypass list, allowing cracked access", username);
            // PremiumGuard: Skip database lookup if no storage (Velocity mode)
            StoredProfile bypassProfile = core.getStorage() != null ? core.getStorage().loadProfile(username) : null;
            if (bypassProfile == null) {
                bypassProfile = new StoredProfile(null, username, false,
                    com.github.games647.fastlogin.core.shared.FloodgateState.FALSE, ip);
            }
            startCrackedSession(source, bypassProfile, username);
            return;
        }
        
        // 3. ดึงข้อมูลจาก Mojang API เพื่อตรวจสอบว่า username นี้เป็น premium หรือไม่
        Optional<Profile> mojangProfile = Optional.empty();
        try {
            MojangResolver resolver = core.getResolver();
            mojangProfile = resolver.findProfile(username);
        } catch (RateLimitException rateLimitEx) {
            core.getPlugin().getLog().error("Mojang's rate limit reached for {}. Cannot verify premium status.", username);
            // ถ้าเคยมีข้อมูลใน database ว่าเป็น premium → ให้ผ่านไปตรวจสอบ session
            // ถ้าไม่มี → ให้ผ่านไป (assumption: ไม่ใช่ premium)
        } catch (Exception ex) {
            core.getPlugin().getLog().error("Failed to check premium status of {}", username, ex);
        }

        // 4. โหลด profile จาก database (ถ้ามี) - PremiumGuard: Skip if no database
        StoredProfile storedProfile = core.getStorage() != null ? core.getStorage().loadProfile(username) : null;

        // 5. ถ้า Mojang บอกว่าเป็น premium username
        if (mojangProfile.isPresent()) {
            Profile premiumProfile = mojangProfile.get();
            UUID premiumUUID = premiumProfile.getId();
            String correctUsername = premiumProfile.getName();
            
            core.getPlugin().getLog().info("Premium username detected: {} (UUID: {})", correctUsername, premiumUUID);

            // ตรวจสอบ name change - ถ้า UUID นี้มีใน database แต่ชื่อต่างกัน
            // PremiumGuard: Skip if no database
            if (core.getStorage() != null) {
                StoredProfile uuidProfile = core.getStorage().loadProfile(premiumUUID);
                if (uuidProfile != null && !uuidProfile.getName().equalsIgnoreCase(correctUsername)) {
                    core.getPlugin().getLog().info("Name change detected: {} -> {}", uuidProfile.getName(), correctUsername);
                    // อัพเดตชื่อใหม่ใน database
                    uuidProfile.setPlayerName(correctUsername);
                    uuidProfile.setLastIp(ip);
                    uuidProfile.setOnlinemodePreferred(true);
                    core.getStorage().save(uuidProfile);

                    // ใช้ profile ที่อัพเดตแล้ว
                    storedProfile = uuidProfile;
                }
            }

            // ถ้าไม่มี profile ใน database ให้สร้างใหม่
            if (storedProfile == null) {
                storedProfile = new StoredProfile(premiumUUID, correctUsername, true,
                    com.github.games647.fastlogin.core.shared.FloodgateState.FALSE, ip);
                // PremiumGuard: Skip save if no database
                if (core.getStorage() != null) {
                    core.getStorage().save(storedProfile);
                }
                core.getPlugin().getLog().info("Created new premium profile for: {}", correctUsername);
            }

            // ตรวจสอบว่า username ที่ส่งมาตรงกับ Mojang หรือไม่ (case sensitivity)
            if (!username.equals(correctUsername)) {
                core.getPlugin().getLog().warn("Username case mismatch: sent='{}' mojang='{}'", username, correctUsername);
            }

            // เรียก event และขอ premium login (ต้องผ่าน session verification)
            callFastLoginPreLoginEvent(correctUsername, source, storedProfile);
            requestPremiumLogin(source, storedProfile, correctUsername, storedProfile.isExistingPlayer());
            return;
        }

        // 6. ถ้าไม่ใช่ premium username (cracked)
        core.getPlugin().getLog().info("Non-premium username detected: {}", username);
        
        // ตรวจสอบว่าเคยมีคนใช้ชื่อนี้เป็น premium ไหม (จาก database)
        if (storedProfile != null && storedProfile.isOnlinemodePreferred()) {
            // เคยเป็น premium แต่ตอนนี้ Mojang บอกว่าไม่มี → อาจเป็น name change
            // ให้ผ่านไปก่อน ถ้าเขาเป็นเจ้าของจริงเขาจะ verify ผ่าน session ได้
            // ถ้าไม่ใช่เจ้าของจริง → จะถูก kick ที่ VerifyResponseTask เพราะ verify ไม่ผ่าน
            core.getPlugin().getLog().info("Player {} was previously premium but name not found in Mojang (possible name change)", username);
            
            // BUG FIX: ต้องบันทึกลง database ด้วย ไม่ใช่แค่ set ใน memory
            // อัพเดตสถานะให้เป็น false ชั่วคราว ถ้า verify ผ่านจะถูกอัพเดตเป็น true อีกครั้งใน VerifyResponseTask
            // PremiumGuard: Skip save if no database
            storedProfile.setOnlinemodePreferred(false);
            if (core.getStorage() != null) {
                core.getStorage().save(storedProfile);
            }
        }

        // 7. สร้าง profile ใหม่สำหรับ cracked ถ้ายังไม่มี
        if (storedProfile == null) {
            storedProfile = new StoredProfile(null, username, false, 
                com.github.games647.fastlogin.core.shared.FloodgateState.FALSE, ip);
            // ไม่บันทึกลง database ทันที - รอให้ login สำเร็จก่อน
        }

        // อนุญาตให้เข้าแบบ cracked (ไม่ต้อง verify session)
        startCrackedSession(source, storedProfile, username);
    }

    private void performBedrockChecks(String username, S source) {
        // Bedrock ผ่านได้เลยโดยไม่ต้องตรวจสอบ premium
        // PremiumGuard: Skip database lookup if no storage
        StoredProfile profile = core.getStorage() != null ? core.getStorage().loadProfile(username) : null;
        if (profile == null) {
            profile = new StoredProfile(null, username, false,
                com.github.games647.fastlogin.core.shared.FloodgateState.TRUE,
                source.getAddress().getAddress().getHostAddress());
        }
        startCrackedSession(source, profile, username);
    }

    public abstract FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, S source, StoredProfile profile);

    public abstract void requestPremiumLogin(S source, StoredProfile profile, String username, boolean registered);

    public abstract void startCrackedSession(S source, StoredProfile profile, String username);

    /**
     * PremiumGuard: Check if a username is in the bypass list.
     * Platform-specific implementation (SQL for Bukkit, YML for Velocity).
     */
    public abstract boolean isBypassed(String username);
}
