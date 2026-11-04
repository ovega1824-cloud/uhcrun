package com.uhcrun;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Opciones de la partida. Se guardan en config/uhcrun.properties y se cambian con /uhc config. */
public final class UhcSettings {
    /** nombre -> {valor por defecto, mínimo, máximo} */
    private static final Map<String, int[]> DEFS = new LinkedHashMap<>();
    private static final Map<String, Integer> VALUES = new HashMap<>();

    static {
        def("borde_inicial", 2000, 50, 50000);          // diámetro inicial del borde
        def("borde_final", 50, 10, 5000);               // diámetro mínimo al que llega
        def("minutos_pvp", 10, 0, 240);                 // minutos hasta que se activa el PvP
        def("minutos_primer_cierre", 20, 0, 480);       // minutos hasta el primer cierre
        def("minutos_entre_cierres", 5, 1, 120);        // minutos entre cierres
        def("segundos_cierre", 90, 5, 3600);            // lo que tarda el borde en cerrarse
        def("porcentaje_cierre", 60, 10, 95);           // el borde queda en este % del anterior
        def("danio_borde", 2, 1, 20);                   // daño por bloque fuera del borde
        def("prob_manzana", 5, 0, 100);                 // % de manzana extra al romper hojas
        def("pedernal", 1, 0, 1);                       // 1 = la grava suelta pedernal extra
        def("cutclean", 1, 0, 1);                       // 1 = minerales y comida cocinados
        def("cabeza_dorada", 1, 0, 1);                  // 1 = al morir se suelta la cabeza
        def("dispersar", 1, 0, 1);                      // 1 = dispersa a los jugadores al iniciar
        def("solo_en_partida", 0, 0, 1);                // 1 = cutclean/manzanas solo con partida activa
    }

    private UhcSettings() {}

    private static void def(String name, int def, int min, int max) {
        DEFS.put(name, new int[]{def, min, max});
        VALUES.put(name, def);
    }

    public static int get(String name) {
        return VALUES.getOrDefault(name, 0);
    }

    public static List<String> names() {
        return new ArrayList<>(DEFS.keySet());
    }

    public static int[] range(String name) {
        return DEFS.get(name);
    }

    /** @return null si todo bien, o un mensaje de error. */
    public static String set(String name, int value) {
        int[] d = DEFS.get(name);
        if (d == null) return "Opción desconocida: " + name;
        if (value < d[1] || value > d[2]) return "El valor de " + name + " debe estar entre " + d[1] + " y " + d[2];
        VALUES.put(name, value);
        save();
        return null;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("uhcrun.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        for (Map.Entry<String, int[]> e : DEFS.entrySet()) {
            String s = p.getProperty(e.getKey());
            if (s == null) continue;
            try {
                int v = Integer.parseInt(s.trim());
                int[] d = e.getValue();
                if (v >= d[1] && v <= d[2]) VALUES.put(e.getKey(), v);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static void save() {
        Properties p = new Properties();
        for (String k : DEFS.keySet()) p.setProperty(k, String.valueOf(VALUES.get(k)));
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "UHC Run - opciones (tambien se cambian con /uhc config <opcion> <valor>)");
        } catch (IOException ignored) {
        }
    }
}
