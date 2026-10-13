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
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

final class RouletteTable extends Machine {
    static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    record Bet(UUID player, String type, long amount) {
    }

    private final List<Bet> bets = new ArrayList<>();
    private final Random rnd = new Random();
    boolean betting;
    boolean spinning;
    private ItemDisplay wheel;
    private ItemDisplay ball;
    private TextDisplay label;
    private Location center;

    RouletteTable(CasinoPlugin plugin, String id, Location base, BlockFace face) {
        super(plugin, id, base, face);
    }

    @Override
    String type() {
        return "roulette";
    }

    private String idleText() {
        return "<gold><bold>ROULETTE\n<gray>Rechtsklick zum Starten";
    }

    @Override
    void build() {
        box(Material.DARK_OAK_PLANKS, 0, 0, 0, 2.8f, 0.85f, 1.8f);
        box(Material.GREEN_CONCRETE, 0, 0, 0.85, 2.6f, 0.1f, 1.6f);
        center = at(0, -0.7, 1.0);

        ItemStack wheelItem = plugin.icon(Material.CLOCK, "models.wheel");
        wheel = add(ItemDisplay.class, center.clone(), d -> {
            d.setItemStack(wheelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
        });
        rotate(0f);

        ball = add(ItemDisplay.class, center.clone().add(0.45, 0.05, 0), d -> {
            d.setItemStack(new ItemStack(Material.SNOWBALL));
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.15f), new Quaternionf()));
        });

        label = text(at(0, 0, 2.1), idleText());
        hitbox(2.8f, 1.0f);
    }

    private void rotate(float angle) {
        wheel.setInterpolationDelay(0);
        wheel.setInterpolationDuration(1);
        wheel.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf().rotateY(angle).rotateX(-1.5708f), // flach hinlegen, dann drehen
                new Vector3f(1.3f),
                new Quaternionf()));
    }

    @Override
    void use(Player p) {
        if (spinning) {
            p.sendMessage(CasinoPlugin.mm("<red>Das Rad dreht sich bereits."));
            return;
        }
        if (!betting) startBetting();
        p.sendMessage(CasinoPlugin.mm("<gold>Roulette: <gray>/bet [red|black|green|even|odd|0-36] [Betrag]"));
    }

    private void startBetting() {
        betting = true;
        int secs = plugin.getConfig().getInt("roulette-bet-time", 20);
        label.text(CasinoPlugin.mm("<gold><bold>ROULETTE\n<green>Wetten offen: <yellow>" + secs + "s\n<gray>/bet ..."));
        nearby("<gold>[Roulette] <white>Wetten sind offen! <gray>/bet [red|black|green|even|odd|0-36] [Betrag]", 12);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (bets.isEmpty()) {
                betting = false;
                label.text(CasinoPlugin.mm(idleText()));
                nearby("<gold>[Roulette] <gray>Niemand hat gesetzt - Runde abgebrochen.", 12);
            } else {
                spin();
            }
        }, secs * 20L);
    }

    /** @return Fehlermeldung oder null bei Erfolg */
    String placeBet(Player p, String typeRaw, long amount) {
        if (!betting) return "Gerade läuft keine Wettrunde (Rechtsklick auf den Tisch).";
        String type = typeRaw.toLowerCase();
        if (!validType(type)) return "Ungültige Wette: red, black, green, even, odd oder 0-36.";
        long max = plugin.getConfig().getLong("roulette-max-bet", 1000);
        if (amount < 1 || amount > max) return "Einsatz muss zwischen 1 und " + max + " liegen.";
        if (!plugin.coins.take(p.getUniqueId(), amount)) return "Zu wenig Coins!";
        bets.add(new Bet(p.getUniqueId(), type, amount));
        return null;
    }

    private static boolean validType(String t) {
        if (Set.of("red", "black", "green", "even", "odd").contains(t)) return true;
        try {
            int n = Integer.parseInt(t);
            return n >= 0 && n <= 36;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean matches(String type, int n) {
        return switch (type) {
            case "green" -> n == 0;
            case "red" -> n != 0 && RED.contains(n);
            case "black" -> n != 0 && !RED.contains(n);
            case "even" -> n != 0 && n % 2 == 0;
            case "odd" -> n % 2 == 1;
            default -> Integer.parseInt(type) == n;
        };
    }

    private static int mult(String type) {
        return switch (type) {
            case "red", "black", "even", "odd" -> 2;
            default -> 36; // einzelne Zahl oder green (=0)
        };
    }

    private void spin() {
        betting = false;
        spinning = true;
        int result = rnd.nextInt(37);
        label.text(CasinoPlugin.mm("<gold><bold>ROULETTE\n<red>Nichts geht mehr!"));
        new BukkitRunnable() {
            int t = 0;
            float angle = 0f;
            float ballAngle = 0f;

            @Override
            public void run() {
                t++;
                float speed = 0.5f * (1f - t / 160f) + 0.03f;
                angle += speed;
                ballAngle -= speed * 1.6f;
                rotate(angle);
                double radius = 0.45 - 0.2 * (t / 160.0); // Kugel spiralt nach innen
                ball.teleport(center.clone().add(Math.cos(ballAngle) * radius, 0.05, Math.sin(ballAngle) * radius));
                if (t % 6 == 0 && t < 140) {
                    center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                }
                if (t >= 160) {
                    cancel();
                    finish(result);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finish(int n) {
        String col = n == 0 ? "<green>" : RED.contains(n) ? "<red>" : "<gray>";
        label.text(CasinoPlugin.mm("<gold><bold>ROULETTE\n" + col + "<bold>" + n));
        nearby("<gold>[Roulette] <white>Die Kugel fällt auf " + col + "<bold>" + n + "<reset><white>!", 12);
        center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);

        Map<UUID, Long> wins = new HashMap<>();
        for (Bet b : bets) {
            if (matches(b.type(), n)) wins.merge(b.player(), b.amount() * mult(b.type()), Long::sum);
        }
        for (var e : wins.entrySet()) {
            plugin.coins.add(e.getKey(), e.getValue());
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.sendMessage(CasinoPlugin.mm("<green><bold>GEWONNEN! <gold>+" + e.getValue() + " Coins"));
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.2);
            }
        }
        Set<UUID> notified = new HashSet<>();
        for (Bet b : bets) {
            if (wins.containsKey(b.player()) || !notified.add(b.player())) continue;
            Player p = Bukkit.getPlayer(b.player());
            if (p != null) p.sendMessage(CasinoPlugin.mm("<red>Leider verloren."));
        }
        bets.clear();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spinning = false;
            label.text(CasinoPlugin.mm(idleText()));
        }, 100L);
    }
}
