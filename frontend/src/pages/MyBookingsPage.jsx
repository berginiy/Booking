import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useToast } from '../components/Toast.jsx';
import EditBookingModal from '../components/EditBookingModal.jsx';
import { STATUS_LABEL, fmtDate, isPastBooking } from '../utils.js';

export default function MyBookingsPage() {
  const toast = useToast();
  const [cfg, setCfg] = useState(null);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(null);

  const load = useCallback(() => api.myBookings().then(setItems)
    .catch((e) => toast.error(e.message)).finally(() => setLoading(false)), []);

  useEffect(() => { api.config().then(setCfg).catch(() => {}); load(); }, [load]);

  const cancel = async (b) => {
    if (!window.confirm(`Отменить бронирование: ${b.roomName}, ${fmtDate(b.date)} ${b.startTime}–${b.endTime}?`)) return;
    try {
      await api.cancelBooking(b.id);
      toast.success('Бронирование успешно отменено');
      load();
    } catch (e) { toast.error(e.message); }
  };

  return (
    <div className="stack">
      <h2>Мои бронирования</h2>
      {loading ? <p className="muted">Загрузка…</p> : items.length === 0 ? (
        <div className="card"><p>У вас пока нет бронирований. <Link to="/">Забронировать зал</Link></p></div>
      ) : items.map((b) => {
        const editable = b.organizer && b.status === 'ACTIVE' && !isPastBooking(b);
        return (
          <div className={`card booking ${b.status === 'CANCELLED' ? 'cancelled' : ''}`} key={b.id}>
            <div>
              <b>{b.roomName}</b> · {b.floor} этаж <span className={`badge badge-${b.status}`}>{STATUS_LABEL[b.status]}</span>
              {!b.organizer && <span className="badge badge-INFO">Приглашён</span>}
              <div>{fmtDate(b.date)}, {b.startTime}–{b.endTime}</div>
              {!b.organizer && <div className="muted">Организатор: {b.fullName}</div>}
              {b.attendees.length > 0 && <div className="muted">Участники: {b.attendees.map((a) => a.fullName).join(', ')}</div>}
            </div>
            {editable && (
              <div className="actions">
                <button className="btn" onClick={() => setEditing(b)}>Изменить</button>
                <button className="btn btn-danger" onClick={() => cancel(b)}>Отменить</button>
              </div>
            )}
          </div>
        );
      })}
      {editing && (
        <EditBookingModal booking={editing} cfg={cfg}
                          onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />
      )}
    </div>
  );
}
