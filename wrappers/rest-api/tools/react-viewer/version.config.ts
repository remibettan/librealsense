import { readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))

/**
 * Viewer version = librealsense repo version from rs.h: RS2_API
 * major.minor.patch plus the build number (RS2_API_BUILD_VERSION), e.g.
 * "2.58.0.0". Injected into the app as __VIEWER_VERSION__ (see vite/vitest
 * config `define`). Returns "unknown" if rs.h can't be read.
 */
export function viewerVersion(): string {
  try {
    const rs = readFileSync(resolve(here, '../../../../include/librealsense2/rs.h'), 'utf-8')
    const m = (re: RegExp) => rs.match(re)?.[1] ?? '0'
    return [
      m(/RS2_API_MAJOR_VERSION\s+(\d+)/),
      m(/RS2_API_MINOR_VERSION\s+(\d+)/),
      m(/RS2_API_PATCH_VERSION\s+(\d+)/),
      m(/RS2_API_BUILD_VERSION\s+(\d+)/),
    ].join('.')
  } catch {
    return 'unknown'
  }
}
