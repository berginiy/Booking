import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api.js';
import { useToast } from '../../components/Toast.jsx';
import EditBookingModal from '../../components/EditBookingModal.jsx';
import { STATUS_LABEL, fmtDate } from '../../utils.js';

export default function BookingsAdmin({ cfg, onAuthError }) {
  const toast = useToast();
  const [rooms, setRooms] = useState([]);
  const [f, setF] = useState({ date: '', floor: '', roomId: '', status: '' });
  const [items, setItems] = useState([]);
  const [editing, setEditing] = useState(null);

  const fail = useCallback((e) => (e.status === 401 ? onAuthError() : toast.error(e.message)), []);
  useEffect(() => { api.admin.rooms().then(setRooms).catch(fail); }, []);
  const load = useCallback(() => api.admin.bookings(f).then(setItems).catch(fail), [f]);
  useEffect(() => { load(); }, [load]);

  const cancel = async (b) => {
    if (!window.confirm(`Отменить бронь №${b.id} (${b.roomName}, ${fmtDate(b.date)} ${b.startTime}–${b.endTime})?`)) return;
    try { await api.cancelBooking(b.id); toast.success('Бронирование успешно отменено'); load(); } catch (e) { fail(e); }
  };

  return (
    <div className="stack">
      <h3>Все бронирования</h3>
      <div className="card filters">
        <label className="field"><span>Дата</span><input type="date" value={f.date} onChange={(e) => setF({ ...f, date: e.target.value })} /></label>
        <label className="field"><span>Этаж</span>
          <select value={f.floor} onChange={(e) => setF({ ...f, floor: e.target.value, roomId: '' })}>
            <option value="">Все</option>{cfg?.floors.map((x) => <option key={x} value={x}>{x}</option>)}
          </select></label>
        <label className="field"><span>Зал</span>
          <select value={f.roomId} onChange={(e) => setF({ ...f, roomId: e.target.value })}>
            <option value="">Все</option>
            {rooms.filter((r) => !f.floor || r.floor === Number(f.floor)).map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
          </select></label>
        <label className="field"><span>Статус</span>
          <select value={f.status} onChange={(e) => setF({ ...f, status: e.target.value })}>
            <option value="">Все</option><option value="ACTIVE">Активные</option><option value="CANCELLED">Отменённые</option>
          </select></label>
      </div>
      <div className="card table-wrap">
        {items.length === 0 ? <p className="muted">Бронирований не найдено.</p> : (
          <table>
            <thead><tr><th>№</th><th>Дата</th><th>Время</th><th>Зал</th><th>Этаж</th><th>Организатор</th><th>Контакты</th><th>Участников</th><th>Статус</th><th /></tr></thead>
            <tbody>
              {items.map((b) => (
                <tr key={b.id} className={b.status === 'CANCELLED' ? 'cancelled' : ''}>
                  <td>{b.id}</td><td>{fmtDate(b.date)}</td><td>{b.startTime}–{b.endTime}</td><td>{b.roomName}</td><td>{b.floor}</td>
                  <td>{b.fullName}</td><td>{[b.phone, b.email].filter(Boolean).join(', ')}</td>
                  <td>{b.attendees.length}</td>
                  <td><span className={`badge badge-${b.status}`}>{STATUS_LABEL[b.status]}</span></td>
                  <td className="actions">
                    {b.status === 'ACTIVE' && <>
                      <button className="btn" onClick={() => setEditing(b)}>Изменить</button>
                      <button className="btn btn-danger" onClick={() => cancel(b)}>Отменить</button>
                    </>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {editing && <EditBookingModal booking={editing} cfg={cfg}
                                    onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />}
    </div>
  );
}
