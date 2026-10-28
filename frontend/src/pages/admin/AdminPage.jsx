import { useEffect, useState } from 'react';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import RoomsAdmin from './RoomsAdmin.jsx';
import BookingsAdmin from './BookingsAdmin.jsx';
import EmployeesAdmin from './EmployeesAdmin.jsx';
import MailAdmin from './MailAdmin.jsx';
import AuditAdmin from './AuditAdmin.jsx';

const TABS = [['bookings', 'Бронирования'], ['rooms', 'Залы'], ['employees', 'Сотрудники'], ['mail', 'Почта'], ['audit', 'Журнал']];

export default function AdminPage() {
  const { logout } = useAuth();
  const [tab, setTab] = useState('bookings');
  const [cfg, setCfg] = useState(null);

  useEffect(() => { api.config().then(setCfg).catch(() => {}); }, []);

  return (
    <div className="stack">
      <h2>Панель администратора</h2>
      <div className="seg">
        {TABS.map(([k, label]) => <button key={k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{label}</button>)}
      </div>
      {tab === 'bookings' && <BookingsAdmin cfg={cfg} onAuthError={logout} />}
      {tab === 'rooms' && <RoomsAdmin cfg={cfg} onAuthError={logout} />}
      {tab === 'employees' && <EmployeesAdmin onAuthError={logout} />}
      {tab === 'mail' && <MailAdmin onAuthError={logout} />}
      {tab === 'audit' && <AuditAdmin onAuthError={logout} />}
    </div>
  );
}
