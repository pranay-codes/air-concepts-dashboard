import { useId, useState, type ReactNode } from "react";
import { useMediaQuery } from "../ui/useMediaQuery";

export type Column<T> = {
  header: string;
  numeric?: boolean;
  /** Takes a whole line on a phone card. */
  wide?: boolean;
  cell(row: T): ReactNode;
};

const WIDE_LIMIT = 10;
const NARROW_LIMIT = 5;

/**
 * One table on wider screens, one card per row on narrow ones. The first column names the row.
 * `collapsible` shows the first rows only until asked; `table` keeps short summaries as tables everywhere.
 */
export function Records<T>({
  label,
  columns,
  rows,
  rowKey,
  collapsible = false,
  table = false,
}: {
  label: string;
  columns: Column<T>[];
  rows: T[];
  rowKey(row: T): string;
  collapsible?: boolean;
  table?: boolean;
}) {
  const narrow = useMediaQuery("(width < 48rem)");
  const [all, setAll] = useState(false);
  const limit = narrow ? NARROW_LIMIT : WIDE_LIMIT;
  const long = collapsible && rows.length > limit;
  const shown = long && !all ? rows.slice(0, limit) : rows;
  const [head, ...facts] = columns;
  const list =
    narrow && !table ? (
      <ul className="mywork-cards" aria-label={label}>
        {shown.map((row) => (
          <li key={rowKey(row)} className="card mywork-card">
            <div className="overview-card-head">{head?.cell(row)}</div>
            <dl className="overview-card-facts">
              {facts.map((column) => (
                <div key={column.header} className={column.wide ? "wide" : undefined}>
                  <dt>{column.header}</dt>
                  <dd>{column.cell(row)}</dd>
                </div>
              ))}
            </dl>
          </li>
        ))}
      </ul>
    ) : (
      <div
        className={`mywork-table-wrap${table ? " overview-compact" : ""}`}
        role="group"
        aria-label={label}
        tabIndex={0}
      >
        <table className="mywork-table">
          <caption className="visually-hidden">{label}</caption>
          <thead>
            <tr>
              {columns.map((column) => (
                <th
                  key={column.header}
                  scope="col"
                  className={column.numeric ? "money" : undefined}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {shown.map((row) => (
              <tr key={rowKey(row)}>
                {columns.map((column, index) =>
                  index === 0 ? (
                    <th key={column.header} scope="row">
                      {column.cell(row)}
                    </th>
                  ) : (
                    <td key={column.header} className={column.numeric ? "money" : undefined}>
                      {column.cell(row)}
                    </td>
                  ),
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  return (
    <>
      {list}
      {long ? (
        <div className="overview-more">
          <span className="supporting">
            Showing {shown.length} of {rows.length}
          </span>
          <button type="button" aria-expanded={all} onClick={() => setAll((value) => !value)}>
            {all ? "Show fewer" : `Show all ${rows.length}`}
          </button>
        </div>
      ) : null}
    </>
  );
}

/** A named region. With an id it is also a jump-link and focus target. */
export function Section({
  id,
  title,
  note,
  level = 2,
  children,
}: {
  id?: string;
  title: string;
  note?: string;
  level?: 2 | 3;
  children: ReactNode;
}) {
  const heading = useId();
  const Heading = level === 2 ? "h2" : "h3";
  return (
    <section id={id} className="overview-section" aria-labelledby={heading} tabIndex={-1}>
      <Heading id={heading}>{title}</Heading>
      {note ? <p className="supporting">{note}</p> : null}
      {children}
    </section>
  );
}

/** Decorative comparison bar. The figure it illustrates is always written beside it. */
export function Bar({ value, max }: { value: number; max: number }) {
  return (
    <span className="overview-bar" aria-hidden="true">
      <span style={{ width: `${max > 0 ? Math.round((value / max) * 100) : 0}%` }} />
    </span>
  );
}
