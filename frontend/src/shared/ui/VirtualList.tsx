import { useVirtualizer } from '@tanstack/react-virtual';
import { useRef, type ReactNode } from 'react';

/** Lists up to this length render every item; longer ones are virtualized. */
export const VIRTUALIZE_AFTER = 60;

/**
 * A scrollable, virtualized list for long boards. Only the items in view (plus
 * an overscan margin, so Tab can reach the next item) are in the DOM; their
 * real heights are measured after render.
 */
export function VirtualList<T>({
  items,
  label,
  estimateSize,
  getKey,
  renderItem,
}: {
  items: readonly T[];
  label: string;
  estimateSize: number;
  getKey: (item: T) => string | number;
  renderItem: (item: T) => ReactNode;
}) {
  'use no memo'; // The virtualizer mutates its own state between renders.
  const scrollRef = useRef<HTMLDivElement>(null);
  // The compiler skips this component ('use no memo'); the virtualizer
  // re-renders it itself when the scroll position or measurements change.
  // eslint-disable-next-line react-hooks/incompatible-library
  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => estimateSize,
    getItemKey: (index) => getKey(items[index] as T),
    overscan: 4,
  });
  return (
    // eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions -- Home/End scroll this focusable viewport; child controls keep their own keys.
    <div
      ref={scrollRef}
      className="virtual-scroll"
      // A scrollable region must be reachable by keyboard (WCAG 2.1.1).
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      tabIndex={0}
      role="group"
      aria-label={label}
      onKeyDown={(event) => {
        if (event.target !== event.currentTarget) return;
        if (event.key !== 'Home' && event.key !== 'End') return;
        event.preventDefault();
        // Native End can stop at the last mounted item in a virtual window.
        // Target the list index so measurement corrections reach the real edge.
        virtualizer.scrollToIndex(event.key === 'Home' ? 0 : items.length - 1, {
          align: event.key === 'Home' ? 'start' : 'end',
        });
      }}
    >
      <div
        className="virtual-scroll-content"
        style={{ height: virtualizer.getTotalSize() }}
      >
        {virtualizer.getVirtualItems().map((row) => (
          <div
            key={row.key}
            data-index={row.index}
            ref={virtualizer.measureElement}
            className="virtual-scroll-item"
            style={{ transform: `translateY(${row.start}px)` }}
          >
            {renderItem(items[row.index] as T)}
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * A table whose body is virtualized once it has more than VIRTUALIZE_AFTER
 * rows. The rendered window is padded by spacer rows so the table keeps its
 * scroll height, and `aria-rowcount`/`aria-rowindex` tell assistive
 * technology where each row sits in the full list.
 */
export function VirtualTable<T>({
  rows,
  label,
  className,
  headCells,
  columns,
  estimateSize,
  getKey,
  renderRow,
}: {
  rows: readonly T[];
  label: string;
  className: string;
  headCells: ReactNode;
  columns: number;
  estimateSize: number;
  getKey: (row: T) => string | number;
  renderRow: (row: T) => ReactNode;
}) {
  'use no memo';
  const scrollRef = useRef<HTMLDivElement>(null);
  const enabled = rows.length > VIRTUALIZE_AFTER;
  // eslint-disable-next-line react-hooks/incompatible-library -- see VirtualList
  const virtualizer = useVirtualizer({
    count: enabled ? rows.length : 0,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => estimateSize,
    getItemKey: (index) => getKey(rows[index] as T),
    overscan: 6,
  });
  if (!enabled)
    return (
      <table className={className}>
        <caption className="sr-only">{label}</caption>
        <thead>
          <tr>{headCells}</tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={getKey(row)}>{renderRow(row)}</tr>
          ))}
        </tbody>
      </table>
    );
  const items = virtualizer.getVirtualItems();
  const before = items[0]?.start ?? 0;
  const last = items.at(-1);
  const after = last ? virtualizer.getTotalSize() - last.end : 0;
  return (
    // eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions -- Home/End scroll this focusable viewport; child controls keep their own keys.
    <div
      ref={scrollRef}
      className="virtual-scroll"
      // A scrollable region must be reachable by keyboard (WCAG 2.1.1).
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      tabIndex={0}
      role="group"
      aria-label={label}
      onKeyDown={(event) => {
        if (event.target !== event.currentTarget) return;
        if (event.key !== 'Home' && event.key !== 'End') return;
        event.preventDefault();
        // Native End can stop at the last mounted item in a virtual window.
        // Target the list index so measurement corrections reach the real edge.
        virtualizer.scrollToIndex(event.key === 'Home' ? 0 : rows.length - 1, {
          align: event.key === 'Home' ? 'start' : 'end',
        });
      }}
    >
      <table className={className} aria-rowcount={rows.length + 1}>
        <caption className="sr-only">{label}</caption>
        <thead>
          <tr aria-rowindex={1}>{headCells}</tr>
        </thead>
        <tbody>
          {before > 0 && (
            <tr className="virtual-spacer" aria-hidden="true">
              <td colSpan={columns} style={{ height: before }} />
            </tr>
          )}
          {items.map((item) => (
            <tr
              key={item.key}
              data-index={item.index}
              ref={virtualizer.measureElement}
              aria-rowindex={item.index + 2}
            >
              {renderRow(rows[item.index] as T)}
            </tr>
          ))}
          {after > 0 && (
            <tr className="virtual-spacer" aria-hidden="true">
              <td colSpan={columns} style={{ height: after }} />
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
