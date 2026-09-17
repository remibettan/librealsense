// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
//
// Shared input footer for both the AI Assistant and Chatbot modes — text input, optional
// file/image attachments (AI Assistant only), and a send button that's replaced by a stop
// button while a request is in flight (never a greyed-out stop button sitting idle).
// Differences between modes (accent color, placeholder, submit handler, whether attachments
// are enabled) are passed in as props.

import { useEffect, useRef, useState } from 'react'
import { Send, Square, Paperclip, X, FileText } from 'lucide-react'
import { usePendingAttachments } from './usePendingAttachments'
import type { AssistantFileAttachment } from '../../api/assistantChat'

export interface ChatInputAttachments {
  imageDataUris?: string[]
  fileDataUris?: AssistantFileAttachment[]
}

interface ChatInputProps {
  placeholder: string
  accent: 'blue' | 'amber'
  isLoading: boolean
  attachmentsEnabled: boolean
  onSend: (message: string, attachments?: ChatInputAttachments) => void
  onStop: () => void
  onAttachmentError?: (message: string) => void
}

const ACCENT_CLASSES = {
  blue: { button: 'bg-rs-blue hover:bg-blue-600', focus: 'focus:border-rs-blue' },
  amber: { button: 'bg-amber-600 hover:bg-amber-500', focus: 'focus:border-amber-500' },
} as const

export function ChatInput({
  placeholder,
  accent,
  isLoading,
  attachmentsEnabled,
  onSend,
  onStop,
  onAttachmentError = () => {},
}: ChatInputProps) {
  const [inputValue, setInputValue] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const { pendingImages, pendingFiles, handleFileChange, removeImage, removeFile, clear: clearAttachments } =
    usePendingAttachments(onAttachmentError)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  const hasContent = Boolean(inputValue.trim() || pendingImages.length || pendingFiles.length)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!hasContent || isLoading) return
    const message = inputValue.trim()
    setInputValue('')
    const attachments = (pendingImages.length || pendingFiles.length)
      ? { imageDataUris: pendingImages, fileDataUris: pendingFiles }
      : undefined
    clearAttachments()
    onSend(message, attachments)
  }

  const colors = ACCENT_CLASSES[accent]

  return (
    <form onSubmit={handleSubmit} className="p-3 border-t bg-rs-darker border-gray-700">
      {attachmentsEnabled && (pendingImages.length > 0 || pendingFiles.length > 0) && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          {pendingImages.map((dataUri, i) => (
            <div key={i} className="relative w-12 h-12 rounded overflow-hidden border border-gray-600 shrink-0">
              <img src={dataUri} alt="" className="w-full h-full object-cover" />
              <button
                type="button"
                onClick={() => removeImage(i)}
                className="absolute top-0 right-0 bg-black/60 text-white rounded-bl p-0.5"
                title="Remove image"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          ))}
          {pendingFiles.map((file, i) => (
            <div
              key={i}
              className="flex items-center gap-1 pl-2 pr-1 py-1 rounded border text-xs bg-gray-800 border-gray-600 text-gray-300"
            >
              <FileText className="w-3.5 h-3.5 shrink-0" />
              <span className="max-w-[120px] truncate">{file.fileName}</span>
              <button
                type="button"
                onClick={() => removeFile(i)}
                className="text-gray-400 hover:text-white"
                title="Remove file"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="flex items-center gap-2">
        <input
          ref={inputRef}
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder={placeholder}
          disabled={isLoading}
          className={`flex-1 px-3 py-2 border rounded-lg focus:outline-none text-sm bg-gray-800 border-gray-600 text-white placeholder-gray-500 ${colors.focus}`}
        />
        {attachmentsEnabled && (
          <>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              onChange={handleFileChange}
              className="hidden"
              accept="image/*,.pdf,.doc,.docx,.txt"
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              title="Attach a file"
              className="p-2 rounded-lg transition-colors text-gray-400 hover:text-white hover:bg-gray-700"
            >
              <Paperclip className="w-4 h-4" />
            </button>
          </>
        )}
        {isLoading ? (
          <button
            type="button"
            onClick={onStop}
            title="Stop generating"
            className="p-2 bg-gray-700 text-white rounded-lg hover:bg-gray-600 transition-colors"
          >
            <Square className="w-4 h-4" />
          </button>
        ) : (
          <button
            type="submit"
            disabled={!hasContent}
            className={`p-2 text-white rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors ${colors.button}`}
          >
            <Send className="w-4 h-4" />
          </button>
        )}
      </div>
    </form>
  )
}
