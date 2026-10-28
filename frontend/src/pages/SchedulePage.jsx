import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useToast } from '../components/Toast.jsx';
import { fmtDate, toMin, todayStr } from '../utils.js';

export default function SchedulePage() {
  const toast = useToast();
  const [cfg, setCfg] = useState(null);
  const [rooms, setRooms] = useState([]);
  const [floor, setFloor] = useState('');
  const [roomId, setRoomId] = useState('');
  const [date, setDate] = useState(todayStr());
  const [view, setView] = useState('timeline');
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    Promise.all([api.config(), api.rooms()]).then(([c, r]) => { setCfg(c); setRooms(r); })
      .catch((e) => toast.error(e.message));
  }, []);

  useEffect(() => {
    setLoading(true);
    api.bookings({ date, floor, roomId }).then(setItems).catch((e) => toast.error(e.message)).finally(() => setLoading(false));
  }, [date, floor, roomId]);

  const roomOptions = rooms.filter((r) => !floor || r.floor === Number(floor));
  const timelineAvailable = view === 'timeline' && date && cfg;

  const ws = cfg ? toMin(cfg.workDayStart) : 0;
  const we = cfg ? toMin(cfg.workDayEnd) : 1;
  const hours = [];
  if (cfg) for (let m = ws; m <= we; m += 60) hours.push(m);

  return (
    <div className="stack">
      <h2>Занятость залов</h2>

      <div className="card filters">
        <label className="field"><span>Этаж</span>
          <select value={floor} onChange={(e) => { setFloor(e.target.value); setRoomId(''); }}>
            <option value="">Все этажи</option>
            {cfg?.floors.map((f) => <option key={f} value={f}>{f} этаж</option>)}
          </select>
        </label>
        <label className="field"><span>Зал</span>
          <select value={roomId} onChange={(e) => setRoomId(e.target.value)}>
            <option value="">Все залы</option>
            {roomOptions.map((r) => <option key={r.id} value={r.id}>{r.name} ({r.floor} эт.)</option>)}
          </select>
        </label>
        <label className="field"><span>Дата</span>
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <div className="field">
          <span>Вид</span>
          <div className="seg">
            <button className={view === 'timeline' ? 'on' : ''} onClick={() => setView('timeline')}>Расписание</button>
            <button className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}>Список</button>
          </div>
        </div>
      </div>

      {view === 'timeline' && !date && <p className="muted">Для расписания выберите дату или переключитесь на «Список» (покажет все предстоящие брони).</p>}

      {timelineAvailable && (
        <div className="card scroll-x">
          <h3>{fmtDate(date)}</h3>
          <div className="sched" style={{ '--cols': (we - ws) / 60 }}>
            <div className="sched-row sched-head">
              <div className="sched-label" />
              <div className="sched-track ticks">
                {hours.map((m) => <span key={m} style={{ left: `${((m - ws) / (we - ws)) * 100}%` }}>{Math.floor(m / 60)}:00</span>)}
              </div>
            </div>
            {roomOptions.filter((r) => !roomId || r.id === Number(roomId)).map((r) => (
              <div className="sched-row" key={r.id}>
                <div className="sched-label"><b>{r.name}</b><small>{r.floor} этаж · {r.capacity} чел.</small></div>
                <div className="sched-track">
                  {items.filter((b) => b.roomId === r.id).map((b) => (
                    <div key={b.id} className="sched-block"
                         style={{ left: `${((toMin(b.startTime) - ws) / (we - ws)) * 100}%`, width: `${((toMin(b.endTime) - toMin(b.startTime)) / (we - ws)) * 100}%` }}
                         title={`${b.startTime}–${b.endTime}\n${b.fullName}\n${b.phone || ''} ${b.email}\nУчастников: ${b.attendees.length}`}>
                      {b.fullName}
                    </div>
                  ))}
                </div>
              </div>
            ))}
            {roomOptions.length === 0 && <p className="muted">Нет залов по выбранным фильтрам.</p>}
          </div>
        </div>
      )}

      {view === 'list' && (
        <div className="card table-wrap">
          {loading ? <p className="muted">Загрузка…</p> : items.length === 0 ? <p className="muted">Бронирований не найдено.</p> : (
            <table>
              <thead><tr><th>Дата</th><th>Время</th><th>Зал</th><th>Этаж</th><th>Организатор</th><th>Контакты</th><th>Участники</th></tr></thead>
              <tbody>
                {items.map((b) => (
                  <tr key={b.id}>
                    <td>{fmtDate(b.date)}</td><td>{b.startTime}–{b.endTime}</td><td>{b.roomName}</td><td>{b.floor}</td>
                    <td>{b.fullName}</td><td>{[b.phone, b.email].filter(Boolean).join(', ')}</td>
                    <td>{b.attendees.length ? b.attendees.map((a) => a.fullName).join(', ') : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
