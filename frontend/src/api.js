const KEY = 'session';

const read = () => {
  try {
    return JSON.parse(localStorage.getItem(KEY)) || {};
  } catch {
    return {};
  }
};

export const session = {
  get token() { return read().token || null; },
  get user() { return read().user || null; },
  set(token, user) { localStorage.setItem(KEY, JSON.stringify({ token, user })); },
  setUser(user) { localStorage.setItem(KEY, JSON.stringify({ token: this.token, user })); },
  clear() { localStorage.removeItem(KEY); },
};

let onUnauthorized = () => { };
export const setUnauthorizedHandler = (fn) => { onUnauthorized = fn; };

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || 'Ошибка запроса');
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.fieldErrors || {};
  }
}


const API_BASE = import.meta.env.VITE_API_URL || '';

async function request(method, path, { body, params, auth = true } = {}) {
  const url = new URL(API_BASE + '/api' + path, window.location.origin);

  Object.entries(params || {}).forEach(([k, v]) => {
    if (v !== '' && v !== null && v !== undefined) url.searchParams.set(k, v);
  });

  const isForm = body instanceof FormData;
  const headers = {};
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';

  const token = session.token;
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, { message: 'Сервер недоступен. Проверьте подключение.' });
  }

  if (res.status === 204) return null;

  const data = await res.json().catch(() => null);

  if (!res.ok) {
    if (res.status === 401 && auth && token) onUnauthorized();
    throw new ApiError(res.status, data);
  }

  return data;
}

export const api = {
  config: () => request('GET', '/config', { auth: false }),
  login: (body) => request('POST', '/auth/login', { body, auth: false }),
  me: () => request('GET', '/auth/me'),
  changePassword: (body) => request('POST', '/auth/change-password', { body }),

  employees: () => request('GET', '/employees'),
  rooms: (floor) => request('GET', '/rooms', { params: { floor } }),
  bookings: (params) => request('GET', '/bookings', { params }),
  myBookings: () => request('GET', '/bookings/mine'),
  createBooking: (body) => request('POST', '/bookings', { body }),
  updateBooking: (id, body) => request('PUT', `/bookings/${id}`, { body }),
  cancelBooking: (id) => request('DELETE', `/bookings/${id}`),

  admin: {
    rooms: () => request('GET', '/admin/rooms'),
    createRoom: (body) => request('POST', '/admin/rooms', { body }),
    updateRoom: (id, body) => request('PUT', `/admin/rooms/${id}`, { body }),
    setRoomActive: (id, active) => request('PATCH', `/admin/rooms/${id}/status`, { body: { active } }),
    deleteRoom: (id) => request('DELETE', `/admin/rooms/${id}`),
    bookings: (params) => request('GET', '/admin/bookings', { params }),
    audit: () => request('GET', '/admin/audit'),

    employees: () => request('GET', '/admin/employees'),
    createEmployee: (body) => request('POST', '/admin/employees', { body }),
    updateEmployee: (id, body) => request('PUT', `/admin/employees/${id}`, { body }),
    mailStatus: () => request('GET', '/admin/mail'),
    mailTest: (to) => request('POST', '/admin/mail/test', { body: { to } }),
    syncEmployees: () => request('POST', '/admin/employees/sync'),
    resetPassword: (id) => request('POST', `/admin/employees/${id}/reset-password`),
    importEmployees: (file) => {
      const fd = new FormData();
      fd.append('file', file);
      return request('POST', '/admin/employees/import', { body: fd });
    },
  },
};