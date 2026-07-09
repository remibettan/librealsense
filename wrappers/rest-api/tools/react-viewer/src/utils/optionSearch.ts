import Fuse from 'fuse.js'
import { OptionInfo } from '../api/types'
import { getAliases } from './optionAliases'

interface SearchRecord {
  index: number
  name: string
  aliases: string[]
  option_id: string
}

/**
 * Fuzzy-filter camera options by a free-text query.
 *
 * Two independent match paths, unioned:
 * 1. Fuzzy match (fuse.js) over the control name, curated aliases and raw
 *    option id — tolerates typos. Aliases are kept as separate array entries so
 *    each is scored on its own (a joined string wrecks bitap scoring and makes
 *    exact aliases score poorly). A tight threshold keeps loose partial hits
 *    (e.g. "laser" fuzzily grazing "Filter") out.
 * 2. Category substring — typing a section name ("post") reveals every control
 *    in that category.
 *
 * Description is intentionally NOT searched: descriptions mention many unrelated
 * terms (the sync-mode description literally contains "laser"), which produced
 * confusing false hits.
 *
 * An empty/whitespace query returns the input unchanged. Matches are returned in
 * the original array order so downstream category grouping/order is preserved.
 */
export function filterOptions(options: OptionInfo[], query: string): OptionInfo[] {
  const q = query.trim()
  if (!q) return options

  const records: SearchRecord[] = options.map((option, index) => ({
    index,
    name: option.name,
    aliases: getAliases(option),
    option_id: option.option_id,
  }))

  const fuse = new Fuse(records, {
    keys: [
      { name: 'name', weight: 0.6 },
      { name: 'aliases', weight: 0.35 },
      { name: 'option_id', weight: 0.05 },
    ],
    threshold: 0.35,
    ignoreLocation: true,
    minMatchCharLength: 2,
  })

  const matched = new Set<number>()
  const ql = q.toLowerCase()
  if (ql.length >= 2) {
    options.forEach((o, i) => {
      if (o.category.toLowerCase().includes(ql)) matched.add(i)
    })
  }
  for (const r of fuse.search(q)) matched.add(r.item.index)

  return [...matched].sort((a, b) => a - b).map(i => options[i])
}
