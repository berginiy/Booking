import { useEffect, useState } from 'react';
import { api } from '../../api.js';
import { useToast } from '../../components/Toast.jsx';
import { EMAIL_RE } from '../../utils.js';

export default function MailAdmin({ onAuthError }) {
  const toast = useToast();
  const [status, setStatus] = useState(null);
  const [to, setTo] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    api.admin.mailStatus().then(setStatus)
      .catch((e) => (e.status === 401 ? onAuthError() : toast.error(e.message)));
  }, []);

  const send = async () => {
    setError('');
    if (!EMAIL_RE.test(to.trim())) return setError('Введите корректный email');
    setSending(true);
    try {
      const res = await api.admin.mailTest(to.trim());
      toast.success(res.message);
    } catch (e) {
      if (e.status === 401) return onAuthError();
      setError(e.message);
    } finally { setSending(false); }
  };

  return (
    <div className="stack">
      <h3>Почтовые уведомления</h3>
      <div className="card">
        {status ? (
          <dl className="kv kv-narrow">
            <dt>Состояние</dt>
            <dd><span className={`badge ${status.enabled ? 'badge-ACTIVE' : 'badge-CANCELLED'}`}>{status.enabled ? 'Включены' : 'Отключены'}</span></dd>
            <dt>SMTP-сервер</dt><dd>{status.host}:{status.port}</dd>
            <dt>Отправитель</dt><dd>{status.from}</dd>
            <dt>Адрес сайта в письмах</dt><dd>{status.siteUrl}</dd>
          </dl>
        ) : <p className="muted">Загрузка…</p>}
        {status && !status.enabled && (
          <div className="notice notice-warn">
            Отправка выключена. Задайте переменную окружения MAIL_ENABLED=true (и параметры MAIL_HOST, MAIL_PORT и другие) и перезапустите backend.
          </div>
        )}
      </div>

      <div className="card">
        <h3>Проверка отправки</h3>
        <p className="muted">Отправит тестовое письмо и покажет ошибку подключения, если она есть.</p>
        <div className="row">
          <label className={`field ${error ? 'invalid' : ''}`}>
            <span>Адрес получателя</span>
            <input value={to} onChange={(e) => setTo(e.target.value)} placeholder="name@company.ru"
                   onKeyDown={(e) => e.key === 'Enter' && send()} />
            {error && <small className="err">{error}</small>}
          </label>
          <div className="field field-btn">
            <button className="btn btn-primary" disabled={sending || !status?.enabled} onClick={send}>
              {sending ? 'Отправка…' : 'Отправить тестовое письмо'}
            </button>
          </div>
        </div>
      </div>

    </div>
  );
}
