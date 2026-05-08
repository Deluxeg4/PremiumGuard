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
package dev.cads.premiumguard.velocity.storage;

import dev.cads.premiumguard.velocity.PremiumGuardVelocity;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YML-based bypass storage for Velocity (no SQL database required)
 */
public class YamlBypassStorage {

    private final PremiumGuardVelocity plugin;
    private final Path bypassFile;
    private final Set<String> bypassList;
    private final Yaml yaml;

    public YamlBypassStorage(PremiumGuardVelocity plugin) {
        this.plugin = plugin;
        this.bypassFile = plugin.getPluginFolder().resolve("bypass.yml");
        this.bypassList = ConcurrentHashMap.newKeySet();
        this.yaml = new Yaml();
        load();
    }

    @SuppressWarnings("unchecked")
    public synchronized void load() {
        if (!Files.exists(bypassFile)) {
            plugin.getLog().info("No bypass.yml found, creating empty bypass list");
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(bypassFile)) {
            Map<String, Object> data = yaml.load(reader);
            if (data != null && data.containsKey("bypass")) {
                List<String> list = (List<String>) data.get("bypass");
                bypassList.clear();
                if (list != null) {
                    bypassList.addAll(list);
                }
                plugin.getLog().info("Loaded {} bypassed names from bypass.yml", bypassList.size());
            }
        } catch (IOException e) {
            plugin.getLog().error("Failed to load bypass.yml", e);
        }
    }

    public synchronized void save() {
        try {
            if (!Files.exists(plugin.getPluginFolder())) {
                Files.createDirectories(plugin.getPluginFolder());
            }

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("bypass", new ArrayList<>(bypassList));

            try (Writer writer = Files.newBufferedWriter(bypassFile)) {
                yaml.dump(data, writer);
            }
        } catch (IOException e) {
            plugin.getLog().error("Failed to save bypass.yml", e);
        }
    }

    public boolean isBypassed(String username) {
        return bypassList.contains(username.toLowerCase());
    }

    public boolean addBypass(String username) {
        boolean added = bypassList.add(username.toLowerCase());
        if (added) {
            save();
        }
        return added;
    }

    public boolean removeBypass(String username) {
        boolean removed = bypassList.remove(username.toLowerCase());
        if (removed) {
            save();
        }
        return removed;
    }

    public List<String> getBypassList() {
        return Collections.unmodifiableList(new ArrayList<>(bypassList));
    }
}


