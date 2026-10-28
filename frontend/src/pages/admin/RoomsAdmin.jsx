import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api.js';
import { useToast } from '../../components/Toast.jsx';

const empty = (floors) => ({ name: '', floor: floors[0] ?? 1, capacity: 10, description: '', equipment: [], active: true });

export default function RoomsAdmin({ cfg, onAuthError }) {
  const toast = useToast();
  const [rooms, setRooms] = useState([]);
  const [edit, setEdit] = useState(null); 
  const [errors, setErrors] = useState({});

  const fail = useCallback((e) => (e.status === 401 ? onAuthError() : toast.error(e.message)), []);
  const load = useCallback(() => api.admin.rooms().then(setRooms).catch(fail), []);
  useEffect(() => { load(); }, [load]);

  const save = async () => {
    const e = {};
    if (!edit.name.trim()) e.name = 'Укажите название зала';
    if (!edit.capacity || edit.capacity < 1) e.capacity = 'Вместимость должна быть не менее 1';
    setErrors(e);
    if (Object.keys(e).length) return;
    const body = { ...edit, floor: Number(edit.floor), capacity: Number(edit.capacity), equipment: edit.equipment.map((s) => s.trim()).filter(Boolean) };
    try {
      if (edit.id) await api.admin.updateRoom(edit.id, body); else await api.admin.createRoom(body);
      toast.success(edit.id ? 'Зал обновлён' : 'Зал добавлен');
      setEdit(null); load();
    } catch (err) { setErrors(err.fieldErrors || {}); fail(err); }
  };

  const toggle = (r) => api.admin.setRoomActive(r.id, !r.active)
    .then(() => { toast.success(r.active ? 'Зал отключён для бронирования' : 'Зал включён'); load(); }).catch(fail);

  const remove = (r) => {
    if (!window.confirm(`Удалить зал «${r.name}»? Если у зала есть бронирования, его можно только деактивировать.`)) return;
    api.admin.deleteRoom(r.id).then(() => { toast.success('Зал удалён'); load(); }).catch(fail);
  };

  const setEq = (i, v) => setEdit({ ...edit, equipment: edit.equipment.map((x, j) => (j === i ? v : x)) });

  return (
    <div className="stack">
      <div className="toolbar">
        <h3>Конференц-залы</h3>
        <button className="btn btn-primary" onClick={() => { setErrors({}); setEdit(empty(cfg?.floors || [1])); }}>+ Добавить зал</button>
      </div>
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Этаж</th><th>Название</th><th>Вместимость</th><th>Оборудование</th><th>Статус</th><th /></tr></thead>
          <tbody>
            {rooms.map((r) => (
              <tr key={r.id} className={r.active ? '' : 'cancelled'}>
                <td>{r.floor}</td><td><b>{r.name}</b><div className="muted">{r.description}</div></td><td>{r.capacity}</td>
                <td>{r.equipment.join(', ') || '—'}</td>
                <td><span className={`badge ${r.active ? 'badge-ACTIVE' : 'badge-CANCELLED'}`}>{r.active ? 'Доступен' : 'Отключён'}</span></td>
                <td className="actions">
                  <button className="btn" onClick={() => { setErrors({}); setEdit({ ...r }); }}>Изменить</button>
                  <button className="btn" onClick={() => toggle(r)}>{r.active ? 'Отключить' : 'Включить'}</button>
                  <button className="btn btn-danger" onClick={() => remove(r)}>Удалить</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {edit && (
        <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && setEdit(null)}>
          <div className="modal">
            <h3>{edit.id ? 'Редактирование зала' : 'Новый зал'}</h3>
            <label className={`field ${errors.name ? 'invalid' : ''}`}><span>Название *</span>
              <input value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} />
              {errors.name && <small className="err">{errors.name}</small>}</label>
            <div className="row">
              <label className="field"><span>Этаж *</span>
                <select value={edit.floor} onChange={(e) => setEdit({ ...edit, floor: e.target.value })}>
                  {cfg?.floors.map((f) => <option key={f} value={f}>{f} этаж</option>)}
                </select></label>
              <label className={`field ${errors.capacity ? 'invalid' : ''}`}><span>Вместимость *</span>
                <input type="number" min="1" value={edit.capacity} onChange={(e) => setEdit({ ...edit, capacity: e.target.value })} />
                {errors.capacity && <small className="err">{errors.capacity}</small>}</label>
            </div>
            <label className="field"><span>Описание</span>
              <textarea rows="2" value={edit.description || ''} onChange={(e) => setEdit({ ...edit, description: e.target.value })} /></label>
            <div className="field"><span>Оборудование</span>
              {edit.equipment.map((it, i) => (
                <div className="eq-row" key={i}>
                  <input value={it} onChange={(e) => setEq(i, e.target.value)} />
                  <button className="btn btn-danger" onClick={() => setEdit({ ...edit, equipment: edit.equipment.filter((_, j) => j !== i) })}>Удалить</button>
                </div>
              ))}
              <button className="btn" onClick={() => setEdit({ ...edit, equipment: [...edit.equipment, ''] })}>+ Добавить оборудование</button>
            </div>
            <label className="check"><input type="checkbox" checked={edit.active} onChange={(e) => setEdit({ ...edit, active: e.target.checked })} /> Доступен для бронирования</label>
            <div className="actions">
              <button className="btn" onClick={() => setEdit(null)}>Отмена</button>
              <button className="btn btn-primary" onClick={save}>Сохранить</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
