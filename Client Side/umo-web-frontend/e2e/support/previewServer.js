import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { createServer } from 'node:http'
import { extname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const host = '127.0.0.1'
const port = 4173
const root = resolve(process.cwd(), 'dist')
const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
}

let server

export function startPreviewServer() {
  server = createServer(async (request, response) => {
    const pathname = decodeURIComponent(
      new URL(request.url, `http://${host}:${port}`).pathname,
    )
    const requestedPath = resolve(root, `.${pathname}`)
    const withinRoot = requestedPath === root || requestedPath.startsWith(root + sep)
    let filePath = withinRoot ? requestedPath : null

    if (filePath) {
      try {
        const fileStat = await stat(filePath)
        if (fileStat.isDirectory()) {
          filePath = resolve(filePath, 'index.html')
        }
      } catch {
        filePath = resolve(root, 'index.html')
      }
    }

    if (!filePath) {
      response.writeHead(403)
      response.end()
      return
    }

    response.writeHead(200, {
      'Content-Type': contentTypes[extname(filePath)] ?? 'application/octet-stream',
    })
    createReadStream(filePath).pipe(response)
  })

  return new Promise((resolvePromise, reject) => {
    server.once('error', reject)
    server.listen(port, host, () => {
      server.off('error', reject)
      resolvePromise()
    })
  })
}

export function stopPreviewServer() {
  return new Promise((resolvePromise) => {
    if (!server?.listening) {
      resolvePromise()
      return
    }
    server.close(resolvePromise)
  })
}

function shutdown() {
  stopPreviewServer().finally(() => process.exit(0))
  setTimeout(() => process.exit(0), 2_000).unref()
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.on('SIGINT', shutdown)
  process.on('SIGTERM', shutdown)
  startPreviewServer().catch(() => process.exit(1))
}
