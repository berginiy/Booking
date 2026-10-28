import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api.js';
import { useToast } from '../../components/Toast.jsx';
import { EMAIL_RE, PHONE_RE, downloadCsv } from '../../utils.js';

const emptyForm = { fullName: '', email: '', phone: '', active: true };

export default function EmployeesAdmin({ onAuthError }) {
  const toast = useToast();
  const fileRef = useRef(null);
  const [items, setItems] = useState([]);
  const [q, setQ] = useState('');
  const [edit, setEdit] = useState(null);
  const [errors, setErrors] = useState({});
  const [creds, setCreds] = useState(null);   
  const [importing, setImporting] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const fail = useCallback((e) => (e.status === 401 ? onAuthError() : toast.error(e.message)), []);
  const load = useCallback(() => api.admin.employees().then(setItems).catch(fail), []);
  useEffect(() => { load(); }, [load]);

  const filtered = items.filter((e) => (e.fullName + ' ' + e.email).toLowerCase().includes(q.trim().toLowerCase()));

  const save = async () => {
    const er = {};
    if (!edit.fullName.trim()) er.fullName = 'Укажите ФИО';
    if (!edit.email.trim()) er.email = 'Укажите email';
    else if (!EMAIL_RE.test(edit.email.trim())) er.email = 'Некорректный email';
    if (edit.phone && edit.phone.trim() && !PHONE_RE.test(edit.phone.trim())) er.phone = 'Некорректный телефон';
    setErrors(er);
    if (Object.keys(er).length) return;
    const body = { fullName: edit.fullName.trim(), email: edit.email.trim(), phone: (edit.phone || '').trim() || null, active: edit.active };
    try {
      if (edit.id) {
        await api.admin.updateEmployee(edit.id, body);
        toast.success('Данные сотрудника сохранены');
      } else {
        const c = await api.admin.createEmployee(body);
        setCreds({ title: 'Сотрудник добавлен', rows: [c], errors: [] });
      }
      setEdit(null); load();
    } catch (err) { setErrors(err.fieldErrors || {}); fail(err); }
  };

  const toggleActive = (e) => api.admin.updateEmployee(e.id, { fullName: e.fullName, email: e.email, phone: e.phone || null, active: !e.active })
    .then(() => { toast.success(e.active ? 'Сотрудник отключён — войти в систему он больше не сможет' : 'Сотрудник включён'); load(); })
    .catch(fail);

  const reset = async (e) => {
    if (!window.confirm(`Сбросить пароль сотруднику ${e.fullName}? Будет выдан новый временный пароль.`)) return;
    try {
      const c = await api.admin.resetPassword(e.id);
      setCreds({ title: 'Пароль сброшен', rows: [c], errors: [] });
      load();
    } catch (err) { fail(err); }
  };

  const onFile = async (ev) => {
    const file = ev.target.files[0];
    ev.target.value = '';
    if (!file) return;
    setImporting(true);
    try {
      const res = await api.admin.importEmployees(file);
      toast.success(`Импорт завершён: создано ${res.created}, обновлено ${res.updated}, ошибок ${res.skipped}`);
      setCreds({ title: 'Результат импорта', rows: res.credentials, errors: res.errors, summary: res });
      load();
    } catch (err) { fail(err); } finally { setImporting(false); }
  };

  const sync = async () => {
    if (!window.confirm('Синхронизировать сотрудников с каталогом Outlook? Новые будут добавлены, а отсутствующие в каталоге или отключённые в нём — отключены.')) return;
    setSyncing(true);
    try {
      const res = await api.admin.syncEmployees();
      toast.success(`Синхронизация завершена: создано ${res.created}, обновлено ${res.updated}, отключено ${res.disabled}`);
      setCreds({ title: 'Результат синхронизации', rows: res.credentials, errors: res.errors, summary: res });
      load();
    } catch (err) { fail(err); } finally { setSyncing(false); }
  };

  const downloadTemplate = () => downloadCsv('employees-template.csv', [
    ['ФИО', 'Email', 'Телефон'],
    ['Иванов Иван Иванович', 'ivanov@company.ru', '+7 900 123-45-67'],
  ]);

  const downloadCreds = () => downloadCsv('employees-passwords.csv',
    [['ФИО', 'Email (логин)', 'Временный пароль'], ...creds.rows.map((r) => [r.fullName, r.email, r.tempPassword])]);

  return (
    <div className="stack">
      <div className="toolbar">
        <h3>Сотрудники ({items.length})</h3>
        <div className="actions">
          <button className="btn" disabled={syncing} onClick={sync}>{syncing ? 'Синхронизация…' : 'Синхронизировать с Outlook'}</button>
          <button className="btn" onClick={downloadTemplate}>Шаблон CSV</button>
          <input ref={fileRef} type="file" accept=".csv,.txt,text/csv" hidden onChange={onFile} />
          <button className="btn" disabled={importing} onClick={() => fileRef.current.click()}>{importing ? 'Импорт…' : 'Импорт из CSV'}</button>
          <button className="btn btn-primary" onClick={() => { setErrors({}); setEdit({ ...emptyForm }); }}>+ Добавить</button>
        </div>
      </div>

      <div className="card filters">
        <label className="field"><span>Поиск по ФИО или email</span>
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Начните вводить…" /></label>
      </div>

      <div className="card table-wrap">
        {filtered.length === 0 ? <p className="muted">Сотрудников нет. Загрузите CSV или добавьте вручную.</p> : (
          <table>
            <thead><tr><th>ФИО</th><th>Email (логин)</th><th>Телефон</th><th>Статус</th><th /></tr></thead>
            <tbody>
              {filtered.map((e) => (
                <tr key={e.id} className={e.active ? '' : 'cancelled'}>
                  <td><b>{e.fullName}</b></td><td>{e.email}</td><td>{e.phone || '—'}</td>
                  <td>
                    <span className={`badge ${e.active ? 'badge-ACTIVE' : 'badge-CANCELLED'}`}>{e.active ? 'Активен' : 'Отключён'}</span>
                    {e.mustChangePassword && <span className="badge badge-WAIT" title="Ещё не сменил временный пароль">ждёт входа</span>}
                  </td>
                  <td className="actions">
                    <button className="btn" onClick={() => { setErrors({}); setEdit({ ...e }); }}>Изменить</button>
                    <button className="btn" onClick={() => reset(e)}>Сбросить пароль</button>
                    <button className="btn btn-danger" onClick={() => toggleActive(e)}>{e.active ? 'Отключить' : 'Включить'}</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {edit && (
        <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && setEdit(null)}>
          <div className="modal">
            <h3>{edit.id ? 'Редактирование сотрудника' : 'Новый сотрудник'}</h3>
            <label className={`field ${errors.fullName ? 'invalid' : ''}`}><span>ФИО *</span>
              <input value={edit.fullName} onChange={(e) => setEdit({ ...edit, fullName: e.target.value })} />
              {errors.fullName && <small className="err">{errors.fullName}</small>}</label>
            <div className="row">
              <label className={`field ${errors.email ? 'invalid' : ''}`}><span>Корпоративный email (логин) *</span>
                <input value={edit.email} onChange={(e) => setEdit({ ...edit, email: e.target.value })} />
                {errors.email && <small className="err">{errors.email}</small>}</label>
              <label className={`field ${errors.phone ? 'invalid' : ''}`}><span>Телефон</span>
                <input value={edit.phone || ''} onChange={(e) => setEdit({ ...edit, phone: e.target.value })} />
                {errors.phone && <small className="err">{errors.phone}</small>}</label>
            </div>
            {!edit.id && <p className="muted">После сохранения будет показан временный пароль — его нужно передать сотруднику.</p>}
            <div className="actions">
              <button className="btn" onClick={() => setEdit(null)}>Отмена</button>
              <button className="btn btn-primary" onClick={save}>Сохранить</button>
            </div>
          </div>
        </div>
      )}

      {creds && (
        <div className="modal-backdrop">
          <div className="modal modal-wide">
            <h3>{creds.title}</h3>
            {creds.summary && (
              <p>
                Создано: <b>{creds.summary.created}</b> · Обновлено: <b>{creds.summary.updated}</b>
                {creds.summary.disabled !== undefined && <> · Отключено: <b>{creds.summary.disabled}</b></>}
                {' '}· Ошибок: <b>{creds.errors?.length || 0}</b>
              </p>
            )}
            {creds.rows.length > 0 && (
              <>
                <p className="warn">Временные пароли показываются только сейчас и нигде не сохраняются. Скачайте файл или скопируйте их до закрытия окна.
                  При первом входе сотрудник обязан задать собственный пароль.</p>
                <div className="table-wrap scroll-y">
                  <table>
                    <thead><tr><th>ФИО</th><th>Логин (email)</th><th>Временный пароль</th></tr></thead>
                    <tbody>{creds.rows.map((r) => (
                      <tr key={r.email}><td>{r.fullName}</td><td>{r.email}</td><td><code className="pw">{r.tempPassword}</code></td></tr>
                    ))}</tbody>
                  </table>
                </div>
              </>
            )}
            {creds.errors?.length > 0 && (
              <>
                <h4>Строки с ошибками (не импортированы)</h4>
                <div className="table-wrap scroll-y">
                  <table>
                    <thead><tr><th>Строка</th><th>Проблема</th></tr></thead>
                    <tbody>{creds.errors.map((er, i) => <tr key={i}><td>{er.line}</td><td className="err">{er.message}</td></tr>)}</tbody>
                  </table>
                </div>
              </>
            )}
            <div className="actions">
              {creds.rows.length > 0 && <button className="btn" onClick={downloadCreds}>Скачать пароли (CSV)</button>}
              <button className="btn btn-primary" onClick={() => setCreds(null)}>Закрыть</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
