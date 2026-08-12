import type { OptionInfo } from '../api/types'

/**
 * Case-insensitive substring filter over camera options.
 *
 * Mirrors the C++ viewer's control search (`common/device-model.cpp`): the query
 * is matched against the control name, plus the labels a control is grouped
 * under - its category ("post" reveals Post-Processing) and its post-processing
 * filter name ("spatial" reveals the Spatial Filter parameters).
 *
 * No fuzzy matching: a query only matches text the user can actually see, so
 * there are no results without the typed term in them.
 *
 * Description is intentionally NOT searched: descriptions mention many unrelated
 * terms (the sync-mode description literally contains "laser"), which produced
 * confusing false hits.
 *
 * An empty/whitespace query returns the input unchanged. Matches are returned in
 * the original array order so downstream category grouping/order is preserved.
 */
export function filterOptions(options: OptionInfo[], query: string): OptionInfo[] {
  const q = query.trim().toLowerCase()
  if (!q) return options

  // Group labels only kick in from two characters: a single letter would drag in
  // every control of a section whose name happens to contain it.
  const matchGroups = q.length >= 2

  return options.filter(option =>
    option.name.toLowerCase().includes(q)
    || (matchGroups && (option.category || '').toLowerCase().includes(q))
    || (matchGroups && (option.filter_name || '').toLowerCase().includes(q))
  )
}
