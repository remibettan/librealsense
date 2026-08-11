import { useEffect, useState } from 'react'
import { apiClient } from '../api'

/**
 * Dismissible banner for server-side environment warnings reported by
 * GET /health (e.g. the server is running a Debug SDK build with degraded
 * streaming performance).
 */
export function ServerWarnings() {
  const [warnings, setWarnings] = useState<string[]>([])
  const [dismissed, setDismissed] = useState(false)

  useEffect(() => {
    let cancelled = false
    let attempts = 0
    const fetchWarnings = () => {
      apiClient
        .getHealth()
        .then((h) => {
          if (!cancelled && h.warnings?.length) setWarnings(h.warnings)
        })
        .catch(() => {
          // Backend may still be starting (e.g. desktop build spawns it);
          // retry a few times, then give up — ApiDiagnostics covers hard-down.
          if (!cancelled && ++attempts < 5) setTimeout(fetchWarnings, 3000)
        })
    }
    fetchWarnings()
    return () => {
      cancelled = true
    }
  }, [])

  if (dismissed || warnings.length === 0) return null

  return (
    <div
      role="alert"
      className="bg-amber-500/15 border-b border-amber-500/40 text-amber-300 text-sm px-4 py-2 flex items-start gap-2"
    >
      <span aria-hidden className="mt-0.5">⚠</span>
      <div className="flex-1">
        {warnings.map((w) => (
          <div key={w}>{w}</div>
        ))}
      </div>
      <button
        type="button"
        onClick={() => setDismissed(true)}
        title="Dismiss"
        className="text-amber-300/70 hover:text-amber-200 px-1"
      >
        ✕
      </button>
    </div>
  )
}
