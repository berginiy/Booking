import { toMin } from '../utils.js';

export default function DayTimeline({ cfg, busy, selStart, selEnd }) {
  if (!cfg) return null;
  const ws = toMin(cfg.workDayStart);
  const we = toMin(cfg.workDayEnd);
  const span = we - ws;
  const pos = (a, b) => ({ left: `${((a - ws) / span) * 100}%`, width: `${((b - a) / span) * 100}%` });
  const hours = [];
  for (let m = ws; m <= we; m += 60) hours.push(m);

  return (
    <div className="timeline">
      <div className="timeline-bar">
        {busy.map((b) => (
          <div key={b.id} className="tl-busy" style={pos(toMin(b.startTime), toMin(b.endTime))}
               title={`${b.startTime}–${b.endTime} · ${b.fullName}`} />
        ))}
        {selStart && selEnd && toMin(selEnd) > toMin(selStart) && (
          <div className="tl-sel" style={pos(toMin(selStart), toMin(selEnd))} />
        )}
      </div>
      <div className="timeline-ticks">
        {hours.map((m) => (
          <span key={m} style={{ left: `${((m - ws) / span) * 100}%` }}>
            {Math.floor(m / 60)}
          </span>
        ))}
      </div>
      <div className="legend">
        <i className="dot dot-busy" /> занято <i className="dot dot-sel" /> ваш выбор
      </div>
    </div>
  );
}
