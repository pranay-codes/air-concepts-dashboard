import { formatFetchTime } from "../ui/format";
import { activityEntries, type ActivityEvent } from "./activity";

export function ActivityTimeline({ events }: { events: ActivityEvent[] }) {
  if (!events.length) return <p className="supporting">No activity recorded.</p>;
  return (
    <>
      <p className="supporting">Times show when the dashboard recorded each change.</p>
      <ol className="activity-timeline">
        {activityEntries(events).map((entry) => (
          <li key={entry.key}>
            <p className="activity-title">{entry.title}</p>
            {entry.changes.length ? (
              <dl className="activity-changes">
                {entry.changes.map((change) => (
                  <div key={change.label}>
                    <dt>{change.label}</dt>
                    <dd>
                      {change.from ? (
                        <>
                          <span className="activity-previous">{change.from}</span>{" "}
                          <span aria-hidden="true">→</span>{" "}
                          <span className="visually-hidden">changed to</span>{" "}
                        </>
                      ) : null}
                      <span>{change.to}</span>
                    </dd>
                  </div>
                ))}
              </dl>
            ) : null}
            <p className="supporting">
              {entry.actor} · <time dateTime={entry.at}>{formatFetchTime(entry.at)}</time>
            </p>
          </li>
        ))}
      </ol>
    </>
  );
}
