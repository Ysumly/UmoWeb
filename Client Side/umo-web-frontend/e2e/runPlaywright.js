import { spawn } from 'node:child_process'
import { startPreviewServer, stopPreviewServer } from './support/previewServer.js'

await startPreviewServer()

const playwright = spawn(
  process.execPath,
  ['./node_modules/@playwright/test/cli.js', 'test', ...process.argv.slice(2)],
  {
    env: {
      ...process.env,
      PLAYWRIGHT_EXTERNAL_SERVER: '1',
    },
    stdio: 'inherit',
    windowsHide: true,
  },
)

const exitCode = await new Promise((resolve) => {
  playwright.on('error', () => resolve(1))
  playwright.on('exit', (code) => resolve(code ?? 1))
})

await stopPreviewServer()
process.exit(exitCode)
