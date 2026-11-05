package com.cycling.report.map;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Map;

/** Liefert Kartenkacheln (256 x 256, Web-Mercator) fuer das Kartenbild. */
public interface TileSource {

    /** Kachel-Koordinaten; {@code x} ist bereits auf 0 .. 2^z-1 umgebrochen. */
    record Tile(int z, int x, int y) {
    }

    /**
     * Laedt die Kacheln. Fehlende Kacheln fehlen in der Map; scheitert alles, ist sie leer (das Bild bekommt dann
     * einen neutralen Hintergrund).
     */
    Map<Tile, BufferedImage> fetch(Collection<Tile> tiles);

    /** Quellenangabe, die ins Bild gehoert; null = keine. */
    String attribution();

    /** Keine Kacheln (Kachel-URL leer): nur die Route auf neutralem Grund. */
    static TileSource none() {
        return new TileSource() {
            @Override
            public Map<Tile, BufferedImage> fetch(Collection<Tile> tiles) {
                return Map.of();
            }

            @Override
            public String attribution() {
                return null;
            }
        };
    }
}
