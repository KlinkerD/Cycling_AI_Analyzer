import { Chart, registerables } from 'chart.js';
import zoomPlugin from 'chartjs-plugin-zoom';
import { token } from '../theme';
import { zoomGroupPlugin } from './zoom-group';
import { highlightRangePlugin } from './highlight-range';

/** Einmalige Chart.js-Grundeinstellungen passend zum dunklen Theme. */
export function setupCharts(): void {
  Chart.register(...registerables, zoomPlugin, zoomGroupPlugin, highlightRangePlugin);

  Chart.defaults.font.family = "'Inter', system-ui, sans-serif";
  Chart.defaults.font.size = 11;
  Chart.defaults.color = token('--text-muted');
  Chart.defaults.borderColor = token('--grid');
  Chart.defaults.animation = false;
  Chart.defaults.maintainAspectRatio = false;
  Chart.defaults.responsive = true;

  Chart.defaults.plugins.legend.labels.boxWidth = 10;
  Chart.defaults.plugins.legend.labels.boxHeight = 10;
  Chart.defaults.plugins.legend.labels.usePointStyle = true;
  Chart.defaults.plugins.legend.labels.pointStyle = 'rectRounded';

  const tooltip = Chart.defaults.plugins.tooltip;
  tooltip.backgroundColor = token('--surface-3');
  tooltip.borderColor = token('--border-strong');
  tooltip.borderWidth = 1;
  tooltip.titleColor = token('--text');
  tooltip.bodyColor = token('--text');
  tooltip.padding = 10;
  tooltip.cornerRadius = 8;
  tooltip.boxPadding = 4;
  tooltip.usePointStyle = true;
}
