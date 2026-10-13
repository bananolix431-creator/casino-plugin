package dev.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CasinoPlugin extends JavaPlugin implements Listener {
    public Coins coins;
    public Shop shop;
    final Map<UUID, Machine> byEntity = new HashMap<>();
    final List<Machine> machines = new ArrayList<>();
    private File machineFile;
    private YamlConfiguration machineCfg;

    public static Component mm(String s) {
        return MiniMessage.miniMessage().deserialize(s);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        coins = new Coins(this);
        shop = new Shop(this);
        machineFile = new File(getDataFolder(), "machines.yml");
        machineCfg = YamlConfiguration.loadConfiguration(machineFile);

        CasinoCommand cmd = new CasinoCommand(this);
        for (String n : List.of("coins", "sell", "sellall", "prices", "bet", "casino")) {
            getCommand(n).setExecutor(cmd);
        }
        getServer().getPluginManager().registerEvents(this, this);

        loadMachines();
        getServer().getScheduler().runTask(this, () -> machines.forEach(this::spawnIfLoaded));
        getServer().getScheduler().runTaskTimer(this, shop::reroll, 0L, 20L * 60 * 10);
    }

    @Override
    public void onDisable() {
        machines.forEach(Machine::despawn);
    }

    /** Item mit optionalem Resource-Pack-Model (item_model) aus der Config. */
    ItemStack icon(Material m, String configPath) {
        ItemStack s = new ItemStack(m);
        String key = getConfig().getString(configPath, "");
        if (!key.isEmpty()) {
            NamespacedKey nk = NamespacedKey.fromString(key);
            if (nk != null) {
                ItemMeta meta = s.getItemMeta();
                meta.setItemModel(nk);
                s.setItemMeta(meta);
            }
        }
        return s;
    }

    // ---------- Events ----------

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Machine m = byEntity.get(e.getRightClicked().getUniqueId());
        if (m == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        m.use(e.getPlayer());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        getServer().getScheduler().runTask(this, () -> {
            for (Machine m : machines) {
                if (m.base.getWorld().equals(e.getWorld())
                        && (m.base.getBlockX() >> 4) == e.getChunk().getX()
                        && (m.base.getBlockZ() >> 4) == e.getChunk().getZ()) {
                    spawnIfLoaded(m);
                }
            }
        });
    }

    private void spawnIfLoaded(Machine m) {
        World w = m.base.getWorld();
        if (w.isChunkLoaded(m.base.getBlockX() >> 4, m.base.getBlockZ() >> 4) && !m.isSpawned()) {
            m.spawn();
        }
    }

    // ---------- Machine-Verwaltung ----------

    private Machine create(String type, String id, Location base, BlockFace face) {
        return switch (type) {
            case "slot" -> new SlotMachine(this, id, base, face);
            case "roulette" -> new RouletteTable(this, id, base, face);
            default -> null;
        };
    }

    Machine addMachine(String type, Location base, BlockFace face) {
        Machine m = create(type, UUID.randomUUID().toString().substring(0, 8), base, face);
        if (m == null) return null;
        machines.add(m);
        m.spawn();
        saveMachines();
        return m;
    }

    boolean removeNearest(Location l, double radius) {
        Machine best = null;
        double bd = radius * radius;
        for (Machine m : machines) {
            if (!m.base.getWorld().equals(l.getWorld())) continue;
            double d = m.base.distanceSquared(l);
            if (d <= bd) {
                bd = d;
                best = m;
            }
        }
        if (best == null) return false;
        best.despawn();
        machines.remove(best);
        saveMachines();
        return true;
    }

    RouletteTable nearestRoulette(Location l, double radius) {
        RouletteTable best = null;
        double bd = radius * radius;
        for (Machine m : machines) {
            if (!(m instanceof RouletteTable r) || !m.base.getWorld().equals(l.getWorld())) continue;
            double d = m.base.distanceSquared(l);
            if (d <= bd) {
                bd = d;
                best = r;
            }
        }
        return best;
    }

    private void saveMachines() {
        machineCfg.set("machines", null);
        for (Machine m : machines) {
            String k = "machines." + m.id;
            machineCfg.set(k + ".type", m.type());
            machineCfg.set(k + ".world", m.base.getWorld().getName());
            machineCfg.set(k + ".x", m.base.getX());
            machineCfg.set(k + ".y", m.base.getY());
            machineCfg.set(k + ".z", m.base.getZ());
            machineCfg.set(k + ".face", m.face.name());
        }
        try {
            machineCfg.save(machineFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadMachines() {
        ConfigurationSection s = machineCfg.getConfigurationSection("machines");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            World w = Bukkit.getWorld(c.getString("world", ""));
            if (w == null) continue;
            Location base = new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
            BlockFace face = BlockFace.valueOf(c.getString("face", "SOUTH"));
            Machine m = create(c.getString("type", ""), id, base, face);
            if (m != null) machines.add(m);
        }
    }
}
