import { STEP, fromMin, toMin, todayStr } from '../utils.js';

export default function TimePicker({ cfg, busy, date, start, end, onStart, onEnd, disabled }) {
  if (!cfg) return null;
  const ws = toMin(cfg.workDayStart);
  const we = toMin(cfg.workDayEnd);
  const intervals = busy.map((b) => [toMin(b.startTime), toMin(b.endTime)]);

  const now = new Date();
  const nowMin = date === todayStr() ? now.getHours() * 60 + now.getMinutes() : -1;

  const starts = [];
  for (let m = ws; m < we; m += STEP) starts.push(m);
  const startBlocked = (m) => intervals.some(([s, e]) => m >= s && m < e) || m < nowMin;

  const sm = start ? toMin(start) : null;
  const ends = [];
  if (sm !== null) for (let m = sm + STEP; m <= we; m += STEP) ends.push(m);
  const endBlocked = (m) => intervals.some(([s, e]) => s < m && e > sm);

  return (
    <div className="row">
      <label className="field">
        <span>Время начала *</span>
        <select value={start} disabled={disabled} onChange={(e) => onStart(e.target.value)}>
          <option value="">— выберите —</option>
          {starts.map((m) => (
            <option key={m} value={fromMin(m)} disabled={startBlocked(m)}>
              {fromMin(m)}
              {startBlocked(m) ? ' (занято)' : ''}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>Время окончания *</span>
        <select value={end} disabled={disabled || sm === null} onChange={(e) => onEnd(e.target.value)}>
          <option value="">{sm === null ? 'Сначала выберите начало' : '— выберите —'}</option>
          {ends.map((m) => (
            <option key={m} value={fromMin(m)} disabled={endBlocked(m)}>
              {fromMin(m)}
              {endBlocked(m) ? ' (занято)' : ''}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
