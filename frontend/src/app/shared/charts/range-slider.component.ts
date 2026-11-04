import { ChangeDetectionStrategy, Component, ElementRef, computed, input, viewChild } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ZoomGroup } from './zoom-group';

export interface SparkPoint {
  x: number;
  y: number;
}

type DragMode = 'min' | 'max' | 'move';

/**
 * Bereichs-Schieber unter einem oder mehreren Charts: Mini-Vorschau der Daten mit einem
 * verschiebbaren Ausschnitt. Funktioniert mit Maus und Touch gleich.
 */
@Component({
  selector: 'app-range-slider',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row">
      <div class="track" #track
           (pointerdown)="onTrackDown($event)">
        <svg class="spark" viewBox="0 0 1000 100" preserveAspectRatio="none" aria-hidden="true">
          <path [attr.d]="sparkPath()" [attr.stroke]="color()" [attr.fill]="color()" />
        </svg>
        <div class="shade" [style.width.%]="sel().left"></div>
        <div class="shade right" [style.width.%]="100 - sel().left - sel().width"></div>
        <div class="window"
             [style.left.%]="sel().left" [style.width.%]="sel().width"
             (pointerdown)="startDrag($event, 'move')">
          <span class="handle start" (pointerdown)="startDrag($event, 'min')"
                role="slider" aria-label="Beginn des Ausschnitts"></span>
          <span class="handle end" (pointerdown)="startDrag($event, 'max')"
                role="slider" aria-label="Ende des Ausschnitts"></span>
        </div>
      </div>
      <button mat-icon-button class="reset"
              [disabled]="!group().zoomed()"
              (click)="group().reset()"
              matTooltip="Ganzen Bereich zeigen">
        <mat-icon>zoom_out_map</mat-icon>
      </button>
    </div>
    <p class="hint">
      Ausschnitt mit dem Schieber wählen<span class="desktop-only"> oder im Chart mit der Maus aufziehen</span>
    </p>
  `,
  styles: [`
    :host { display: block; margin-top: 10px; }
    .row { display: flex; align-items: center; gap: 6px; }
    .track {
      position: relative;
      flex: 1;
      height: 40px;
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      overflow: hidden;
      touch-action: none;
      cursor: crosshair;
      user-select: none;
    }
    .spark {
      position: absolute; inset: 4px 0;
      width: 100%; height: calc(100% - 8px);
      path { stroke-width: 1.5; fill-opacity: 0.15; vector-effect: non-scaling-stroke; }
    }
    .shade {
      position: absolute; top: 0; bottom: 0; left: 0;
      background: rgba(13, 16, 22, 0.6);
      pointer-events: none;
      &.right { left: auto; right: 0; }
    }
    .window {
      position: absolute; top: 0; bottom: 0;
      border: 1.5px solid var(--accent);
      border-radius: 6px;
      background: var(--accent-soft);
      cursor: grab;
      min-width: 6px;
      &:active { cursor: grabbing; }
    }
    .handle {
      position: absolute; top: 50%;
      width: 14px; height: 26px;
      margin-top: -13px;
      border-radius: 4px;
      background: var(--accent);
      cursor: ew-resize;
      &::after {
        content: ''; position: absolute; left: 5px; top: 7px;
        width: 4px; height: 12px;
        border-left: 1px solid rgba(0,0,0,.45); border-right: 1px solid rgba(0,0,0,.45);
      }
      &.start { left: -8px; }
      &.end { right: -8px; }
      // grössere Trefferfläche für Finger
      &::before { content: ''; position: absolute; inset: -10px -12px; }
    }
    .reset { color: var(--text-muted); }
    .hint { font-size: 11px; color: var(--text-faint); margin: 4px 0 0 2px; }
    @media (hover: none), (max-width: 720px) {
      .desktop-only { display: none; }
      .track { height: 44px; }
    }
  `]
})
export class RangeSliderComponent {
  readonly group = input.required<ZoomGroup>();
  readonly points = input<SparkPoint[]>([]);
  readonly color = input<string>('#8d96a5');

  private readonly track = viewChild.required<ElementRef<HTMLElement>>('track');

  /** Ausschnitt in Prozent der Spurbreite. */
  readonly sel = computed(() => {
    const g = this.group();
    const b = g.bounds();
    const r = g.range();
    const left = this.toFrac(r.min, b.min, b.max) * 100;
    const right = this.toFrac(r.max, b.min, b.max) * 100;
    return { left, width: Math.max(0, right - left) };
  });

  readonly sparkPath = computed(() => {
    const pts = this.points();
    const b = this.group().bounds();
    if (pts.length < 2) return '';
    const sampled = this.sample(pts, 300);
    let yMin = Infinity, yMax = -Infinity;
    for (const p of sampled) {
      if (p.y < yMin) yMin = p.y;
      if (p.y > yMax) yMax = p.y;
    }
    const span = yMax - yMin || 1;
    const coords = sampled.map(p => {
      const x = this.toFrac(p.x, b.min, b.max) * 1000;
      const y = 100 - ((p.y - yMin) / span) * 96 - 2;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    });
    const firstX = coords[0].split(',')[0];
    const lastX = coords[coords.length - 1].split(',')[0];
    return `M${firstX},100 L${coords.join(' L')} L${lastX},100 Z`;
  });

  private drag: { mode: DragMode; startX: number; startMin: number; startMax: number; width: number } | null = null;

  startDrag(event: PointerEvent, mode: DragMode): void {
    event.stopPropagation();
    event.preventDefault();
    const el = this.track().nativeElement;
    const b = this.group().bounds();
    const r = this.group().range();
    this.drag = {
      mode,
      startX: event.clientX,
      startMin: this.toFrac(r.min, b.min, b.max),
      startMax: this.toFrac(r.max, b.min, b.max),
      width: el.getBoundingClientRect().width || 1
    };
    el.setPointerCapture(event.pointerId);
    el.onpointermove = (e) => this.onMove(e);
    el.onpointerup = el.onpointercancel = (e) => this.endDrag(e);
  }

  /** Klick auf die Spur ausserhalb des Fensters: Fenster dorthin zentrieren. */
  onTrackDown(event: PointerEvent): void {
    const el = this.track().nativeElement;
    const rect = el.getBoundingClientRect();
    const frac = (event.clientX - rect.left) / (rect.width || 1);
    const b = this.group().bounds();
    const r = this.group().range();
    const lo = this.toFrac(r.min, b.min, b.max);
    const hi = this.toFrac(r.max, b.min, b.max);
    const half = (hi - lo) / 2;
    let start = frac - half;
    start = Math.max(0, Math.min(1 - (hi - lo), start));
    this.setFrac(start, start + (hi - lo));
    this.startDrag(event, 'move');
  }

  private onMove(event: PointerEvent): void {
    if (!this.drag) return;
    const d = this.drag;
    const delta = (event.clientX - d.startX) / d.width;
    const minGap = 0.01;
    if (d.mode === 'min') {
      this.setFrac(Math.max(0, Math.min(d.startMin + delta, d.startMax - minGap)), d.startMax);
    } else if (d.mode === 'max') {
      this.setFrac(d.startMin, Math.min(1, Math.max(d.startMax + delta, d.startMin + minGap)));
    } else {
      const span = d.startMax - d.startMin;
      const start = Math.max(0, Math.min(1 - span, d.startMin + delta));
      this.setFrac(start, start + span);
    }
  }

  private endDrag(event: PointerEvent): void {
    const el = this.track().nativeElement;
    if (el.hasPointerCapture(event.pointerId)) el.releasePointerCapture(event.pointerId);
    el.onpointermove = el.onpointerup = el.onpointercancel = null;
    this.drag = null;
  }

  private setFrac(lo: number, hi: number): void {
    const b = this.group().bounds();
    this.group().setRange(this.fromFrac(lo, b.min, b.max), this.fromFrac(hi, b.min, b.max));
  }

  private toFrac(v: number, min: number, max: number): number {
    if (this.group().log) {
      const l = Math.log(Math.max(v, 1e-9)), a = Math.log(min), z = Math.log(max);
      return z > a ? (l - a) / (z - a) : 0;
    }
    return max > min ? (v - min) / (max - min) : 0;
  }

  private fromFrac(f: number, min: number, max: number): number {
    if (this.group().log) {
      const a = Math.log(min), z = Math.log(max);
      return Math.exp(a + f * (z - a));
    }
    return min + f * (max - min);
  }

  private sample(pts: SparkPoint[], max: number): SparkPoint[] {
    if (pts.length <= max) return pts;
    const step = pts.length / max;
    const out: SparkPoint[] = [];
    for (let i = 0; i < max; i++) {
      const from = Math.floor(i * step);
      const to = Math.max(from + 1, Math.floor((i + 1) * step));
      let sx = 0, sy = 0, n = 0;
      for (let j = from; j < to && j < pts.length; j++) {
        sx += pts[j].x; sy += pts[j].y; n++;
      }
      if (n) out.push({ x: sx / n, y: sy / n });
    }
    return out;
  }
}
