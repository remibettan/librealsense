// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
//
// Shared "stop generating" button — identical markup was duplicated between the AI Assistant
// and Chatbot modes' input toolbars.

import { Square } from 'lucide-react'

interface StopGeneratingButtonProps {
  onClick: () => void
  disabled: boolean
}

export function StopGeneratingButton({ onClick, disabled }: StopGeneratingButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title="Stop generating"
      className="p-1.5 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed text-gray-400 hover:text-white hover:bg-gray-700"
    >
      <Square className="w-4 h-4" />
    </button>
  )
}
