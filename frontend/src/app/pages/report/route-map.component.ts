import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, NgZone, OnDestroy, OnInit, ViewChild, inject,
  signal
} from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import * as L from 'leaflet';
import { StreamDataDto, ZoneBandDto } from '../../api/report';
import {
  ColoredRoute, LegendEntry, METRIC_LABELS, RouteMetric, availableMetrics, colorRoute
} from './route-coloring';

/** Höchstzahl der Punkte, die beim Hover nach dem nächsten Punkt durchsucht werden. */
const HOVER_SAMPLES = 2500;
/** Maximaler Abstand (px) zwischen Maus und Route, damit der Hover greift. */
const HOVER_RADIUS = 18;

/** Fahrzeit als h:mm:ss. */
function clock(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

interface HoverInfo {
  km: string | null;
  time: string | null;
  watts: number | null;
  hr: number | null;
  speed: string | null;
}

/**
 * Fahrtverlauf auf einer OpenStreetMap-Karte, eingefärbt nach Leistung, HF, Tempo oder einfarbig.
 * Hover über die Route zeigt die Werte an diesem Punkt.
 */
@Component({
  selector: 'app-route-map',
  standalone: true,
  imports: [MatButtonToggleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="map-toolbar">
      <span class="label">Einfärbung</span>
      <mat-button-toggle-group hideSingleSelectionIndicator [value]="metric()" (change)="setMetric($event.value)"
                               aria-label="Einfärbung der Route">
        @for (m of metrics; track m) {
          <mat-button-toggle [value]="m">{{ labels[m] }}</mat-button-toggle>
        }
      </mat-button-toggle-group>
    </div>

    <div class="map-wrap">
      <div #mapEl class="map"></div>
      @if (hover(); as h) {
        <div class="hover-card">
          @if (h.km) { <span><b>{{ h.km }}</b> km</span> }
          @if (h.time) { <span>{{ h.time }}</span> }
          @if (h.watts != null) { <span class="power"><b>{{ h.watts }}</b> W</span> }
          @if (h.hr != null) { <span class="hr"><b>{{ h.hr }}</b> bpm</span> }
          @if (h.speed) { <span class="speed"><b>{{ h.speed }}</b> km/h</span> }
        </div>
      }
    </div>

    <div class="legend">
      @if (legendTitle()) { <span class="legend-title">{{ legendTitle() }}</span> }
      @for (e of legend(); track e.label) {
        <span class="legend-item"><span class="swatch" [style.background]="e.color"></span>{{ e.label }}</span>
      }
      <span class="legend-item"><span class="dot start"></span>Start</span>
      <span class="legend-item"><span class="dot end"></span>Ziel</span>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .map-toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
    .map-toolbar .label { color: var(--text-muted); font-size: 13px; }
    .map-wrap { position: relative; }
    .map {
      height: 520px; border-radius: 12px; overflow: hidden;
      border: 1px solid var(--border); background: var(--surface-3);
    }
    @media (max-width: 720px) { .map { height: 380px; } }
    .hover-card {
      position: absolute; top: 10px; right: 10px; z-index: 500;
      display: flex; gap: 12px; flex-wrap: wrap;
      padding: 8px 12px; border-radius: 8px; font-size: 13px;
      background: color-mix(in srgb, var(--surface) 92%, transparent);
      border: 1px solid var(--border-strong); color: var(--text); pointer-events: none;
    }
    .hover-card .power b { color: var(--c-power); }
    .hover-card .hr b { color: var(--c-hr); }
    .hover-card .speed b { color: var(--c-speed); }
    .legend {
      display: flex; flex-wrap: wrap; align-items: center; gap: 6px 16px;
      margin-top: 12px; font-size: 12.5px; color: var(--text-muted);
    }
    .legend-title { color: var(--text); font-weight: 600; }
    .legend-item { display: inline-flex; align-items: center; gap: 6px; }
    .swatch { width: 14px; height: 6px; border-radius: 3px; }
    .dot { width: 10px; height: 10px; border-radius: 50%; border: 2px solid #fff; }
    .dot.start { background: #22c55e; }
    .dot.end { background: #ef4444; }
  `]
})
export class RouteMapComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input({ required: true }) streams!: StreamDataDto;
  @Input() powerBands: ZoneBandDto[] = [];
  @Input() hrBands: ZoneBandDto[] = [];

  @ViewChild('mapEl', { static: true }) private mapEl!: ElementRef<HTMLDivElement>;

  private readonly zone = inject(NgZone);

  readonly labels = METRIC_LABELS;
  metrics: RouteMetric[] = [];
  readonly metric = signal<RouteMetric>('single');
  readonly legend = signal<LegendEntry[]>([]);
  readonly legendTitle = signal<string | null>(null);
  readonly hover = signal<HoverInfo | null>(null);

  private map: L.Map | null = null;
  private routeLayer: L.LayerGroup | null = null;
  private hoverMarker: L.CircleMarker | null = null;
  private route: ColoredRoute | null = null;
  private resizeObserver: ResizeObserver | null = null;

  ngOnInit(): void {
    this.metrics = availableMetrics(this.streams, this.powerBands, this.hrBands);
    this.metric.set(this.metrics.includes('power') ? 'power' : this.metrics.includes('speed') ? 'speed' : 'single');
  }

  ngAfterViewInit(): void {
    // Leaflet-Events außerhalb von Angular, sonst löst jede Mausbewegung eine Change-Detection aus
    this.zone.runOutsideAngular(() => {
      this.map = L.map(this.mapEl.nativeElement, { zoomControl: true, attributionControl: true });
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>-Mitwirkende'
      }).addTo(this.map);
      this.routeLayer = L.layerGroup().addTo(this.map);
      this.map.on('mousemove', e => this.onMouseMove(e));
      this.map.on('mouseout', () => this.clearHover());

      // Der Tab-Inhalt hat beim Erzeugen evtl. noch keine Größe
      this.resizeObserver = new ResizeObserver(() => this.map?.invalidateSize());
      this.resizeObserver.observe(this.mapEl.nativeElement);
    });

    this.draw(true);
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.map?.remove();
    this.map = null;
  }

  setMetric(metric: RouteMetric): void {
    this.metric.set(metric);
    this.draw(false);
  }

  private draw(fit: boolean): void {
    if (!this.map || !this.routeLayer) return;
    const route = colorRoute(this.streams, this.metric(), this.powerBands, this.hrBands);
    this.route = route;
    this.legend.set(route.legend);
    this.legendTitle.set(route.legendTitle);
    this.routeLayer.clearLayers();
    this.hoverMarker = null;
    if (route.points.length < 2) return;

    const pts = route.points;
    const latLngs = pts.map(p => L.latLng(p.lat, p.lng));

    // Umriss in einem Zug, darüber die farbigen Abschnitte
    L.polyline(latLngs, { color: '#141821', weight: 8, opacity: 0.6, interactive: false })
      .addTo(this.routeLayer);
    let start = 0;
    for (let i = 1; i <= pts.length; i++) {
      if (i < pts.length && pts[i].color === pts[start].color) continue;
      const from = Math.max(0, start - 1);
      L.polyline(latLngs.slice(from, i), {
        color: route.colors[pts[start].color], weight: 4.5, opacity: 1,
        lineCap: 'round', lineJoin: 'round', interactive: false
      }).addTo(this.routeLayer);
      start = i;
    }

    const marker = (p: L.LatLng, color: string) => L.circleMarker(p, {
      radius: 7, color: '#fff', weight: 2.5, fillColor: color, fillOpacity: 1, interactive: false
    });
    marker(latLngs[latLngs.length - 1], '#ef4444').addTo(this.routeLayer);
    marker(latLngs[0], '#22c55e').addTo(this.routeLayer);

    if (fit) this.map.fitBounds(L.latLngBounds(latLngs), { padding: [30, 30] });
  }

  // ---- Hover ------------------------------------------------------------------------------

  private onMouseMove(e: L.LeafletMouseEvent): void {
    const map = this.map;
    const route = this.route;
    if (!map || !route || !this.routeLayer) return;

    const pts = route.points;
    const stride = Math.max(1, Math.ceil(pts.length / HOVER_SAMPLES));
    let best = -1;
    let bestDist = HOVER_RADIUS * HOVER_RADIUS;
    for (let i = 0; i < pts.length; i += stride) {
      const p = map.latLngToContainerPoint([pts[i].lat, pts[i].lng]);
      const d = (p.x - e.containerPoint.x) ** 2 + (p.y - e.containerPoint.y) ** 2;
      if (d < bestDist) {
        bestDist = d;
        best = i;
      }
    }
    if (best < 0) {
      this.clearHover();
      return;
    }

    const point = pts[best];
    const latLng = L.latLng(point.lat, point.lng);
    if (!this.hoverMarker) {
      this.hoverMarker = L.circleMarker(latLng, {
        radius: 6, color: '#fff', weight: 2, fillColor: '#141821', fillOpacity: 1, interactive: false
      }).addTo(this.routeLayer);
    } else {
      this.hoverMarker.setLatLng(latLng);
    }

    const s = this.streams;
    const i = point.index;
    const distance = s.distance?.[i];
    const time = s.time?.[i];
    const velocity = s.velocity?.[i];
    this.zone.run(() => this.hover.set({
      km: distance != null ? (distance / 1000).toFixed(1).replace('.', ',') : null,
      time: time != null ? clock(time) : null,
      watts: s.watts?.[i] ?? null,
      hr: s.heartrate?.[i] ?? null,
      speed: velocity != null ? (velocity * 3.6).toFixed(1).replace('.', ',') : null
    }));
  }

  private clearHover(): void {
    if (this.hoverMarker) {
      this.hoverMarker.remove();
      this.hoverMarker = null;
    }
    if (this.hover()) this.zone.run(() => this.hover.set(null));
  }
}
