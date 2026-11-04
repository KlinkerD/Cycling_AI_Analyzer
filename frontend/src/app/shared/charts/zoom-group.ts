import { computed, signal } from '@angular/core';
import { Chart, ChartType, Plugin } from 'chart.js';
import { alpha, token } from '../theme';

export interface Range {
  min: number;
  max: number;
}

declare module 'chart.js' {
  interface PluginOptionsByType<TType extends ChartType> {
    zoomGroup?: { group?: ZoomGroup };
  }
}

type ZoomableChart = Chart & { zoomScale(id: string, range: Range, mode?: string): void };

/**
 * Gemeinsamer X-Ausschnitt für einen oder mehrere Charts und einen Bereichs-Schieber.
 * Alle verbundenen Charts zeigen immer denselben Bereich.
 */
export class ZoomGroup {
  readonly bounds = signal<Range>({ min: 0, max: 1 });
  readonly range = signal<Range>({ min: 0, max: 1 });
  readonly zoomed = computed(() => {
    const b = this.bounds();
    const r = this.range();
    const eps = (b.max - b.min) * 1e-6;
    return r.min > b.min + eps || r.max < b.max - eps;
  });

  private readonly charts = new Set<Chart>();

  /** @param log Achse ist logarithmisch (Power-Curve); der Schieber rechnet dann in log. */
  constructor(readonly log = false) {}

  setBounds(min: number, max: number): void {
    if (!isFinite(min) || !isFinite(max) || max <= min) return;
    this.bounds.set({ min, max });
    this.apply({ min, max });
  }

  setRange(min: number, max: number): void {
    const b = this.bounds();
    let lo = Math.max(b.min, Math.min(min, max));
    let hi = Math.min(b.max, Math.max(min, max));
    const minSpan = this.log ? 0 : (b.max - b.min) * 0.005;
    if (hi - lo < minSpan) {
      const mid = (lo + hi) / 2;
      lo = Math.max(b.min, mid - minSpan / 2);
      hi = Math.min(b.max, lo + minSpan);
    }
    if (this.log && hi / lo < 1.05) return;
    this.apply({ min: lo, max: hi });
  }

  reset(): void {
    this.apply(this.bounds());
  }

  /** Chart.js-Optionen für Aufziehen-zum-Zoomen und Verknüpfung mit dieser Gruppe. */
  pluginOptions(): Record<string, unknown> {
    const accent = token('--accent');
    return {
      zoomGroup: { group: this },
      zoom: {
        zoom: {
          mode: 'x',
          wheel: { enabled: false },
          pinch: { enabled: false },
          drag: {
            enabled: true,
            backgroundColor: alpha(accent, 0.12),
            borderColor: alpha(accent, 0.6),
            borderWidth: 1,
            threshold: 6
          },
          onZoomComplete: ({ chart }: { chart: Chart }) => {
            const x = chart.scales['x'];
            if (x) this.setRange(x.min, x.max);
          }
        },
        pan: { enabled: false }
      }
    };
  }

  /** @internal vom Plugin aufgerufen; ein neuer Chart (z. B. nach Tab-Wechsel) übernimmt den aktuellen Ausschnitt. */
  attach(chart: Chart): void {
    this.charts.add(chart);
    if (this.zoomed()) {
      queueMicrotask(() => {
        if (this.charts.has(chart)) this.zoomChart(chart, this.range());
      });
    }
  }

  /** @internal vom Plugin aufgerufen */
  detach(chart: Chart): void {
    this.charts.delete(chart);
  }

  private apply(r: Range): void {
    this.range.set(r);
    for (const chart of this.charts) {
      this.zoomChart(chart, r);
    }
  }

  /**
   * Setzt den Ausschnitt über die API des Zoom-Plugins. Direkt in chart.options geschriebene
   * Grenzen würden von Chart.js' Options-Cache übergangen.
   */
  private zoomChart(chart: Chart, r: Range): void {
    if (!chart.scales['x']) return;
    (chart as ZoomableChart).zoomScale('x', r, 'none');
  }
}

/** Global registriertes Chart.js-Plugin, das Charts mit ihrer {@link ZoomGroup} verbindet. */
export const zoomGroupPlugin: Plugin = {
  id: 'zoomGroup',
  afterInit(chart, _args, opts: { group?: ZoomGroup }) {
    opts?.group?.attach(chart);
  },
  afterDestroy(chart, _args, opts: { group?: ZoomGroup }) {
    opts?.group?.detach(chart);
  }
};
