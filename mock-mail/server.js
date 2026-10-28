const net = require('net');
const http = require('http');

const SMTP_PORT = Number(process.env.SMTP_PORT) || 2525;
const HTTP_PORT = Number(process.env.HTTP_PORT) || 8025;
const MAX_MESSAGES = 500;

const messages = [];
let nextId = 1;

const qpDecode = (s) => Buffer.from(
  s.replace(/=\r?\n/g, '').replace(/=([0-9A-F]{2})/gi, (m, h) => String.fromCharCode(parseInt(h, 16))), 'latin1');

function decodeWords(value) {
  return (value || '')
    .replace(/\?=\s+=\?/g, '?==?')
    .replace(/=\?([^?]+)\?([bBqQ])\?([^?]*)\?=/g, (m, charset, enc, text) => {
      const bytes = enc.toUpperCase() === 'B'
        ? Buffer.from(text, 'base64')
        : qpDecode(text.replace(/_/g, ' '));
      try { return new TextDecoder(charset).decode(bytes); } catch { return bytes.toString('utf8'); }
    });
}

function parseHeaders(block) {
  const headers = {};
  block.replace(/\r\n[ \t]+/g, ' ').split('\r\n').forEach((line) => {
    const i = line.indexOf(':');
    if (i > 0) headers[line.slice(0, i).toLowerCase()] = line.slice(i + 1).trim();
  });
  return headers;
}

function splitMessage(raw) {
  const i = raw.indexOf('\r\n\r\n');
  return i < 0 ? [raw, ''] : [raw.slice(0, i), raw.slice(i + 4)];
}

function parsePart(raw) {
  const [headBlock, body] = splitMessage(raw);
  const headers = parseHeaders(headBlock);
  const contentType = headers['content-type'] || 'text/plain';
  const type = contentType.split(';')[0].trim().toLowerCase();
  if (type.startsWith('multipart/')) {
    const boundary = (contentType.match(/boundary="?([^";]+)"?/i) || [])[1];
    const parts = boundary
      ? body.split('--' + boundary).slice(1).filter((p) => !p.startsWith('--'))
          .map((p) => parsePart(p.replace(/^\r\n/, '').replace(/\r\n$/, '')))
      : [];
    return { type, parts };
  }
  const encoding = (headers['content-transfer-encoding'] || '7bit').toLowerCase();
  const bytes = encoding === 'base64' ? Buffer.from(body.replace(/\s+/g, ''), 'base64')
    : encoding === 'quoted-printable' ? qpDecode(body) : Buffer.from(body, 'latin1');
  const charset = (contentType.match(/charset="?([^";]+)"?/i) || [])[1] || 'utf-8';
  const disposition = headers['content-disposition'] || '';
  const name = (disposition.match(/filename\*?="?([^";]+)"?/i) || contentType.match(/name="?([^";]+)"?/i) || [])[1];
  return { type, bytes, charset, name: name ? decodeWords(name) : null, attachment: /attachment/i.test(disposition) || !!name };
}

function flatten(part, out = []) {
  if (part.parts) part.parts.forEach((p) => flatten(p, out)); else out.push(part);
  return out;
}

function decodeBody(part) {
  try { return new TextDecoder(part.charset).decode(part.bytes); } catch { return part.bytes.toString('utf8'); }
}

function store(raw, envelope) {
  const text = raw.toString('latin1');
  const [headBlock] = splitMessage(text);
  const headers = parseHeaders(headBlock);
  const leaves = flatten(parsePart(text));
  const html = leaves.find((p) => p.type === 'text/html' && !p.attachment);
  const plain = leaves.find((p) => p.type === 'text/plain' && !p.attachment);
  const attachments = leaves.filter((p) => p.attachment || (!p.type.startsWith('text/') && p.bytes));
  messages.unshift({
    id: nextId++,
    receivedAt: new Date().toISOString(),
    envelopeFrom: envelope.from,
    envelopeTo: envelope.to,
    from: decodeWords(headers.from),
    to: decodeWords(headers.to),
    subject: decodeWords(headers.subject),
    html: html ? decodeBody(html) : null,
    text: plain ? decodeBody(plain) : null,
    attachments: attachments.map((a) => ({ name: a.name || 'attachment', type: a.type, size: a.bytes.length, bytes: a.bytes })),
  });
  if (messages.length > MAX_MESSAGES) messages.length = MAX_MESSAGES;
}

const smtp = net.createServer((socket) => {
  let buffer = Buffer.alloc(0);
  let dataMode = false;
  let envelope = { from: '', to: [] };
  const reply = (line) => socket.write(line + '\r\n');

  const command = (line) => {
    const upper = line.toUpperCase();
    if (upper.startsWith('EHLO')) { reply('250-mock-mail'); reply('250 8BITMIME'); }
    else if (upper.startsWith('HELO')) reply('250 mock-mail');
    else if (upper.startsWith('MAIL FROM')) { envelope = { from: (line.match(/<([^>]*)>/) || [])[1] || '', to: [] }; reply('250 OK'); }
    else if (upper.startsWith('RCPT TO')) { envelope.to.push((line.match(/<([^>]*)>/) || [])[1] || ''); reply('250 OK'); }
    else if (upper === 'DATA') { dataMode = true; reply('354 End data with <CR><LF>.<CR><LF>'); }
    else if (upper === 'RSET') { envelope = { from: '', to: [] }; reply('250 OK'); }
    else if (upper === 'NOOP') reply('250 OK');
    else if (upper === 'QUIT') { reply('221 Bye'); socket.end(); }
    else reply('502 Command not implemented');
  };

  socket.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    for (;;) {
      if (!dataMode) {
        const i = buffer.indexOf('\r\n');
        if (i < 0) return;
        const line = buffer.subarray(0, i).toString('latin1');
        buffer = buffer.subarray(i + 2);
        command(line);
      } else {
        const end = buffer.indexOf('\r\n.\r\n');
        if (end < 0) return;
        const body = buffer.subarray(0, end + 2).toString('latin1').replace(/(^|\r\n)\.\./g, '$1.');
        buffer = buffer.subarray(end + 5);
        dataMode = false;
        try { store(Buffer.from(body, 'latin1'), envelope); } catch (e) { console.error('Ошибка разбора письма:', e); }
        reply('250 OK: queued');
      }
    }
  });
  socket.on('error', () => {});
  reply('220 mock-mail ready');
});

const summary = (m) => ({
  id: m.id, receivedAt: m.receivedAt, from: m.from, to: m.to, subject: m.subject,
  envelopeFrom: m.envelopeFrom, envelopeTo: m.envelopeTo, attachments: m.attachments.length,
});

const web = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const json = (status, body) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(body)); };
  let m;
  if (req.method === 'GET' && url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    return res.end(require('fs').readFileSync(require('path').join(__dirname, 'index.html'), 'utf8'));
  }
  if (req.method === 'GET' && url.pathname === '/api/messages') return json(200, messages.map(summary));
  if (req.method === 'DELETE' && url.pathname === '/api/messages') { messages.length = 0; return json(200, { ok: true }); }
  if ((m = url.pathname.match(/^\/api\/messages\/(\d+)$/)) && req.method === 'GET') {
    const msg = messages.find((x) => x.id === Number(m[1]));
    if (!msg) return json(404, { error: 'not found' });
    return json(200, { ...summary(msg), html: msg.html, text: msg.text, attachments: msg.attachments.map(({ name, type, size }) => ({ name, type, size })) });
  }
  if ((m = url.pathname.match(/^\/api\/messages\/(\d+)\/attachments\/(\d+)$/)) && req.method === 'GET') {
    const msg = messages.find((x) => x.id === Number(m[1]));
    const att = msg && msg.attachments[Number(m[2])];
    if (!att) return json(404, { error: 'not found' });
    res.writeHead(200, { 'Content-Type': att.type, 'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(att.name)}` });
    return res.end(att.bytes);
  }
  json(404, { error: 'not found' });
});

smtp.listen(SMTP_PORT, () => console.log(`SMTP: localhost:${SMTP_PORT}`));
web.listen(HTTP_PORT, () => console.log(`Тестовая почта (веб-интерфейс): http://localhost:${HTTP_PORT}`));
