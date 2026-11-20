export const widgetTypes = [
  'BURNDOWN',
  'CFD',
  'FORECAST',
  'WIP',
  'MY_TASKS',
  'SPRINT_GOAL',
] as const;
export type WidgetType = (typeof widgetTypes)[number];
export type DashboardWidget = { type: WidgetType; wide: boolean };
export type DashboardLayout = { version: number; widgets: DashboardWidget[] };
export const defaultWidgets = (): DashboardWidget[] =>
  widgetTypes.map((type) => ({
    type,
    wide: type === 'BURNDOWN' || type === 'CFD',
  }));
