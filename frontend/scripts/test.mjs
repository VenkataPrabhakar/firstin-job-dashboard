/**
 * Test runner shim.
 *
 * CI invokes `npm test -- --watchAll=false` (a Jest-ism baked into the
 * workflow). Vitest rejects unknown CLI flags, so strip anything it does
 * not understand before delegating to `vitest run`.
 */
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const args = process.argv
  .slice(2)
  .filter((a) => a !== '--watchAll' && !a.startsWith('--watchAll='));

const vitestBin = fileURLToPath(new URL('../node_modules/vitest/vitest.mjs', import.meta.url));
const result = spawnSync(process.execPath, [vitestBin, 'run', ...args], { stdio: 'inherit' });
process.exit(result.status ?? 1);
