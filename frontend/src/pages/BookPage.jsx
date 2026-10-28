import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useToast } from '../components/Toast.jsx';
import TimePicker from '../components/TimePicker.jsx';
import DayTimeline from '../components/DayTimeline.jsx';
import AttendeePicker from '../components/AttendeePicker.jsx';
import { useAuth } from '../auth.jsx';
import { EMAIL_RE, PHONE_RE, fmtDate, todayStr, toMin } from '../utils.js';

export default function BookPage() {
  const toast = useToast();
  const { user, isAdmin } = useAuth();
  const locked = !isAdmin;
  const [cfg, setCfg] = useState(null);
  const [floor, setFloor] = useState('');
  const [rooms, setRooms] = useState([]);
  const [roomId, setRoomId] = useState('');
  const [date, setDate] = useState(todayStr());
  const [busy, setBusy] = useState([]);
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [people, setPeople] = useState([]);
  const [attendeeIds, setAttendeeIds] = useState([]);
  const [form, setForm] = useState({ fullName: user.fullName || '', phone: user.phone || '', email: user.email || '' });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(null);

  const room = rooms.find((r) => String(r.id) === String(roomId));
  const maxAttendees = room ? room.capacity - 1 : null;

  useEffect(() => { api.config().then(setCfg).catch((e) => toast.error(e.message)); }, []);
  useEffect(() => { api.employees().then(setPeople).catch(() => {}); }, []);

  useEffect(() => {
    setRoomId(''); setRooms([]);
    if (floor) api.rooms(floor).then(setRooms).catch((e) => toast.error(e.message));
  }, [floor]);

  const loadBusy = useCallback(() => {
    if (!roomId || !date) { setBusy([]); return Promise.resolve(); }
    return api.bookings({ roomId, date }).then(setBusy).catch((e) => toast.error(e.message));
  }, [roomId, date]);

  useEffect(() => { setStart(''); setEnd(''); loadBusy(); }, [roomId, date]);

  const setF = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const validate = () => {
    const e = {};
    if (!floor) e.floor = 'Выберите этаж';
    if (!roomId) e.roomId = 'Выберите зал';
    if (!date) e.date = 'Укажите дату';
    else if (date < todayStr()) e.date = 'Нельзя бронировать на прошедшую дату';
    if (!start) e.startTime = 'Укажите время начала';
    if (!end) e.endTime = 'Укажите время окончания';
    else if (start && toMin(end) <= toMin(start)) e.endTime = 'Окончание должно быть позже начала';
    if (!form.fullName.trim()) e.fullName = 'Укажите ФИО';
    if (!form.email.trim()) e.email = 'Укажите корпоративный email';
    else if (!EMAIL_RE.test(form.email.trim())) e.email = 'Некорректный email';
    else if (cfg?.corporateEmailDomain && !form.email.trim().toLowerCase().endsWith('@' + cfg.corporateEmailDomain.toLowerCase()))
      e.email = `Используйте корпоративную почту (@${cfg.corporateEmailDomain})`;
    if (form.phone.trim() && !PHONE_RE.test(form.phone.trim())) e.phone = 'Некорректный номер телефона';
    if (room && attendeeIds.length + 1 > room.capacity) e.attendees = `Зал вмещает ${room.capacity} чел. Уберите лишних участников`;
    return e;
  };

  const submit = async () => {
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length) return toast.error('Некорректно заполнены обязательные поля');
    setSubmitting(true);
    try {
      const booking = await api.createBooking({
        fullName: form.fullName.trim(), phone: form.phone.trim() || null, email: form.email.trim(),
        roomId: Number(roomId), date, startTime: start, endTime: end, attendeeIds,
      });
      setDone(booking);
      toast.success('Бронирование успешно создано');
    } catch (err) {
      if (err.status === 409) {
        toast.error(err.message);
        setStart(''); setEnd('');
        loadBusy();
      } else {
        setErrors(err.fieldErrors || {});
        toast.error(err.fieldErrors && Object.keys(err.fieldErrors).length ? 'Некорректно заполнены обязательные поля' : err.message);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const reset = () => { setDone(null); setStart(''); setEnd(''); setAttendeeIds([]); setErrors({}); loadBusy(); };

  if (done) {
    return (
      <div className="card confirm">
        <span className="check-badge" />
        <h2>Бронирование подтверждено</h2>
        <dl className="kv">
          <dt>Номер брони</dt><dd>№{done.id}</dd>
          <dt>Этаж</dt><dd>{done.floor}</dd>
          <dt>Зал</dt><dd>{done.roomName}</dd>
          <dt>Дата</dt><dd>{fmtDate(done.date)}</dd>
          <dt>Время</dt><dd>{done.startTime} – {done.endTime}</dd>
          <dt>Организатор</dt><dd>{done.fullName}</dd>
          <dt>Участники</dt><dd>{done.attendees.length ? done.attendees.map((a) => a.fullName).join(', ') : 'Без участников'}</dd>
          <dt>Контакты</dt><dd>{[done.phone, done.email].filter(Boolean).join(', ')}</dd>
        </dl>
        <div className="actions">
          <Link className="btn" to="/my">Мои бронирования</Link>
          <button className="btn btn-primary" onClick={reset}>Забронировать ещё</button>
        </div>
      </div>
    );
  }

  return (
    <div className="stack">
      <div className="page-head">
        <h2>Бронирование конференц-зала</h2>
        <p>Выберите этаж, зал и время. Доступность проверяется автоматически.</p>
      </div>

      <section className="card">
        <h3><b>1</b> Этаж и зал</h3>
        <div className="row">
          <label className={`field ${errors.floor ? 'invalid' : ''}`}>
            <span>Этаж *</span>
            <select value={floor} onChange={(e) => setFloor(e.target.value)}>
              <option value="">— выберите этаж —</option>
              {cfg?.floors.map((f) => <option key={f} value={f}>{f} этаж</option>)}
            </select>
            {errors.floor && <small className="err">{errors.floor}</small>}
          </label>
          <label className={`field ${errors.roomId ? 'invalid' : ''}`}>
            <span>Конференц-зал *</span>
            <select value={roomId} disabled={!floor} onChange={(e) => setRoomId(e.target.value)}>
              <option value="">{floor ? (rooms.length ? '— выберите зал —' : 'На этаже нет доступных залов') : 'Сначала выберите этаж'}</option>
              {rooms.map((r) => <option key={r.id} value={r.id}>{r.name} · до {r.capacity} чел.</option>)}
            </select>
            {errors.roomId && <small className="err">{errors.roomId}</small>}
          </label>
        </div>
        {room && (
          <div className="room-info">
            <span className="chip">до {room.capacity} чел.</span>
            {room.equipment.map((item) => <span className="chip chip-muted" key={item}>{item}</span>)}
            {room.description && <p className="muted">{room.description}</p>}
          </div>
        )}
      </section>

      <section className="card">
        <h3><b>2</b> Дата и время</h3>
        <label className={`field ${errors.date ? 'invalid' : ''}`}>
          <span>Дата *</span>
          <input type="date" min={todayStr()} value={date} onChange={(e) => setDate(e.target.value)} />
          {errors.date && <small className="err">{errors.date}</small>}
        </label>
        <TimePicker cfg={cfg} busy={busy} date={date} start={start} end={end}
                    disabled={!roomId} onStart={(v) => { setStart(v); setEnd(''); }} onEnd={setEnd} />
        {(errors.startTime || errors.endTime) && <small className="err">{errors.startTime || errors.endTime}</small>}
        {roomId && (
          <>
            <DayTimeline cfg={cfg} busy={busy} selStart={start} selEnd={end} />
            {busy.length > 0
              ? <div className="notice notice-warn">Зал занят: {busy.map((b) => `${b.startTime}–${b.endTime}`).join(', ')}</div>
              : <div className="notice notice-ok">На выбранную дату зал свободен весь день.</div>}
          </>
        )}
      </section>

      <section className="card">
        <h3><b>3</b> Участники встречи</h3>
        <p className="muted">
          Необязательно. Приглашённые увидят эту бронь в разделе «Мои бронирования»
          {cfg?.mailEnabled ? ' и получат письмо на почту.' : '.'}
        </p>
        <AttendeePicker people={people} selectedIds={attendeeIds} onChange={setAttendeeIds}
                        excludeEmail={form.email} max={maxAttendees} />
        {errors.attendees && <small className="err">{errors.attendees}</small>}
      </section>

      <section className="card">
        <h3><b>4</b> Данные бронирующего</h3>
        {locked && <p className="muted">Данные берутся из вашего профиля. Чтобы их изменить, обратитесь к администратору.</p>}
        <label className={`field ${errors.fullName ? 'invalid' : ''}`}>
          <span>ФИО *</span>
          <input value={form.fullName} disabled={locked} onChange={(e) => setF('fullName', e.target.value)} placeholder="Иванов Иван Иванович" />
          {errors.fullName && <small className="err">{errors.fullName}</small>}
        </label>
        <div className="row">
          <label className={`field ${errors.phone ? 'invalid' : ''}`}>
            <span>Контактный телефон</span>
            <input value={form.phone} disabled={locked} onChange={(e) => setF('phone', e.target.value)} placeholder="+996" />
            {errors.phone && <small className="err">{errors.phone}</small>}
          </label>
          <label className={`field ${errors.email ? 'invalid' : ''}`}>
            <span>Корпоративный email *</span>
            <input type="email" value={form.email} disabled={locked} onChange={(e) => setF('email', e.target.value)}
                   placeholder={cfg?.corporateEmailDomain ? `name@${cfg.corporateEmailDomain}` : 'name@company.com'} />
            {errors.email && <small className="err">{errors.email}</small>}
          </label>
        </div>
        <div className="actions">
          <button className="btn btn-primary btn-lg" onClick={submit} disabled={submitting}>
            {submitting ? 'Проверяем и сохраняем…' : 'Подтвердить бронирование'}
          </button>
        </div>
      </section>
    </div>
  );
}
