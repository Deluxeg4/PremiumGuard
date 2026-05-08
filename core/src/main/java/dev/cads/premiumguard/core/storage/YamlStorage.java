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
package dev.cads.premiumguard.core.storage;

import dev.cads.premiumguard.core.shared.FloodgateState;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified YML storage - replaces SQL for PremiumGuard
 * Stores profiles and bypass list in YML files
 */
public class YamlStorage implements AuthStorage {

    private final Path dataFolder;
    private final Path profilesFile;
    private final Path bypassFile;
    private final Yaml yaml;

    // In-memory cache
    private final Map<String, StoredProfile> profilesByName;
    private final Map<UUID, StoredProfile> profilesByUUID;
    private final Set<String> bypassList;

    public YamlStorage(Path dataFolder) {
        this.dataFolder = dataFolder;
        this.profilesFile = dataFolder.resolve("profiles.yml");
        this.bypassFile = dataFolder.resolve("bypass.yml");
        this.yaml = new Yaml();
        this.profilesByName = new ConcurrentHashMap<>();
        this.profilesByUUID = new ConcurrentHashMap<>();
        this.bypassList = ConcurrentHashMap.newKeySet();

        load();
    }

    @SuppressWarnings("unchecked")
    public synchronized void load() {
        // Load profiles
        if (Files.exists(profilesFile)) {
            try (Reader reader = Files.newBufferedReader(profilesFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("profiles")) {
                    List<Map<String, Object>> profiles = (List<Map<String, Object>>) data.get("profiles");
                    for (Map<String, Object> profileData : profiles) {
                        StoredProfile profile = deserializeProfile(profileData);
                        if (profile != null) {
                            profilesByName.put(profile.getName().toLowerCase(), profile);
                            if (profile.getId() != null) {
                                profilesByUUID.put(profile.getId(), profile);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // Log error
            }
        }

        // Load bypass list
        if (Files.exists(bypassFile)) {
            try (Reader reader = Files.newBufferedReader(bypassFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("bypass")) {
                    List<String> list = (List<String>) data.get("bypass");
                    if (list != null) {
                        bypassList.addAll(list);
                    }
                }
            } catch (IOException e) {
                // Log error
            }
        }
    }

    public synchronized void save() {
        try {
            if (!Files.exists(dataFolder)) {
                Files.createDirectories(dataFolder);
            }

            // Save profiles
            List<Map<String, Object>> profileList = new ArrayList<>();
            for (StoredProfile profile : profilesByName.values()) {
                profileList.add(serializeProfile(profile));
            }
            Map<String, Object> profileData = new HashMap<>();
            profileData.put("profiles", profileList);

            try (Writer writer = Files.newBufferedWriter(profilesFile)) {
                yaml.dump(profileData, writer);
            }

            // Save bypass list
            Map<String, Object> bypassData = new HashMap<>();
            bypassData.put("bypass", new ArrayList<>(bypassList));

            try (Writer writer = Files.newBufferedWriter(bypassFile)) {
                yaml.dump(bypassData, writer);
            }
        } catch (IOException e) {
            // Log error
        }
    }

    @Override
    public StoredProfile loadProfile(String name) {
        StoredProfile profile = profilesByName.get(name.toLowerCase());
        return profile != null ? profile : new StoredProfile(null, name, false, FloodgateState.FALSE, "");
    }

    @Override
    public StoredProfile loadProfile(UUID id) {
        return profilesByUUID.get(id);
    }

    @Override
    public void save(StoredProfile profile) {
        profilesByName.put(profile.getName().toLowerCase(), profile);
        if (profile.getId() != null) {
            profilesByUUID.put(profile.getId(), profile);
        }
        save();
    }

    @Override
    public int deleteProfile(String name) {
        StoredProfile removed = profilesByName.remove(name.toLowerCase());
        if (removed == null) {
            return 0;
        }

        if (removed.getId() != null) {
            profilesByUUID.remove(removed.getId());
        }

        save();
        return 1;
    }

    @Override
    public boolean isBypassed(String name) {
        return bypassList.contains(name.toLowerCase());
    }

    @Override
    public boolean addBypass(String name) {
        boolean added = bypassList.add(name.toLowerCase());
        if (added) {
            save();
        }
        return added;
    }

    @Override
    public boolean removeBypass(String name) {
        boolean removed = bypassList.remove(name.toLowerCase());
        if (removed) {
            save();
        }
        return removed;
    }

    @Override
    public List<String> getBypassList() {
        List<String> list = new ArrayList<>(bypassList);
        Collections.sort(list);
        return list;
    }

    @Override
    public void close() {
        save();
    }

    private StoredProfile deserializeProfile(Map<String, Object> data) {
        try {
            String name = (String) data.get("name");
            String uuidStr = (String) data.get("uuid");
            UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
            boolean premium = (Boolean) data.getOrDefault("premium", false);
            String lastIp = (String) data.getOrDefault("lastIp", "");
            String floodgateStr = (String) data.getOrDefault("floodgate", "FALSE");
            FloodgateState floodgate = FloodgateState.valueOf(floodgateStr);

            StoredProfile profile = new StoredProfile(uuid, name, premium, floodgate, lastIp);

            return profile;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> serializeProfile(StoredProfile profile) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", profile.getName());
        if (profile.getId() != null) {
            data.put("uuid", profile.getId().toString());
        }
        data.put("premium", profile.isOnlinemodePreferred());
        data.put("lastIp", profile.getLastIp());
        data.put("floodgate", profile.getFloodgate().name());
        return data;
    }
}


