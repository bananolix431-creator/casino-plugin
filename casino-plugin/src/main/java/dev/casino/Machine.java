package dev.casino;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Basis für alle Automaten. Alle Teile sind nicht-persistente Display-Entities,
 * die beim Chunk-Load bzw. Serverstart aus machines.yml neu gespawnt werden.
 */
abstract class Machine {
    final CasinoPlugin plugin;
    final String id;
    final Location base;   // Boden-Mitte des Automaten
    final BlockFace face;  // Richtung, in die die Vorderseite zeigt
    final List<Entity> parts = new ArrayList<>();
    Interaction hitbox;

    Machine(CasinoPlugin plugin, String id, Location base, BlockFace face) {
        this.plugin = plugin;
        this.id = id;
        this.base = base;
        this.face = face;
    }

    abstract String type();

    abstract void build();

    abstract void use(Player p);

    boolean isSpawned() {
        return hitbox != null && hitbox.isValid();
    }

    void spawn() {
        despawn();
        build();
    }

    void despawn() {
        for (Entity e : parts) e.remove();
        parts.clear();
        if (hitbox != null) {
            plugin.byEntity.remove(hitbox.getUniqueId());
            hitbox = null;
        }
    }

    /** Position relativ zum Automaten: vor/zurück, rechts/links, hoch. */
    Location at(double fwd, double right, double up) {
        Vector f = face.getDirection();
        Vector r = new Vector(-f.getZ(), 0, f.getX());
        return base.clone().add(f.clone().multiply(fwd)).add(r.multiply(right)).add(0, up, 0);
    }

    <T extends Entity> T add(Class<T> type, Location loc, Consumer<T> init) {
        T e = loc.getWorld().spawn(loc, type, x -> {
            x.setPersistent(false);
            init.accept(x);
        });
        parts.add(e);
        return e;
    }

    /** Quader aus einem Block-Display. w = Breite (rechts), h = Höhe, d = Tiefe (vorne). */
    BlockDisplay box(Material m, double fwd, double right, double up, float w, float h, float d) {
        boolean ns = face == BlockFace.NORTH || face == BlockFace.SOUTH;
        float sx = ns ? w : d;
        float sz = ns ? d : w;
        return add(BlockDisplay.class, at(fwd, right, up), b -> {
            b.setBlock(m.createBlockData());
            b.setTransformation(new Transformation(
                    new Vector3f(-sx / 2, 0, -sz / 2), new AxisAngle4f(), new Vector3f(sx, h, sz), new AxisAngle4f()));
        });
    }

    ItemDisplay item(ItemStack stack, Location loc, float scale, Display.Billboard billboard,
                     ItemDisplay.ItemDisplayTransform mode) {
        return add(ItemDisplay.class, loc, d -> {
            d.setItemStack(stack);
            d.setBillboard(billboard);
            d.setItemDisplayTransform(mode);
            d.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
    }

    TextDisplay text(Location loc, String mini) {
        return add(TextDisplay.class, loc, t -> {
            t.text(CasinoPlugin.mm(mini));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
        });
    }

    void hitbox(float w, float h) {
        hitbox = add(Interaction.class, base.clone(), i -> {
            i.setInteractionWidth(w);
            i.setInteractionHeight(h);
        });
        plugin.byEntity.put(hitbox.getUniqueId(), this);
    }

    void nearby(String mini, double radius) {
        for (Player p : base.getWorld().getNearbyPlayers(base, radius)) {
            p.sendMessage(CasinoPlugin.mm(mini));
        }
    }
}
