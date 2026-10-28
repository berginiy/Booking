import { useEffect, useState } from 'react';
import { api } from '../../api.js';
import { useToast } from '../../components/Toast.jsx';
import { fmtDateTime } from '../../utils.js';

export default function AuditAdmin({ onAuthError }) {
  const toast = useToast();
  const [items, setItems] = useState([]);
  useEffect(() => {
    api.admin.audit().then(setItems).catch((e) => (e.status === 401 ? onAuthError() : toast.error(e.message)));
  }, []);

  return (
    <div className="stack">
      <h3>Журнал операций (последние 200)</h3>
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Время</th><th>Пользователь</th><th>Операция</th><th>Объект</th><th>Результат</th><th>Детали</th></tr></thead>
          <tbody>
            {items.map((a) => (
              <tr key={a.id}>
                <td>{fmtDateTime(a.occurredAt)}</td><td>{a.actor}</td><td>{a.operation}</td>
                <td>{a.entityType}{a.entityId ? ` #${a.entityId}` : ''}</td>
                <td><span className={`badge ${a.result === 'SUCCESS' ? 'badge-ACTIVE' : 'badge-CANCELLED'}`}>{a.result}</span></td>
                <td>{a.details}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
