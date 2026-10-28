const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT) || 9000;
const TENANT = process.env.TENANT_ID || 'test-tenant';
const CLIENT_ID = process.env.CLIENT_ID || 'test-client';
const CLIENT_SECRET = process.env.CLIENT_SECRET || 'test-secret';
const DB_FILE = path.join(__dirname, 'data.json');

let db = { users: [], events: [] };
try { db = JSON.parse(fs.readFileSync(DB_FILE, 'utf8')); } catch {}
const save = () => fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));

const send = (res, status, body, type = 'application/json; charset=utf-8') => {
  res.writeHead(status, { 'Content-Type': type });
  res.end(body === undefined ? '' : typeof body === 'string' ? body : JSON.stringify(body));
};
const graphError = (res, status, code, message) => send(res, status, { error: { code, message } });
const readBody = (req) => new Promise((resolve) => {
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => resolve(Buffer.concat(chunks)));
});
const readJson = async (req) => {
  try { return JSON.parse((await readBody(req)).toString('utf8') || '{}'); } catch { return {}; }
};

function decodeText(buf) {
  try { return new TextDecoder('utf-8', { fatal: true }).decode(buf).replace(/^\uFEFF/, ''); }
  catch { return new TextDecoder('windows-1251').decode(buf); }
}

function splitLine(line, sep) {
  const out = [];
  let cur = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quoted) {
      if (c === '"') {
        if (line[i + 1] === '"') { cur += '"'; i++; } else quoted = false;
      } else cur += c;
    } else if (c === '"') quoted = true;
    else if (c === sep) { out.push(cur); cur = ''; }
    else cur += c;
  }
  out.push(cur);
  return out;
}

function importCsv(text, replace) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim());
  const head = lines[0] || '';
  const count = (ch) => head.split(ch).length - 1;
  const sep = count(';') >= count(',') && count(';') >= count('\t') && count(';') > 0 ? ';' : count('\t') > count(',') ? '\t' : ',';
  if (replace) db.users = [];
  const result = { added: 0, updated: 0, errors: [] };
  lines.forEach((line, i) => {
    const c = splitLine(line, sep).map((s) => s.trim());
    if (i === 0 && !(c[1] || '').includes('@')) return;
    const [name, mail, phone, department, title] = c;
    if (!name || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail || '')) {
      result.errors.push({ line: i + 1, message: 'Нужны ФИО и корректный email' });
      return;
    }
    const email = mail.toLowerCase();
    const parts = name.split(/\s+/);
    const fields = {
      displayName: name, surname: parts[0] || null, givenName: parts[1] || null,
      mail: email, userPrincipalName: email, mobilePhone: phone || null, businessPhones: [],
      department: department || null, jobTitle: title || null,
    };
    const existing = db.users.find((u) => u.mail === email);
    if (existing) { Object.assign(existing, fields); result.updated++; }
    else { db.users.push({ id: crypto.randomUUID(), ...fields, accountEnabled: true }); result.added++; }
  });
  save();
  return result;
}

const project = (user, select) => {
  if (!select) return user;
  const out = { id: user.id };
  select.split(',').map((s) => s.trim()).forEach((k) => { if (k in user) out[k] = user[k]; });
  return out;
};
const findUser = (key) => {
  const k = decodeURIComponent(key).toLowerCase();
  return db.users.find((u) => u.id.toLowerCase() === k || u.mail === k);
};

async function route(req, res) {
  const host = req.headers.host;
  const url = new URL(req.url, `http://${host}`);
  const p = url.pathname;
  const m = req.method;
  let match;

  if (m === 'GET' && p === '/') {
    return send(res, 200, fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8'), 'text/html; charset=utf-8');
  }

  if (m === 'POST' && (match = p.match(/^\/([^/]+)\/oauth2\/v2\.0\/token$/))) {
    const form = new URLSearchParams((await readBody(req)).toString('utf8'));
    if (form.get('grant_type') !== 'client_credentials') return send(res, 400, { error: 'unsupported_grant_type' });
    if (match[1] !== TENANT) return send(res, 400, { error: 'invalid_request', error_description: 'Unknown tenant ' + match[1] });
    if (form.get('client_id') !== CLIENT_ID || form.get('client_secret') !== CLIENT_SECRET) {
      return send(res, 401, { error: 'invalid_client', error_description: 'Invalid client id or secret' });
    }
    return send(res, 200, { token_type: 'Bearer', expires_in: 3600, access_token: 'mock-' + crypto.randomBytes(16).toString('hex') });
  }

  if (p.startsWith('/mock/')) {
    if (m === 'GET' && p === '/mock/state') {
      return send(res, 200, {
        users: db.users, events: db.events,
        config: { baseUrl: `http://${host}/v1.0`, authority: `http://${host}`, tenantId: TENANT, clientId: CLIENT_ID, clientSecret: CLIENT_SECRET },
      });
    }
    if (m === 'POST' && p === '/mock/users/import') {
      const result = importCsv(decodeText(await readBody(req)), url.searchParams.get('replace') === '1');
      return send(res, 200, result);
    }
    if (m === 'POST' && p === '/mock/reset') {
      db = { users: [], events: [] };
      save();
      return send(res, 200, { ok: true });
    }
    if ((match = p.match(/^\/mock\/users\/([^/]+)$/))) {
      const user = findUser(match[1]);
      if (!user) return send(res, 404, { error: 'not found' });
      if (m === 'PATCH') {
        const body = await readJson(req);
        if (typeof body.accountEnabled === 'boolean') user.accountEnabled = body.accountEnabled;
        save();
        return send(res, 200, user);
      }
      if (m === 'DELETE') {
        db.users = db.users.filter((u) => u.id !== user.id);
        save();
        return send(res, 204);
      }
    }
    return send(res, 404, { error: 'not found' });
  }

  if (p.startsWith('/v1.0/')) {
    if (!/^Bearer mock-/.test(req.headers.authorization || '')) {
      return graphError(res, 401, 'InvalidAuthenticationToken', 'Access token is empty or invalid.');
    }

    if (m === 'GET' && p === '/v1.0/users') {
      const top = Math.min(Number(url.searchParams.get('$top')) || 100, 999);
      const skip = Number(url.searchParams.get('$skiptoken')) || 0;
      const select = url.searchParams.get('$select');
      const body = {
        '@odata.context': `http://${host}/v1.0/$metadata#users`,
        value: db.users.slice(skip, skip + top).map((u) => project(u, select)),
      };
      if (skip + top < db.users.length) {
        const next = new URL(url);
        next.searchParams.set('$skiptoken', String(skip + top));
        body['@odata.nextLink'] = next.toString();
      }
      return send(res, 200, body);
    }

    if (m === 'GET' && (match = p.match(/^\/v1\.0\/users\/([^/]+)$/))) {
      const user = findUser(match[1]);
      return user ? send(res, 200, user) : graphError(res, 404, 'Request_ResourceNotFound', 'User not found.');
    }

    if ((match = p.match(/^\/v1\.0\/users\/([^/]+)\/events(?:\/([^/]+))?$/))) {
      const user = findUser(match[1]);
      if (!user) return graphError(res, 404, 'Request_ResourceNotFound', 'User not found.');
      const eventId = match[2];
      if (!eventId && m === 'GET') return send(res, 200, { value: db.events.filter((e) => e.userId === user.id) });
      if (!eventId && m === 'POST') {
        const event = { id: crypto.randomUUID(), userId: user.id, ...(await readJson(req)), createdDateTime: new Date().toISOString() };
        db.events.push(event);
        save();
        return send(res, 201, event);
      }
      const event = db.events.find((e) => e.id === eventId && e.userId === user.id);
      if (!event) return graphError(res, 404, 'ErrorItemNotFound', 'The specified object was not found in the store.');
      if (m === 'PATCH') {
        Object.assign(event, await readJson(req));
        save();
        return send(res, 200, event);
      }
      if (m === 'DELETE') {
        db.events = db.events.filter((e) => e.id !== event.id);
        save();
        return send(res, 204);
      }
    }
    return graphError(res, 404, 'BadRequest', 'Unsupported request.');
  }

  send(res, 404, { error: 'not found' });
}

http.createServer(async (req, res) => {
  try { await route(req, res); } catch (e) { console.error(e); send(res, 500, { error: String(e.message) }); }
}).listen(PORT, () => {
  console.log(`Тестовый Outlook (эмулятор Microsoft Graph): http://localhost:${PORT}`);
  console.log(`Пользователей в каталоге: ${db.users.length}`);
});
