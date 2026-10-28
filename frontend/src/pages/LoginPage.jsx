import { useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';

export default function LoginPage() {
  const toast = useToast();
  const { signIn } = useAuth();
  const [form, setForm] = useState({ username: '', password: '' });
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!form.username.trim() || !form.password) return toast.error('Введите логин и пароль');
    setBusy(true);
    try {
      signIn(await api.login({ username: form.username.trim(), password: form.password }));
    } catch (err) {
      toast.error(err.message);
    } finally { setBusy(false); }
  };

  return (
    <div className="auth">
      <aside className="auth-side">
        <div className="brand">
          <span className="brand-mark" />
          <span className="brand-text">Конференц-залы<small>Головной офис</small></span>
        </div>
        <div>
          <h1>Бронирование переговорных без накладок</h1>
          <p>Выберите этаж, зал и время. Система сама проверит, что зал свободен.</p>
        </div>
        <ul className="auth-points">
          <li>Актуальная занятость залов на всех этажах</li>
          <li>Изменение и отмена своих бронирований</li>
          <li>Защита от двойного бронирования</li>
        </ul>
      </aside>
      <form className="auth-form" onSubmit={submit}>
      <h2>Вход в систему</h2>
      <p className="muted">Сотрудники входят по корпоративной почте. Пароль выдаёт администратор.</p>
      <label className="field"><span>Email (сотрудник) или логин (администратор)</span>
        <input autoFocus autoComplete="username" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></label>
      <label className="field"><span>Пароль</span>
        <input type="password" autoComplete="current-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
      <button className="btn btn-primary btn-lg" disabled={busy}>{busy ? 'Вход…' : 'Войти'}</button>
      </form>
    </div>
  );
}
