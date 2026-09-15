// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
//
// Icons matching the reference widget (widget.js) exactly, rather than the closest lucide-react
// equivalents, so the panel chrome looks identical to the production embed.

const iconProps = { width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none', 'aria-hidden': true } as const

export function ExpandIcon() {
  return (
    <svg {...iconProps}>
      <path d="M4 10V4h6M20 14v6h-6M4 4l7 7M20 20l-7-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function CollapseIcon() {
  return (
    <svg {...iconProps}>
      <path d="M10 4v6H4M14 20v-6h6M10 10L4 4M14 14l6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function CloseIcon() {
  return (
    <svg width={16} height={16} viewBox="0 0 18 18" aria-hidden="true">
      <path d="M4 4L14 14M14 4L4 14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}
