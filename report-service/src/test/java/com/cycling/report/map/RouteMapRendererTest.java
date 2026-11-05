package com.cycling.report.map;

import com.cycling.report.ReportContent.Streams;
import com.cycling.report.ReportContent.ZoneBand;
import com.cycling.report.map.TileSource.Tile;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteMapRendererTest {

    private static final List<ZoneBand> BANDS = List.of(new ZoneBand("Endurance", 0, null, "#42a5f5"));

    /** Liefert jede Kachel einfarbig magenta und merkt sich die Anfragen. */
    private static final class FakeTiles implements TileSource {
        final List<Tile> requested = new ArrayList<>();

        @Override
        public Map<Tile, BufferedImage> fetch(Collection<Tile> tiles) {
            requested.addAll(tiles);
            Map<Tile, BufferedImage> out = new HashMap<>();
            for (Tile t : tiles) {
                BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.MAGENTA);
                g.fillRect(0, 0, 256, 256);
                g.dispose();
                out.put(t, img);
            }
            return out;
        }

        @Override
        public String attribution() {
            return "Test";
        }
    }

    /** Runde um die Hamburger Alster, ca. 4 x 3 km. */
    private static Streams loop() {
        List<Double> lat = new ArrayList<>();
        List<Double> lng = new ArrayList<>();
        for (int i = 0; i < 360; i++) {
            double a = Math.toRadians(i);
            lat.add(53.565 + 0.014 * Math.sin(a));
            lng.add(10.005 + 0.03 * Math.cos(a));
        }
        return new Streams(Collections.nCopies(360, 0.0), Collections.nCopies(360, 180), List.of(), List.of(),
                List.of(), List.of(), List.of(), lat, lng);
    }

    private static BufferedImage decode(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void rendersRouteOnTilesAsPng() throws Exception {
        FakeTiles tiles = new FakeTiles();

        byte[] png = new RouteMapRenderer(tiles).render(loop(), BANDS);

        BufferedImage img = decode(png);
        assertThat(img.getWidth()).isEqualTo(RouteMapRenderer.WIDTH);
        assertThat(img.getHeight()).isEqualTo(RouteMapRenderer.HEIGHT);
        assertThat(tiles.requested).isNotEmpty().allSatisfy(t -> assertThat(t.z()).isBetween(10, 17));
        // Rechte obere Ecke: Kachel (aufgehellt), nicht neutraler Hintergrund
        Color corner = new Color(img.getRGB(RouteMapRenderer.WIDTH - 5, 5));
        assertThat(corner.getRed()).isGreaterThan(200);
        assertThat(corner.getGreen()).isLessThan(150);
        // Route in Zonenfarbe irgendwo im Bild
        assertThat(containsColor(img, new Color(0x42a5f5))).isTrue();
    }

    @Test
    void withoutTilesTheRouteIsDrawnOnNeutralBackground() throws Exception {
        byte[] png = new RouteMapRenderer(TileSource.none()).render(loop(), BANDS);

        BufferedImage img = decode(png);
        assertThat(new Color(img.getRGB(RouteMapRenderer.WIDTH - 5, 5))).isEqualTo(new Color(0xe8ecf1));
        assertThat(containsColor(img, new Color(0x42a5f5))).isTrue();
    }

    @Test
    void noImageWithoutGps() {
        Streams indoor = new Streams(List.of(0.0), List.of(200), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());

        assertThat(new RouteMapRenderer(new FakeTiles()).render(indoor, BANDS)).isNull();
    }

    private static boolean containsColor(BufferedImage img, Color c) {
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                if ((img.getRGB(x, y) & 0xffffff) == (c.getRGB() & 0xffffff)) return true;
            }
        }
        return false;
    }
}
