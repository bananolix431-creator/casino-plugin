package dev.casino;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class CasinoCommand implements CommandExecutor {
    private final CasinoPlugin pl;

    CasinoCommand(CasinoPlugin pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur für Spieler.");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "coins" -> p.sendMessage(CasinoPlugin.mm("<gold>Coins: <yellow>" + pl.coins.get(p.getUniqueId())));
            case "sell" -> pl.shop.sellHand(p);
            case "sellall" -> pl.shop.sellAll(p);
            case "prices" -> pl.shop.showPrices(p);
            case "bet" -> bet(p, a);
            case "casino" -> admin(p, a);
            default -> {
            }
        }
        return true;
    }

    private void bet(Player p, String[] a) {
        if (a.length != 2) {
            p.sendMessage(CasinoPlugin.mm("<gray>/bet [red|black|green|even|odd|0-36] [Betrag]"));
            return;
        }
        RouletteTable table = pl.nearestRoulette(p.getLocation(), 8);
        if (table == null) {
            p.sendMessage(CasinoPlugin.mm("<red>Du bist an keinem Roulette-Tisch."));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(a[1]);
        } catch (NumberFormatException e) {
            p.sendMessage(CasinoPlugin.mm("<red>Ungültiger Betrag."));
            return;
        }
        String err = table.placeBet(p, a[0], amount);
        if (err != null) {
            p.sendMessage(CasinoPlugin.mm("<red>" + err));
        } else {
            p.sendMessage(CasinoPlugin.mm("<green>Wette platziert: <yellow>" + a[0].toLowerCase()
                    + " <gray>für <gold>" + amount + " Coins"));
        }
    }

    private void admin(Player p, String[] a) {
        if (!p.hasPermission("casino.admin")) {
            p.sendMessage(CasinoPlugin.mm("<red>Keine Rechte."));
            return;
        }
        if (a.length == 0) {
            p.sendMessage(CasinoPlugin.mm("<gray>/casino place [slot|roulette] | remove | give [Spieler] [Betrag] | reload"));
            return;
        }
        switch (a[0].toLowerCase()) {
            case "place" -> {
                if (a.length < 2) {
                    p.sendMessage(CasinoPlugin.mm("<gray>/casino place [slot|roulette]"));
                    return;
                }
                Block b = p.getTargetBlockExact(6);
                if (b == null) {
                    p.sendMessage(CasinoPlugin.mm("<red>Schau auf einen Block (max. 6 Blöcke entfernt)."));
                    return;
                }
                Location base = b.getLocation().add(0.5, 1, 0.5);
                BlockFace face = p.getFacing().getOppositeFace(); // Vorderseite zeigt zum Spieler
                Machine m = pl.addMachine(a[1].toLowerCase(), base, face);
                p.sendMessage(CasinoPlugin.mm(m == null ? "<red>Unbekannter Typ: slot oder roulette." : "<green>Platziert!"));
            }
            case "remove" -> p.sendMessage(CasinoPlugin.mm(pl.removeNearest(p.getLocation(), 4)
                    ? "<green>Entfernt." : "<red>Kein Automat in 4 Blöcken Nähe."));
            case "give" -> {
                if (a.length < 3) {
                    p.sendMessage(CasinoPlugin.mm("<gray>/casino give [Spieler] [Betrag]"));
                    return;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    p.sendMessage(CasinoPlugin.mm("<red>Spieler nicht online."));
                    return;
                }
                try {
                    pl.coins.add(t.getUniqueId(), Long.parseLong(a[2]));
                    p.sendMessage(CasinoPlugin.mm("<green>Coins vergeben."));
                } catch (NumberFormatException e) {
                    p.sendMessage(CasinoPlugin.mm("<red>Ungültiger Betrag."));
                }
            }
            case "reload" -> {
                pl.reloadConfig();
                p.sendMessage(CasinoPlugin.mm("<green>Config neu geladen (Automaten per remove/place neu bauen)."));
            }
            default -> p.sendMessage(CasinoPlugin.mm("<red>Unbekannter Unterbefehl."));
        }
    }
}
