import { ChartType, Plugin } from 'chart.js';

/** X-Bereich (in Achseneinheiten), der hinter den Linien farbig hinterlegt wird. */
export interface HighlightRange {
  from: number;
  to: number;
  color: string;
  label?: string;
}

declare module 'chart.js' {
  interface PluginOptionsByType<TType extends ChartType> {
    highlightRange?: HighlightRange | null;
  }
}

/**
 * Hinterlegt einen X-Bereich, z. B. den erkannten 20-Minuten-FTP-Test. Ohne Option
 * {@code plugins.highlightRange} tut das Plugin nichts.
 */
export const highlightRangePlugin: Plugin = {
  id: 'highlightRange',
  beforeDatasetsDraw(chart, _args, options) {
    const range = options as Partial<HighlightRange> | undefined;
    if (range?.from == null || range.to == null || !range.color) return;
    const x = chart.scales['x'];
    const area = chart.chartArea;
    if (!x || !area) return;

    const left = Math.max(area.left, x.getPixelForValue(range.from));
    const right = Math.min(area.right, x.getPixelForValue(range.to));
    if (right <= left) return;

    const ctx = chart.ctx;
    ctx.save();
    ctx.fillStyle = range.color;
    ctx.fillRect(left, area.top, right - left, area.bottom - area.top);
    if (range.label) {
      ctx.font = '600 10px Inter, system-ui, sans-serif';
      ctx.fillStyle = 'rgba(231, 233, 238, 0.75)';
      ctx.textBaseline = 'top';
      ctx.fillText(range.label, left + 4, area.top + 2);
    }
    ctx.restore();
  }
};
