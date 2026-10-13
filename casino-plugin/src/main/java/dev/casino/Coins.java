package dev.casino;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class Coins {
    private final File file;
    private final YamlConfiguration cfg;

    Coins(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "coins.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public long get(UUID u) {
        return cfg.getLong(u.toString(), 0L);
    }

    public void set(UUID u, long v) {
        cfg.set(u.toString(), Math.max(0L, v));
        save();
    }

    public void add(UUID u, long v) {
        set(u, get(u) + v);
    }

    public boolean take(UUID u, long v) {
        if (v < 0 || get(u) < v) return false;
        set(u, get(u) - v);
        return true;
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
