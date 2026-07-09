import { OptionInfo } from '../api/types'

/**
 * Curated search aliases for common camera controls, keyed by a normalized
 * option id (lowercase, non-alphanumeric stripped). Lets users find a control
 * by a synonym they might type instead of its exact SDK name.
 * Extend freely — unknown ids simply have no aliases.
 */
const ALIASES: Record<string, string[]> = {
  exposure: ['shutter', 'brightness', 'light', 'integration time'],
  gain: ['iso', 'sensitivity', 'amplification'],
  laserpower: ['emitter power', 'ir projector', 'laser strength', 'projector power'],
  emitterenabled: ['laser', 'ir projector', 'projector', 'emitter'],
  emitteronoff: ['laser', 'projector', 'emitter'],
  depthunits: ['scale', 'millimeters', 'depth scale', 'units'],
  visualpreset: ['preset', 'profile', 'mode'],
  enableautoexposure: ['auto exposure', 'ae', 'auto'],
  whitebalance: ['color temperature', 'wb', 'temperature'],
  enableautowhitebalance: ['auto white balance', 'awb', 'auto'],
  contrast: ['dynamic range'],
  sharpness: ['detail', 'edge'],
  frameshold: ['frames queue', 'buffer'],
}

/** Normalize an id/name for alias lookup: lowercase, strip non-alphanumerics. */
export function normalizeKey(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9]/g, '')
}

/** Return the synonym list for an option (by option_id, falling back to name), or []. */
export function getAliases(option: OptionInfo): string[] {
  return ALIASES[normalizeKey(option.option_id)] ?? ALIASES[normalizeKey(option.name)] ?? []
}
