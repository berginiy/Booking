import { useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';

export default function ChangePasswordPage() {
  const toast = useToast();
  const { signIn } = useAuth();
  const [f, setF] = useState({ currentPassword: '', newPassword: '', repeat: '' });
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!f.currentPassword) return toast.error('Введите текущий (временный) пароль');
    if (f.newPassword.length < 8) return toast.error('Новый пароль должен быть не короче 8 символов');
    if (f.newPassword !== f.repeat) return toast.error('Пароли не совпадают');
    setBusy(true);
    try {
      signIn(await api.changePassword({ currentPassword: f.currentPassword, newPassword: f.newPassword }));
      toast.success('Пароль изменён');
    } catch (err) {
      toast.error(err.fieldErrors?.newPassword || err.message);
    } finally { setBusy(false); }
  };

  return (
    <form className="card login" onSubmit={submit}>
      <h2>Смена пароля</h2>
      <p className="muted">Это первый вход с временным паролем. Придумайте свой пароль, чтобы продолжить.</p>
      <label className="field"><span>Временный пароль</span>
        <input type="password" autoComplete="current-password" value={f.currentPassword} onChange={(e) => setF({ ...f, currentPassword: e.target.value })} /></label>
      <label className="field"><span>Новый пароль (не короче 8 символов)</span>
        <input type="password" autoComplete="new-password" value={f.newPassword} onChange={(e) => setF({ ...f, newPassword: e.target.value })} /></label>
      <label className="field"><span>Повторите новый пароль</span>
        <input type="password" autoComplete="new-password" value={f.repeat} onChange={(e) => setF({ ...f, repeat: e.target.value })} /></label>
      <button className="btn btn-primary" disabled={busy}>{busy ? 'Сохранение…' : 'Сменить пароль'}</button>
    </form>
  );
}
