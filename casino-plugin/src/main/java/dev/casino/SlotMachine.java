package dev.casino;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

final class SlotMachine extends Machine {
    static final List<Material> SYMBOLS = List.of(
            Material.REDSTONE, Material.EMERALD, Material.GOLD_INGOT, Material.DIAMOND, Material.NETHER_STAR);
    // Gewichte (Summe 100) und Multiplikatoren bei 3 gleichen. 2 gleiche = Einsatz zurück.
    // Ergibt ca. 99% Auszahlungsquote - für eine härtere Wirtschaft die Multiplikatoren senken.
    static final int[] WEIGHTS = {40, 30, 20, 8, 2};
    static final int[] MULT = {3, 5, 8, 20, 50};

    private final Random rnd = new Random();
    private final ItemDisplay[] reels = new ItemDisplay[3];
    private boolean spinning;

    SlotMachine(CasinoPlugin plugin, String id, Location base, BlockFace face) {
        super(plugin, id, base, face);
    }

    @Override
    String type() {
        return "slot";
    }

    @Override
    void build() {
        String cabinetModel = plugin.getConfig().getString("models.slot_cabinet", "");
        if (!cabinetModel.isEmpty()) {
            // Eigenes 3D-Model aus dem Resource Pack
            item(plugin.icon(Material.PAPER, "models.slot_cabinet"), at(0, 0, 1.1), 2f,
                    Display.Billboard.FIXED, ItemDisplay.ItemDisplayTransform.NONE);
        } else {
            box(Material.GILDED_BLACKSTONE, 0, 0, 0, 1.6f, 2.2f, 0.7f);
            box(Material.GOLD_BLOCK, 0, 0, 2.2, 1.8f, 0.2f, 0.9f);
            box(Material.BLACK_CONCRETE, 0.37, 0, 1.0, 1.4f, 0.6f, 0.05f);
            box(Material.RED_CONCRETE, 0.37, 0, 0.45, 0.25f, 0.25f, 0.05f); // "Knopf"
        }
        for (int i = 0; i < 3; i++) {
            reels[i] = item(symbolIcon(SYMBOLS.get(0)), at(0.45, (i - 1) * 0.45, 1.3), 0.4f,
                    Display.Billboard.VERTICAL, ItemDisplay.ItemDisplayTransform.GUI);
        }
        text(at(0, 0, 2.7), "<gold><bold>★ SLOTS ★\n<gray>Einsatz: <yellow>" + bet() + " Coins");
        hitbox(1.7f, 2.3f);
    }

    private long bet() {
        return plugin.getConfig().getLong("slot-bet", 10);
    }

    private ItemStack symbolIcon(Material m) {
        return plugin.icon(m, "models.symbol." + m.name());
    }

    private int roll() {
        int r = rnd.nextInt(100), acc = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            acc += WEIGHTS[i];
            if (r < acc) return i;
        }
        return 0;
    }

    @Override
    void use(Player p) {
        if (spinning) return;
        long bet = bet();
        if (!plugin.coins.take(p.getUniqueId(), bet)) {
            p.sendMessage(CasinoPlugin.mm("<red>Zu wenig Coins! Du brauchst <gold>" + bet + "<red>."));
            return;
        }
        spinning = true;
        int[] result = {roll(), roll(), roll()};
        boolean[] stopped = new boolean[3];
        Location loc = base;
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t += 2;
                for (int i = 0; i < 3; i++) {
                    if (t < 40 + i * 20) {
                        reels[i].setItemStack(symbolIcon(SYMBOLS.get(rnd.nextInt(SYMBOLS.size()))));
                    } else if (!stopped[i]) {
                        stopped[i] = true;
                        reels[i].setItemStack(symbolIcon(SYMBOLS.get(result[i])));
                        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.8f + i * 0.3f);
                    }
                }
                if (t % 4 == 0) loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
                if (t >= 40 + 2 * 20) {
                    cancel();
                    spinning = false;
                    payout(p, bet, result);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void payout(Player p, long bet, int[] r) {
        long win = 0;
        if (r[0] == r[1] && r[1] == r[2]) {
            win = bet * MULT[r[0]];
        } else if (r[0] == r[1] || r[1] == r[2] || r[0] == r[2]) {
            win = bet; // Einsatz zurück
        }
        if (win > 0) plugin.coins.add(p.getUniqueId(), win);

        if (win > bet) {
            p.sendMessage(CasinoPlugin.mm("<green><bold>GEWONNEN! <gold>+" + win + " Coins"));
            base.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at(0, 0, 1.5), 40, 0.4, 0.4, 0.4, 0.2);
            base.getWorld().playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            if (r[0] == r[1] && r[1] == r[2] && r[0] >= 3) {
                Bukkit.broadcast(CasinoPlugin.mm("<gold><bold>[Casino] <yellow>" + p.getName()
                        + " <gold>hat an der Slotmachine <bold>" + win + " Coins<reset><gold> gewonnen!"));
            }
        } else if (win == bet) {
            p.sendMessage(CasinoPlugin.mm("<yellow>Zwei gleiche - Einsatz zurück."));
        } else {
            p.sendMessage(CasinoPlugin.mm("<red>Leider verloren. <gray>(" + plugin.coins.get(p.getUniqueId()) + " Coins übrig)"));
        }
    }
}
