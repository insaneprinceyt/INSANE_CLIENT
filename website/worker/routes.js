// Large release files stay in R2 and are streamed directly to the browser.
async function download(request, env, release) {
  const meta = await env.BUCKET.head(release.key);
  if (!meta || meta.size !== release.size || meta.customMetadata?.sha256 !== release.sha256) {
    return new Response('The download is being prepared. Please try again shortly.', {status: 503, headers: {'Retry-After': '30'}});
  }
  const headers = new Headers({
    'Content-Type': release.type,
    'Content-Disposition': `attachment; filename="${release.filename}"`,
    'Content-Length': String(meta.size),
    'Accept-Ranges': 'bytes',
    'ETag': meta.httpEtag,
    'X-Content-Type-Options': 'nosniff',
    'Cache-Control': 'private, no-store'
  });
  let range;
  const requested = request.headers.get('Range');
  const ifRange = request.headers.get('If-Range');
  if (request.method === 'GET' && requested && (!ifRange || ifRange === meta.httpEtag)) {
    const match = /^bytes=(\d*)-(\d*)$/.exec(requested);
    let start, end;
    if (match && (match[1] || match[2])) {
      if (match[1]) {
        start = Number(match[1]);
        end = match[2] ? Math.min(Number(match[2]), meta.size - 1) : meta.size - 1;
      } else {
        const suffix = Number(match[2]);
        start = Math.max(0, meta.size - suffix);
        end = meta.size - 1;
      }
    }
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end || start >= meta.size) {
      headers.delete('Content-Length');
      headers.set('Content-Range', `bytes */${meta.size}`);
      return new Response(null, {status: 416, headers});
    }
    range = {offset: start, length: end - start + 1};
    headers.set('Content-Length', String(range.length));
    headers.set('Content-Range', `bytes ${start}-${end}/${meta.size}`);
  }
  if (request.method === 'HEAD') return new Response(null, {headers});
  const object = await env.BUCKET.get(release.key, range ? {range} : undefined);
  if (!object) return new Response('Download not found.', {status: 404});
  return new Response(object.body, {status: range ? 206 : 200, headers});
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!['GET', 'HEAD'].includes(request.method)) {
      return new Response('Method not allowed', {status: 405, headers: {'Allow': 'GET, HEAD'}});
    }
    const release = Object.values(RELEASES).find(file => file.path === url.pathname);
    if (release) return download(request, env, release);
    const path = url.pathname === '/' ? '/android.html' : url.pathname.replace(/\/$/, '') === '/preview' ? '/index.html' : url.pathname.replace(/\/$/, '') === '/android' ? '/android.html' : url.pathname;
    const asset = ASSETS[path];
    if (!asset) return new Response('Not found', {status: 404});
    const bytes = Uint8Array.from(atob(asset.body), c => c.charCodeAt(0));
    return new Response(request.method === 'HEAD' ? null : bytes, {headers: {
      'Content-Type': asset.type,
      'Content-Length': String(bytes.length),
      'Cache-Control': 'private, no-cache',
      'X-Content-Type-Options': 'nosniff',
      ...(path.startsWith('/download/') ? {'Content-Disposition': `attachment; filename="${path.split('/').pop()}"`} : {})
    }});
  }
};
