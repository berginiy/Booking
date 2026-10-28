import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { EMAIL_RE, PHONE_RE, toMin, todayStr } from '../utils.js';
import TimePicker from './TimePicker.jsx';
import DayTimeline from './DayTimeline.jsx';
import AttendeePicker from './AttendeePicker.jsx';
import { useToast } from './Toast.jsx';
import { useAuth } from '../auth.jsx';

export default function EditBookingModal({ booking, cfg, onClose, onSaved }) {
  const toast = useToast();
  const { isAdmin } = useAuth();
  const [form, setForm] = useState({
    fullName: booking.fullName, phone: booking.phone || '', email: booking.email,
    roomId: String(booking.roomId), date: booking.date, startTime: booking.startTime, endTime: booking.endTime,
  });
  const [rooms, setRooms] = useState([]);
  const [people, setPeople] = useState([]);
  const [attendeeIds, setAttendeeIds] = useState(booking.attendees.map((a) => a.id));
  const [busy, setBusy] = useState([]);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const room = rooms.find((r) => String(r.id) === String(form.roomId));

  useEffect(() => {
    api.employees().then((list) => {
      const known = new Set(list.map((p) => p.id));
      setPeople([...list, ...booking.attendees.filter((a) => !known.has(a.id))]);
    }).catch(() => {});
  }, []);

  useEffect(() => {
    api.rooms().then((list) => {
      if (!list.some((r) => r.id === booking.roomId)) {
        list = [...list, { id: booking.roomId, name: booking.roomName, floor: booking.floor }];
      }
      setRooms(list);
    }).catch((e) => toast.error(e.message));
  }, []);

  useEffect(() => {
    if (!form.roomId || !form.date) return;
    api.bookings({ roomId: form.roomId, date: form.date })
      .then((l) => setBusy(l.filter((b) => b.id !== booking.id)))
      .catch(() => {});
  }, [form.roomId, form.date]);

  const validate = () => {
    const e = {};
    if (!form.fullName.trim()) e.fullName = 'Укажите ФИО';
    if (!form.email.trim()) e.email = 'Укажите email';
    else if (!EMAIL_RE.test(form.email.trim())) e.email = 'Некорректный email';
    if (form.phone.trim() && !PHONE_RE.test(form.phone.trim())) e.phone = 'Некорректный номер телефона';
    if (!form.date) e.date = 'Укажите дату';
    if (!form.startTime) e.startTime = 'Укажите время начала';
    if (!form.endTime) e.endTime = 'Укажите время окончания';
    else if (form.startTime && toMin(form.endTime) <= toMin(form.startTime)) e.endTime = 'Окончание должно быть позже начала';
    if (room?.capacity && attendeeIds.length + 1 > room.capacity) e.attendees = `Зал вмещает ${room.capacity} чел. Уберите лишних участников`;
    return e;
  };

  const save = async () => {
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length) return toast.error('Некорректно заполнены обязательные поля');
    setSaving(true);
    try {
      await api.updateBooking(booking.id, {
        ...form, phone: form.phone.trim() || null, roomId: Number(form.roomId), attendeeIds,
      });
      toast.success('Бронирование успешно изменено');
      onSaved();
    } catch (err) {
      setErrors(err.fieldErrors || {});
      toast.error(err.message);
      if (err.status === 409) set('endTime', '');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal">
        <h3>Изменение бронирования №{booking.id}</h3>

        <label className="field">
          <span>Зал *</span>
          <select value={form.roomId} onChange={(e) => { set('roomId', e.target.value); set('startTime', ''); set('endTime', ''); }}>
            {rooms.map((r) => <option key={r.id} value={r.id}>Этаж {r.floor} — {r.name}</option>)}
          </select>
        </label>

        <label className={`field ${errors.date ? 'invalid' : ''}`}>
          <span>Дата *</span>
          <input type="date" min={todayStr()} value={form.date}
                 onChange={(e) => { set('date', e.target.value); set('startTime', ''); set('endTime', ''); }} />
          {errors.date && <small className="err">{errors.date}</small>}
        </label>

        <TimePicker cfg={cfg} busy={busy} date={form.date} start={form.startTime} end={form.endTime}
                    onStart={(v) => { set('startTime', v); set('endTime', ''); }} onEnd={(v) => set('endTime', v)} />
        {(errors.startTime || errors.endTime) && <small className="err">{errors.startTime || errors.endTime}</small>}
        <DayTimeline cfg={cfg} busy={busy} selStart={form.startTime} selEnd={form.endTime} />

        <div className="field">
          <span>Участники</span>
          <AttendeePicker people={people} selectedIds={attendeeIds} onChange={setAttendeeIds}
                          excludeEmail={booking.email} max={room?.capacity ? room.capacity - 1 : null} />
          {errors.attendees && <small className="err">{errors.attendees}</small>}
          {cfg?.mailEnabled && <small className="muted">Об изменениях участники узнают по почте.</small>}
        </div>

        <label className={`field ${errors.fullName ? 'invalid' : ''}`}>
          <span>ФИО *</span>
          <input value={form.fullName} disabled={!isAdmin} onChange={(e) => set('fullName', e.target.value)} />
          {errors.fullName && <small className="err">{errors.fullName}</small>}
        </label>
        <div className="row">
          <label className={`field ${errors.phone ? 'invalid' : ''}`}>
            <span>Телефон</span>
            <input value={form.phone} disabled={!isAdmin} onChange={(e) => set('phone', e.target.value)} />
            {errors.phone && <small className="err">{errors.phone}</small>}
          </label>
          <label className={`field ${errors.email ? 'invalid' : ''}`}>
            <span>Корпоративный email *</span>
            <input value={form.email} disabled={!isAdmin} onChange={(e) => set('email', e.target.value)} />
            {errors.email && <small className="err">{errors.email}</small>}
          </label>
        </div>

        <div className="actions">
          <button className="btn" onClick={onClose}>Закрыть</button>
          <button className="btn btn-primary" onClick={save} disabled={saving}>{saving ? 'Сохранение…' : 'Сохранить'}</button>
        </div>
      </div>
    </div>
  );
}
