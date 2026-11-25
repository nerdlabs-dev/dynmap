package org.dynmap.bukkit;

import java.lang.reflect.Constructor;

import org.bukkit.Bukkit;
import org.dynmap.Log;
import org.dynmap.bukkit.helper.BukkitVersionHelper;

public class Helper {

	private static BukkitVersionHelper loadVersionHelper(String classname) {
		try {
			Class<?> c = Class.forName(classname);
			Constructor<?> cons = c.getConstructor();
			return (BukkitVersionHelper) cons.newInstance();
		} catch (Exception x) {
			Log.severe("Error loading " + classname, x);
			return null;
		}
	}
    public static final BukkitVersionHelper getHelper() {
        if (BukkitVersionHelper.helper == null) {
            String v = Bukkit.getServer().getVersion();
            Log.info("version=" + v);
            // nerdlabs build: Paper 26.x only. Newer drops usually keep working with the 26.x helper,
            // so try it anyway and say so in the log instead of refusing to start.
            if (!v.contains("(MC: 26.")) {
                Log.warning("This Dynmap build targets Paper 26.x; this server reports '" + v + "'. Trying the 26.x support anyway.");
            }
            BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v26x.BukkitVersionHelperSpigot26x");
        }
        return BukkitVersionHelper.helper;
    }

}
