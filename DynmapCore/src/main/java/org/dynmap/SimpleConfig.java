package org.dynmap;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

/**
 * nerdlabs: the short, friendly config.yml.
 *
 * configuration.txt is still loaded first and acts as the hidden "advanced" defaults; the values in
 * config.yml are then written over it. World maps (views, detail, 3D angles) are generated from config.yml
 * instead of the templates / worlds.txt, so one setting applies to every world.
 *
 * Set "advanced-mode: true" in config.yml to switch all of this off and use the stock files untouched.
 */
public class SimpleConfig {

    public static final String FILE_NAME = "config.yml";

    /** 3D compass directions in the order the rotate button walks through them. */
    private static final String[] ANGLES_1 = { "SE" };
    private static final String[] ANGLES_2 = { "SE", "NW" };
    private static final String[] ANGLES_4 = { "SE", "SW", "NW", "NE" };
    private static final String[] ANGLES_8 = { "SE", "S", "SW", "W", "NW", "N", "NE", "E" };

    private final ConfigurationNode cfg;
    private final boolean active;

    private SimpleConfig(ConfigurationNode cfg, boolean active) {
        this.cfg = cfg;
        this.active = active;
    }

    /** Loads (and on first run creates) config.yml in the dynmap folder. */
    public static SimpleConfig load(DynmapCore core, File dataDirectory) {
        File f = new File(dataDirectory, FILE_NAME);
        if (!core.createDefaultFileFromResource("/" + FILE_NAME, f)) {
            Log.warning("Could not create " + FILE_NAME + " - using configuration.txt only.");
            return new SimpleConfig(new ConfigurationNode(), false);
        }
        ConfigurationNode n = new ConfigurationNode(f);
        n.load();
        boolean advanced = n.getBoolean("advanced-mode", false);
        if (advanced) {
            Log.info(FILE_NAME + ": advanced-mode is on, using configuration.txt / worlds.txt / templates as-is.");
        } else {
            Log.info("Using " + FILE_NAME + " (detail: " + quality(n) + ", 3D angles: " + angles(n).length + ").");
        }
        return new SimpleConfig(n, !advanced);
    }

    public boolean isActive() {
        return active;
    }

    // ------------------------------------------------------------------ main settings

    /** Writes the simple settings over the values loaded from configuration.txt. */
    public void applyToMain(ConfigurationNode main) {
        if (!active) return;

        main.put("webserver-port", cfg.getInteger("web/port", 8123));
        String title = cfg.getString("web/title", "");
        if (title != null && title.length() > 0) main.put("webpage-title", title);

        main.put("hideores", cfg.getBoolean("world-display/hide-ores", true));

        boolean showFaces = cfg.getBoolean("players/show-faces", true);
        main.put("showplayerfacesinmenu", showFaces);

        String speed = cfg.getString("performance/render-speed", "normal").toLowerCase(Locale.ROOT);
        if (speed.equals("slow")) {
            main.put("tiles-rendered-at-once", 1);
            main.put("maxchunkspertick", 100);
            main.put("renderinterval", 2);
        } else if (speed.equals("fast")) {
            main.put("tiles-rendered-at-once", 4);
            main.put("maxchunkspertick", 400);
            main.put("renderinterval", 0.5);
        } else {
            main.put("tiles-rendered-at-once", 2);
            main.put("maxchunkspertick", 200);
            main.put("renderinterval", 1);
        }

        boolean showPlayers = cfg.getBoolean("players/show-players", true);
        boolean hideInvisible = cfg.getBoolean("players/hide-invisible", true);
        boolean hideSneaking = cfg.getBoolean("players/hide-sneaking", false);
        boolean showHealth = cfg.getBoolean("players/show-health", false);

        List<Map<String, Object>> comps = main.getMapList("components");
        if (comps == null) return;
        comps = new ArrayList<Map<String, Object>>(comps);
        for (Iterator<Map<String, Object>> it = comps.iterator(); it.hasNext(); ) {
            Map<String, Object> c = it.next();
            String cls = String.valueOf(c.get("class"));
            String type = String.valueOf(c.get("type"));
            if (cls.endsWith("InternalClientUpdateComponent") || cls.endsWith("JsonFileClientUpdateComponent")) {
                c.put("sendposition", showPlayers);
                c.put("sendhealth", showHealth);
                c.put("hideifsneaking", hideSneaking);
                c.put("hide-if-invisiblity-potion", hideInvisible);
                c.put("includehiddenplayers", !hideInvisible);
                c.put("allowwebchat", false);
            } else if (cls.endsWith("MarkersComponent")) {
                c.put("showspawn", cfg.getBoolean("world-display/show-spawn", true));
                c.put("enablesigns", cfg.getBoolean("world-display/show-signs", false));
                c.put("showworldborder", cfg.getBoolean("world-display/show-border", true));
            } else if (cls.endsWith("SimpleWebChatComponent")
                    || (cls.endsWith("ClientComponent") && (type.equals("chat") || type.equals("chatbox") || type.equals("chatballoon")))) {
                it.remove(); // web chat is not part of this build's simple mode
            } else if (cls.endsWith("ClientComponent") && type.equals("playermarkers")) {
                if (!showPlayers) {
                    it.remove();
                } else {
                    c.put("showplayerfaces", showFaces);
                    c.put("showplayerhealth", showHealth);
                }
            }
        }
        main.put("components", comps);
    }

    // ------------------------------------------------------------------ worlds and maps

    /**
     * The full world configuration for one world, or null when simple mode is off.
     * env is "normal", "nether" or "the_end" (anything else is treated as normal).
     */
    public ConfigurationNode worldConfig(String worldName, String env) {
        if (!active) return null;
        String e = env == null ? "normal" : env.toLowerCase(Locale.ROOT);
        if (!e.equals("nether") && !e.equals("the_end")) e = "normal";

        ConfigurationNode w = new ConfigurationNode();
        w.put("enabled", isWorldEnabled(worldName, e));
        w.put("extrazoomout", 2);

        String q = quality(cfg);
        String lighting = e.equals("nether") ? "nethershadows" : e.equals("the_end") ? "brightnight" : "shadows";
        String background = e.equals("nether") ? "#300806" : null;
        String base3d = e.equals("nether") ? "nether" : e.equals("the_end") ? "the_end" : "surface";
        String prefix3d = e.equals("nether") ? "nt" : e.equals("the_end") ? "st" : "t";

        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
        if (cfg.getBoolean("maps/views/flat", true)) {
            maps.add(map("flat", "Flat", "flat", "iso_S_90_" + q, "stdtexture", lighting, background));
        }
        if (cfg.getBoolean("maps/views/3d", true)) {
            String[] angles = angles(cfg);
            for (int i = 0; i < angles.length; i++) {
                String a = angles[i];
                // First angle keeps the stock name/prefix so existing tiles and links keep working.
                String name = (i == 0) ? base3d : base3d + "_" + a.toLowerCase(Locale.ROOT);
                String prefix = (i == 0) ? prefix3d : prefix3d + "_" + a.toLowerCase(Locale.ROOT);
                String title = angles.length == 1 ? "3D" : "3D (" + a + ")";
                Map<String, Object> m = map(name, title, prefix, "iso_" + a + "_30_" + q, "stdtexture", lighting, background);
                m.put("angle-group", base3d); // used by the web page's rotate button
                m.put("angle", a);
                maps.add(m);
            }
        }
        if (e.equals("normal") && cfg.getBoolean("maps/views/caves", false)) {
            maps.add(map("cave", "Caves", "ct", "iso_SE_60_" + q, "cave", "default", null));
        }
        w.put("maps", maps);
        return w;
    }

    private boolean isWorldEnabled(String worldName, String env) {
        boolean envOn;
        if (env.equals("nether")) envOn = cfg.getBoolean("maps/nether", true);
        else if (env.equals("the_end")) envOn = cfg.getBoolean("maps/end", false);
        else envOn = cfg.getBoolean("maps/overworld", true);
        if (!envOn) return false;

        List<String> only = cfg.getStrings("maps/worlds", null);
        if (only == null || only.isEmpty()) return true;
        for (String s : only) {
            if (s != null && s.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    private static Map<String, Object> map(String name, String title, String prefix, String perspective,
                                           String shader, String lighting, String background) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("class", "org.dynmap.hdmap.HDMap");
        m.put("name", name);
        m.put("title", title);
        m.put("prefix", prefix);
        m.put("perspective", perspective);
        m.put("shader", shader);
        m.put("lighting", lighting);
        if (background != null) m.put("background", background);
        m.put("mapzoomin", 0); // no blurry digital over-zoom past the real detail
        return m;
    }

    /** low / medium / high -> the perspective resolution suffix. */
    private static String quality(ConfigurationNode n) {
        String q = n.getString("maps/quality", "medium").toLowerCase(Locale.ROOT);
        if (q.equals("low")) return "lowres";
        if (q.equals("high")) return "hires";
        return "medres";
    }

    private static String[] angles(ConfigurationNode n) {
        int a = n.getInteger("maps/3d-angles", 4);
        if (a >= 8) return ANGLES_8;
        if (a >= 4) return ANGLES_4;
        if (a >= 2) return ANGLES_2;
        return ANGLES_1;
    }

    // ------------------------------------------------------------------ markers

    /** Creates the marker layer listed under markers: in config.yml (rebuilt on every start/reload). */
    public void applyMarkers(MarkerAPI api) {
        if (!active || api == null) return;
        ConfigurationNode mk = cfg.getNode("markers");
        if (mk == null) return;
        String id = "nerdlabs_simple";
        MarkerSet old = api.getMarkerSet(id);
        if (old != null) old.deleteMarkerSet();

        List<ConfigurationNode> list = mk.getNodes("list");
        if (list.isEmpty()) return;
        MarkerSet set = api.createMarkerSet(id, mk.getString("layer-name", "Games"), null, false);
        if (set == null) return;
        Map<String, Integer> used = new HashMap<String, Integer>();
        for (ConfigurationNode m : list) {
            String name = m.getString("name", "Marker");
            String world = m.getString("world", "world");
            String iconId = m.getString("icon", "flag");
            if (iconId.equals("flag")) iconId = "redflag";
            MarkerIcon icon = api.getMarkerIcon(iconId);
            if (icon == null) icon = api.getMarkerIcon(MarkerIcon.DEFAULT);
            String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
            Integer n = used.get(key);
            used.put(key, n == null ? 1 : n + 1);
            if (n != null) key = key + "_" + (n + 1);
            set.createMarker(key, name, world, m.getDouble("x", 0), m.getDouble("y", 64), m.getDouble("z", 0), icon, false);
        }
        Log.info("Added " + list.size() + " marker(s) from " + FILE_NAME + ".");
    }
}
