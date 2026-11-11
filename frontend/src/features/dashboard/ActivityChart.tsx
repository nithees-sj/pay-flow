import type { Entry } from "../../api/types";
import { formatMoney } from "../../lib/money";
export function ActivityChart({ entries }: { entries: Entry[] }) {
  const days = Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() - 6 + index);
    date.setHours(0, 0, 0, 0);
    return { date, credit: 0n, debit: 0n };
  });
  for (const entry of entries) {
    const date = new Date(entry.createdAt);
    const day = days.find((d) => d.date.toDateString() === date.toDateString());
    if (day)
      day[entry.direction === "CREDIT" ? "credit" : "debit"] += BigInt(
        entry.amount,
      );
  }
  const max = days.reduce(
    (n, d) => [n, d.credit, d.debit].reduce((a, b) => (a > b ? a : b)),
    1n,
  );
  return (
    <div
      className="activity-chart"
      role="img"
      aria-label="Incoming and outgoing amounts for the last seven days, from the latest 100 wallet entries"
    >
      <div className="chart-grid">
        <span />
        <span />
        <span />
        <span />
      </div>
      <div className="chart-bars">
        {days.map((day) => (
          <div className="chart-day" key={day.date.toISOString()}>
            <div className="bar-pair">
              <div
                className="bar bar-in"
                style={{
                  height: `${day.credit === 0n ? 3 : Math.max(5, Number((day.credit * 100n) / max))}%`,
                }}
                title={`Incoming ${formatMoney(day.credit.toString())}`}
              />
              <div
                className="bar bar-out"
                style={{
                  height: `${day.debit === 0n ? 3 : Math.max(5, Number((day.debit * 100n) / max))}%`,
                }}
                title={`Outgoing ${formatMoney(day.debit.toString())}`}
              />
            </div>
            <span>
              {new Intl.DateTimeFormat("en", { weekday: "short" }).format(
                day.date,
              )}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
