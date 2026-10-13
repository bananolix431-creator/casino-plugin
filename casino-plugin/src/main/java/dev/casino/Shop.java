package dev.casino;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

final class Shop {
    private final CasinoPlugin pl;
    private final Random rnd = new Random();
    private final Map<Material, Double> market = new HashMap<>();

    Shop(CasinoPlugin pl) {
        this.pl = pl;
    }

    /** Würfelt neue Preisfaktoren (70% - 160%). */
    void reroll() {
        ConfigurationSection s = pl.getConfig().getConfigurationSection("prices");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null) market.put(m, 0.7 + rnd.nextDouble() * 0.9);
        }
        Bukkit.broadcast(CasinoPlugin.mm("<gold>[Händler] <gray>Die Marktpreise haben sich geändert! <yellow>/prices"));
    }

    int price(Material m) {
        int base = pl.getConfig().getInt("prices." + m.name(), 0);
        return (int) Math.round(base * market.getOrDefault(m, 1.0));
    }

    void sellHand(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        int price = it.getType().isAir() ? 0 : price(it.getType());
        if (price <= 0 || it.hasItemMeta()) {
            p.sendMessage(CasinoPlugin.mm("<red>Der Händler kauft das nicht. <gray>(/prices)"));
            return;
        }
        long total = (long) price * it.getAmount();
        int amount = it.getAmount();
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        pl.coins.add(p.getUniqueId(), total);
        p.sendMessage(CasinoPlugin.mm("<green>" + amount + "x verkauft für <gold>" + total + " Coins<green>."));
    }

    void sellAll(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        long total = 0;
        int items = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir() || it.hasItemMeta()) continue;
            int price = price(it.getType());
            if (price <= 0) continue;
            total += (long) price * it.getAmount();
            items += it.getAmount();
            inv.setItem(i, null);
        }
        if (items == 0) {
            p.sendMessage(CasinoPlugin.mm("<red>Du hast nichts Verkaufbares dabei."));
            return;
        }
        pl.coins.add(p.getUniqueId(), total);
        p.sendMessage(CasinoPlugin.mm("<green>" + items + " Items verkauft für <gold>" + total + " Coins<green>."));
    }

    void showPrices(Player p) {
        ConfigurationSection s = pl.getConfig().getConfigurationSection("prices");
        if (s == null) return;
        p.sendMessage(CasinoPlugin.mm("<gold><bold>Aktuelle Marktpreise"));
        for (String k : s.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m == null) continue;
            long pct = Math.round(market.getOrDefault(m, 1.0) * 100);
            p.sendMessage(CasinoPlugin.mm("<gray>" + k + ": <yellow>" + price(m) + " <dark_gray>(" + pct + "%)"));
        }
    }
}
