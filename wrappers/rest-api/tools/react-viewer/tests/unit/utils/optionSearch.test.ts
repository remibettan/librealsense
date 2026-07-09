import { describe, it, expect } from 'vitest'
import { filterOptions } from '@/utils/optionSearch'
import { createMockOption } from '../../utils/test-utils'

const exposure = createMockOption({ option_id: 'Exposure', name: 'Exposure', category: 'Basic Controls' })
const gain = createMockOption({ option_id: 'Gain', name: 'Gain', category: 'Basic Controls' })
const laser = createMockOption({ option_id: 'Laser_Power', name: 'Laser Power', category: 'Basic Controls' })
const ppSpatial = createMockOption({
  option_id: 'PP_Spatial_Filter_Magnitude',
  name: 'Filter Magnitude',
  category: 'Post-Processing',
  filter_name: 'Spatial Filter',
})

// Real-world trap: this control's description contains the word "laser" but it
// is NOT a laser control. Searching "laser" must not surface it.
const syncMode = createMockOption({
  option_id: 'inter_cam_sync_mode',
  name: 'Inter Cam Sync Mode',
  category: 'Basic Controls',
  description:
    'Inter-camera synchronization mode: ... 259 and 260 for two frames per trigger with laser ON-OFF and OFF-ON.',
})

const all = [exposure, gain, laser, ppSpatial]

describe('filterOptions', () => {
  it('returns all options unchanged for an empty query', () => {
    expect(filterOptions(all, '')).toEqual(all)
    expect(filterOptions(all, '   ')).toEqual(all)
  })

  it('matches by exact substring of the name', () => {
    const r = filterOptions(all, 'gain')
    expect(r).toContain(gain)
    expect(r).not.toContain(exposure)
  })

  it('matches by alias (ir projector -> Laser Power)', () => {
    const r = filterOptions(all, 'ir projector')
    expect(r).toContain(laser)
  })

  it('does not match a control merely because its description mentions the term', () => {
    const r = filterOptions([laser, syncMode], 'laser')
    expect(r).toContain(laser)
    expect(r).not.toContain(syncMode)
  })

  it('does not loosely match unrelated controls (laser must not surface Filter Magnitude)', () => {
    const filterMag = createMockOption({
      option_id: 'PP_Decimation_Filter_filter_magnitude',
      name: 'Filter Magnitude',
      category: 'Post-Processing',
      filter_name: 'Decimation Filter',
    })
    const r = filterOptions([laser, filterMag], 'laser')
    expect(r).toContain(laser)
    expect(r).not.toContain(filterMag)
  })

  it('tolerates a typo (expsure -> Exposure)', () => {
    const r = filterOptions(all, 'expsure')
    expect(r).toContain(exposure)
  })

  it('surfaces a whole section via category name (post -> Post-Processing)', () => {
    const r = filterOptions(all, 'post')
    expect(r).toContain(ppSpatial)
  })

  it('preserves original array order among matches', () => {
    const r = filterOptions([exposure, gain, laser], 'a') // matches gain (gain) + laser (laser) + exposure? loosely
    // whatever matches, order must follow source order
    const idx = r.map(o => all.indexOf(o))
    expect(idx).toEqual([...idx].sort((a, b) => a - b))
  })

  it('returns empty for a nonsense query', () => {
    expect(filterOptions(all, 'zzzqqq')).toHaveLength(0)
  })
})
