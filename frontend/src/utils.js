export const STEP = 30; // шаг выбора времени, минут

const pad = (n) => String(n).padStart(2, '0');
export const toMin = (t) => {
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
};
export const fromMin = (m) => `${pad(Math.floor(m / 60))}:${pad(m % 60)}`;

export const todayStr = () => {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

export const fmtDate = (s) =>
  new Date(s + 'T00:00:00').toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric', weekday: 'short' });

export const fmtDateTime = (iso) => new Date(iso).toLocaleString('ru-RU');

export const isPastBooking = (b) => new Date(`${b.date}T${b.endTime}:00`) < new Date();

export const PHONE_RE = /^\+?[0-9][0-9()\-\s]{6,19}$/;
export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function downloadCsv(filename, rows) {
  const esc = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const text = '\ufeff' + rows.map((r) => r.map(esc).join(';')).join('\r\n');
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export const STATUS_LABEL = { ACTIVE: 'Активно', CANCELLED: 'Отменено' };
