package io.github.lmalololol.leafpvp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leaf-PvP: knockback profiles per world.
 *
 * <p>Profiles live in {@code leaf-pvp.yml} next to the server jar. A world uses the profile named for it under
 * {@code worlds} in that file, else the one a plugin gave it with {@link #world(String, String, int)} (Duels calls
 * this through reflection for its arena worlds). Players in any other world, and every mob, get 26.3's own knockback.
 * The {@code vanilla} profile is exactly 26.3's numbers, and a profile a plugin names that the file does not have yet
 * starts as a copy of it, so nothing changes until someone tunes it.
 */
public final class Knockback {

    /**
     * One set of knockback numbers. 26.3 gives every hit a push of 0.4 and a sprint hit (or a Knockback enchantment)
     * a second, extra push.
     *
     * @param friction        the target's speed is divided by this before a push is added (26.3: 2)
     * @param horizontal      multiplier for the sideways part of the push every hit gives (26.3: 1)
     * @param vertical        multiplier for its upward part (26.3: 1)
     * @param verticalLimit   highest upward speed a push can leave (26.3: 0.4)
     * @param extraHorizontal multiplier for the sideways part of the sprint and enchantment push (26.3: 1)
     * @param extraVertical   multiplier for its upward part (26.3: 1)
     * @param airVertical     also push up targets that are off the ground (26.3: false)
     */
    public record Profile(String name, double friction, double horizontal, double vertical, double verticalLimit,
                          double extraHorizontal, double extraVertical, boolean airVertical) {

        public static final Profile VANILLA = new Profile("vanilla", 2.0, 1.0, 1.0, 0.4, 1.0, 1.0, false);

        Profile named(final String other) {
            return new Profile(other, this.friction, this.horizontal, this.vertical, this.verticalLimit,
                this.extraHorizontal, this.extraVertical, this.airVertical);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("Leaf-PvP");
    private static final File FILE = new File("leaf-pvp.yml");
    private static final List<String> HEADER = List.of(
        "Leaf-PvP knockback profiles.",
        "A world uses the profile named for it under 'worlds', else the one a plugin gives it (Duels: duels-modern).",
        "Players everywhere else, and all mobs, get 26.3's own knockback; the 'vanilla' profile is those numbers.",
        "",
        "friction          the target's speed is divided by this before a push is added (vanilla 2.0)",
        "horizontal        multiplier for the sideways part of the 0.4 push every hit gives (vanilla 1.0)",
        "vertical          multiplier for its upward part (vanilla 1.0)",
        "vertical-limit    highest upward speed a push can leave (vanilla 0.4)",
        "extra-horizontal  multiplier for the sideways part of the sprint / Knockback enchantment push (vanilla 1.0)",
        "extra-vertical    multiplier for its upward part (vanilla 1.0)",
        "air-vertical      also push up players who are off the ground (vanilla false)",
        "",
        "Apply changes with /leaf reload."
    );

    private static final Map<String, Profile> PROFILES = new ConcurrentHashMap<>();
    // world name -> profile name, from leaf-pvp.yml (wins) and from plugins
    private static final Map<String, String> CONFIGURED = new ConcurrentHashMap<>();
    private static final Map<String, String> FROM_PLUGINS = new ConcurrentHashMap<>();

    static {
        reload();
    }

    private Knockback() {
    }

    /**
     * Gives a world a knockback profile; plugins call it through reflection. An unknown profile is created as a copy
     * of vanilla and written to leaf-pvp.yml. The hit delay is accepted for parity with PandaSpigot's profiles; plugins
     * set it per player with {@code LivingEntity#setMaximumNoDamageTicks}.
     */
    public static void world(final String world, final String profile, final int hitDelay) {
        if (world == null) {
            return;
        }
        if (profile == null || profile.isBlank()) {
            FROM_PLUGINS.remove(world);
            return;
        }
        final String name = key(profile);
        if (!PROFILES.containsKey(name)) {
            PROFILES.put(name, Profile.VANILLA.named(name));
            save();
        }
        FROM_PLUGINS.put(world, name);
    }

    /** The profile a world uses, or null for 26.3's own knockback. */
    public static Profile profile(final String world) {
        String name = CONFIGURED.get(world);
        if (name == null) {
            name = FROM_PLUGINS.get(world);
        }
        return name == null ? null : PROFILES.get(name);
    }

    /**
     * The speed a push leaves the target with: 26.3's formula from LivingEntity#knockback, with the world's numbers.
     *
     * @param movement the target's speed before the push
     * @param power    the push, after knockback resistance
     * @param extra    true for the sprint and enchantment push
     */
    public static Vec3 target(final LivingEntity target, final Vec3 movement, final double xd, final double zd, final double power, final boolean extra) {
        Profile p = target instanceof Player ? profile(target.level().getWorld().getName()) : null;
        if (p == null) {
            p = Profile.VANILLA;
        }
        final double sideways = power * (extra ? p.extraHorizontal() : p.horizontal());
        final double upward = power * (extra ? p.extraVertical() : p.vertical());
        final Vec3 push = new Vec3(xd, 0.0, zd).normalize().scale(sideways);
        return new Vec3(
            movement.x / p.friction() - push.x,
            target.onGround() || p.airVertical() ? Math.min(p.verticalLimit(), movement.y / p.friction() + upward) : movement.y,
            movement.z / p.friction() - push.z
        );
    }

    /** Reads leaf-pvp.yml again (/leaf reload), writing it with the vanilla profile when it is missing. */
    public static synchronized void reload() {
        final YamlConfiguration yml = YamlConfiguration.loadConfiguration(FILE);
        final Map<String, Profile> read = new TreeMap<>();
        read.put(Profile.VANILLA.name(), Profile.VANILLA);
        final ConfigurationSection profiles = yml.getConfigurationSection("profiles");
        if (profiles != null) {
            for (final String key : profiles.getKeys(false)) {
                final ConfigurationSection s = profiles.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                final String name = key(key);
                double friction = s.getDouble("friction", 2.0);
                if (!(friction > 0.0)) {
                    LOGGER.warn("Knockback profile {} has friction {}; it must be above 0, using 2.0", name, friction);
                    friction = 2.0;
                }
                read.put(name, new Profile(name, friction, s.getDouble("horizontal", 1.0), s.getDouble("vertical", 1.0),
                    s.getDouble("vertical-limit", 0.4), s.getDouble("extra-horizontal", 1.0), s.getDouble("extra-vertical", 1.0),
                    s.getBoolean("air-vertical", false)));
            }
        }
        // profiles plugins asked for this run stay, as vanilla copies if the file dropped them
        for (final String name : FROM_PLUGINS.values()) {
            read.putIfAbsent(name, Profile.VANILLA.named(name));
        }
        PROFILES.putAll(read);
        PROFILES.keySet().retainAll(read.keySet());

        final Map<String, String> worlds = new TreeMap<>();
        final ConfigurationSection section = yml.getConfigurationSection("worlds");
        if (section != null) {
            for (final String world : section.getKeys(false)) {
                final String name = section.getString(world);
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (!read.containsKey(key(name))) {
                    LOGGER.warn("World {} names knockback profile {}, which leaf-pvp.yml does not have", world, name);
                    continue;
                }
                worlds.put(world, key(name));
            }
        }
        CONFIGURED.putAll(worlds);
        CONFIGURED.keySet().retainAll(worlds.keySet());

        if (!FILE.exists() || profiles == null) {
            save();
        }
        LOGGER.info("{} knockback profiles, {} worlds set in {}", PROFILES.size(), CONFIGURED.size(), FILE.getName());
    }

    private static synchronized void save() {
        final YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(HEADER);
        final List<Profile> all = new ArrayList<>(new TreeMap<>(PROFILES).values());
        for (final Profile p : all) {
            final String at = "profiles." + p.name() + ".";
            yml.set(at + "friction", p.friction());
            yml.set(at + "horizontal", p.horizontal());
            yml.set(at + "vertical", p.vertical());
            yml.set(at + "vertical-limit", p.verticalLimit());
            yml.set(at + "extra-horizontal", p.extraHorizontal());
            yml.set(at + "extra-vertical", p.extraVertical());
            yml.set(at + "air-vertical", p.airVertical());
        }
        if (CONFIGURED.isEmpty()) {
            yml.createSection("worlds");
        } else {
            for (final Map.Entry<String, String> e : new TreeMap<>(CONFIGURED).entrySet()) {
                yml.set("worlds." + e.getKey(), e.getValue());
            }
        }
        try {
            yml.save(FILE);
        } catch (final IOException e) {
            LOGGER.warn("Could not write {}", FILE.getName(), e);
        }
    }

    private static String key(final String name) {
        // '.' separates YAML paths
        return name.trim().toLowerCase(Locale.ROOT).replace('.', '_');
    }
}
