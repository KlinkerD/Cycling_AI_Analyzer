package com.cycling.report.map;

import com.cycling.report.ReportContent.Streams;
import com.cycling.report.ReportContent.ZoneBand;
import com.cycling.report.map.RouteColoring.ColoredRoute;
import com.cycling.report.map.RouteColoring.LegendEntry;
import com.cycling.report.map.RouteColoring.Point;
import com.cycling.report.map.TileSource.Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Zeichnet den Fahrtverlauf als PNG fuer Mail und Discord: Kartenkacheln (Web-Mercator) als Hintergrund, darauf
 * die eingefaerbte Route mit Start und Ziel, unten links die Legende, unten rechts die Quellenangabe. Fehlen die
 * Kacheln, liegt die Route auf neutralem Grund.
 */
public class RouteMapRenderer {

    private static final Logger log = LoggerFactory.getLogger(RouteMapRenderer.class);

    static final int WIDTH = 800;
    static final int HEIGHT = 500;
    private static final int TILE_SIZE = 256;
    private static final int PADDING = 40;
    private static final int MAX_ZOOM = 17;
    private static final Color BACKGROUND = new Color(0xe8ecf1);

    private final TileSource tiles;

    public RouteMapRenderer(TileSource tiles) {
        this.tiles = tiles;
    }

    /** null ohne GPS-Daten oder wenn das Zeichnen scheitert. */
    public byte[] render(Streams streams, List<ZoneBand> powerBands) {
        ColoredRoute route = RouteColoring.color(streams, powerBands);
        if (route == null) return null;
        try {
            return png(draw(route));
        } catch (Exception | Error e) {
            // Error: z. B. fehlende Schriften im Container (InternalError/UnsatisfiedLinkError aus AWT)
            log.warn("Kartenbild konnte nicht gezeichnet werden: {}", e.toString());
            return null;
        }
    }

    BufferedImage draw(ColoredRoute route) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            // Links Platz fuer die Legende lassen, damit sie die Route nicht verdeckt
            int legendWidth = legendWidth(g, route);
            Viewport view = Viewport.fit(route.points(), WIDTH, HEIGHT, legendWidth + 20 + PADDING / 2, PADDING);

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            g.setColor(BACKGROUND);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            boolean hasTiles = drawTiles(g, view);
            if (hasTiles) {
                // Karte leicht aufhellen, damit die Route heraussticht
                g.setColor(new Color(255, 255, 255, 70));
                g.fillRect(0, 0, WIDTH, HEIGHT);
            }

            drawRoute(g, view, route);
            drawLegend(g, route);
            if (hasTiles) drawAttribution(g, tiles.attribution());
        } finally {
            g.dispose();
        }
        return img;
    }

    // ---- Kacheln ------------------------------------------------------------------------------

    private boolean drawTiles(Graphics2D g, Viewport view) {
        int n = 1 << view.zoom;
        int x0 = (int) Math.floor(view.left / TILE_SIZE);
        int x1 = (int) Math.floor((view.left + WIDTH - 1) / TILE_SIZE);
        int y0 = Math.max(0, (int) Math.floor(view.top / TILE_SIZE));
        int y1 = Math.min(n - 1, (int) Math.floor((view.top + HEIGHT - 1) / TILE_SIZE));

        List<Tile> needed = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                needed.add(new Tile(view.zoom, Math.floorMod(x, n), y));
            }
        }
        Map<Tile, BufferedImage> loaded = tiles.fetch(needed);
        if (loaded.isEmpty()) return false;

        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                BufferedImage tile = loaded.get(new Tile(view.zoom, Math.floorMod(x, n), y));
                if (tile == null) continue;
                int px = (int) Math.round(x * TILE_SIZE - view.left);
                int py = (int) Math.round(y * TILE_SIZE - view.top);
                g.drawImage(tile, px, py, TILE_SIZE, TILE_SIZE, null);
            }
        }
        return true;
    }

    // ---- Route --------------------------------------------------------------------------------

    private static void drawRoute(Graphics2D g, Viewport view, ColoredRoute route) {
        List<Point> pts = route.points();

        // Umriss in einem Zug, darunter
        Path2D outline = new Path2D.Double();
        for (int i = 0; i < pts.size(); i++) {
            double x = view.x(pts.get(i).lng());
            double y = view.y(pts.get(i).lat());
            if (i == 0) outline.moveTo(x, y);
            else outline.lineTo(x, y);
        }
        g.setStroke(new BasicStroke(7.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(20, 24, 33, 150));
        g.draw(outline);

        // Farbige Abschnitte: aufeinanderfolgende Punkte gleicher Farbe als ein Pfad
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int start = 0;
        for (int i = 1; i <= pts.size(); i++) {
            boolean end = i == pts.size() || pts.get(i).color() != pts.get(start).color();
            if (!end) continue;
            Path2D segment = new Path2D.Double();
            // an den vorherigen Abschnitt anschliessen, damit keine Luecke entsteht
            int from = Math.max(0, start - 1);
            segment.moveTo(view.x(pts.get(from).lng()), view.y(pts.get(from).lat()));
            for (int j = from + 1; j < i; j++) {
                segment.lineTo(view.x(pts.get(j).lng()), view.y(pts.get(j).lat()));
            }
            g.setColor(Color.decode(route.colors().get(pts.get(start).color())));
            g.draw(segment);
            start = i;
        }

        Point first = pts.getFirst();
        Point last = pts.getLast();
        drawMarker(g, view.x(last.lng()), view.y(last.lat()), new Color(0xef4444));
        drawMarker(g, view.x(first.lng()), view.y(first.lat()), new Color(0x22c55e));
    }

    private static void drawMarker(Graphics2D g, double x, double y, Color fill) {
        double r = 7;
        Ellipse2D dot = new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
        g.setColor(fill);
        g.fill(dot);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(Color.WHITE);
        g.draw(dot);
    }

    // ---- Legende und Quelle -------------------------------------------------------------------

    private static final Font LEGEND_TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font LEGEND_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final int LEGEND_PAD = 8;
    private static final int SWATCH = 10;

    private static List<LegendEntry> legendEntries(ColoredRoute route) {
        List<LegendEntry> entries = new ArrayList<>(route.legend());
        entries.add(new LegendEntry("#22c55e", "Start"));
        entries.add(new LegendEntry("#ef4444", "Ziel"));
        return entries;
    }

    /** Breite des Legendenkastens in Pixeln. */
    private static int legendWidth(Graphics2D g, ColoredRoute route) {
        FontMetrics fm = g.getFontMetrics(LEGEND_FONT);
        FontMetrics tfm = g.getFontMetrics(LEGEND_TITLE_FONT);
        int width = route.legendTitle() != null ? tfm.stringWidth(route.legendTitle()) : 0;
        for (LegendEntry e : legendEntries(route)) width = Math.max(width, SWATCH + 6 + fm.stringWidth(e.label()));
        return width + 2 * LEGEND_PAD;
    }

    private static void drawLegend(Graphics2D g, ColoredRoute route) {
        List<LegendEntry> entries = legendEntries(route);
        Font titleFont = LEGEND_TITLE_FONT;
        Font font = LEGEND_FONT;
        FontMetrics fm = g.getFontMetrics(font);
        FontMetrics tfm = g.getFontMetrics(titleFont);

        int pad = LEGEND_PAD;
        int swatch = SWATCH;
        int lineH = Math.max(fm.getHeight(), swatch + 3);
        int titleH = route.legendTitle() != null ? tfm.getHeight() + 2 : 0;
        int boxW = legendWidth(g, route);
        int boxH = titleH + entries.size() * lineH + 2 * pad - 2;
        int bx = 10;
        int by = HEIGHT - boxH - 10;

        g.setColor(new Color(255, 255, 255, 225));
        g.fill(new RoundRectangle2D.Double(bx, by, boxW, boxH, 10, 10));
        g.setColor(new Color(0, 0, 0, 40));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(bx, by, boxW, boxH, 10, 10));

        int y = by + pad;
        if (route.legendTitle() != null) {
            g.setFont(titleFont);
            g.setColor(new Color(0x1f2937));
            g.drawString(route.legendTitle(), bx + pad, y + tfm.getAscent());
            y += titleH;
        }
        g.setFont(font);
        for (LegendEntry e : entries) {
            int cy = y + lineH / 2;
            g.setColor(Color.decode(e.color()));
            g.fillRoundRect(bx + pad, cy - swatch / 2, swatch, swatch, 4, 4);
            g.setColor(new Color(0x374151));
            g.drawString(e.label(), bx + pad + swatch + 6, cy + fm.getAscent() / 2 - 1);
            y += lineH;
        }
    }

    private static void drawAttribution(Graphics2D g, String text) {
        if (text == null) return;
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
        FontMetrics fm = g.getFontMetrics(font);
        int w = fm.stringWidth(text) + 8;
        int h = fm.getHeight() + 2;
        g.setColor(new Color(255, 255, 255, 200));
        g.fillRect(WIDTH - w, HEIGHT - h, w, h);
        g.setFont(font);
        g.setColor(new Color(0x374151));
        g.drawString(text, WIDTH - w + 4, HEIGHT - h + 1 + fm.getAscent());
    }

    private static byte[] png(BufferedImage img) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    // ---- Projektion ---------------------------------------------------------------------------

    /** Bildausschnitt in Web-Mercator-Pixeln einer Zoomstufe; {@code left/top} = Pixel der linken oberen Ecke. */
    record Viewport(int zoom, double left, double top) {

        /** Route so gross wie moeglich in den Bereich rechts von {@code padLeft}, sonst {@code padding} Rand. */
        static Viewport fit(List<Point> points, int width, int height, int padLeft, int padding) {
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
            for (Point p : points) {
                minLat = Math.min(minLat, p.lat());
                maxLat = Math.max(maxLat, p.lat());
                minLng = Math.min(minLng, p.lng());
                maxLng = Math.max(maxLng, p.lng());
            }
            int zoom = MAX_ZOOM;
            for (; zoom > 1; zoom--) {
                double w = worldX(maxLng, zoom) - worldX(minLng, zoom);
                double h = worldY(minLat, zoom) - worldY(maxLat, zoom);
                if (w <= width - padLeft - padding && h <= height - 2 * padding) break;
            }
            double cx = (worldX(minLng, zoom) + worldX(maxLng, zoom)) / 2;
            double cy = (worldY(minLat, zoom) + worldY(maxLat, zoom)) / 2;
            double areaCenterX = padLeft + (width - padLeft - padding) / 2.0;
            return new Viewport(zoom, cx - areaCenterX, cy - height / 2.0);
        }

        double x(double lng) {
            return worldX(lng, zoom) - left;
        }

        double y(double lat) {
            return worldY(lat, zoom) - top;
        }

        static double worldX(double lng, int zoom) {
            return (lng + 180.0) / 360.0 * TILE_SIZE * (1 << zoom);
        }

        static double worldY(double lat, int zoom) {
            double rad = Math.toRadians(lat);
            double merc = Math.log(Math.tan(rad) + 1 / Math.cos(rad));
            return (1 - merc / Math.PI) / 2 * TILE_SIZE * (1 << zoom);
        }
    }
}
