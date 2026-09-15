// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
//
// Body content (message list + input) for the RealSense AI Assistant mode, rendered inside the
// shared AssistantPanel shell — only the header/frame around it is shared with the Chatbot mode.
// Mirrors ChatBotContent.tsx's self-contained shape (reads its own store slice, owns its own
// input state) for symmetry between the two modes.

import { useState, useRef, useEffect } from 'react'
import { Send, Loader2, Sparkles, Paperclip, FileText, X } from 'lucide-react'
import { useAppStore } from '../../store'
import { AssistantMessageBubble } from './AssistantMessage'
import { usePendingAttachments } from './usePendingAttachments'
import { StopGeneratingButton } from './StopGeneratingButton'

const ICON_BTN = 'text-gray-400 hover:text-white hover:bg-gray-700'

export function AssistantContent() {
  const { isAssistantLoading, assistantMessages, sendAssistantMessage, stopAssistantMessage, setError } = useAppStore()

  const [inputValue, setInputValue] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const { pendingImages, pendingFiles, handleFileChange, removeImage, removeFile, clear: clearAttachments } =
    usePendingAttachments(setError)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [assistantMessages])

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const message = inputValue.trim()
    if ((!message && pendingImages.length === 0 && pendingFiles.length === 0) || isAssistantLoading) return
    setInputValue('')
    const attachments = (pendingImages.length || pendingFiles.length)
      ? { imageDataUris: pendingImages, fileDataUris: pendingFiles }
      : undefined
    clearAttachments()
    sendAssistantMessage(message, attachments)
  }

  return (
    <>
      <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-4">
        {assistantMessages.length === 0 ? (
          <div className="text-center mt-8 text-gray-400">
            <Sparkles className="w-12 h-12 mx-auto mb-3 text-gray-600" />
            <p className="text-sm">Ask me anything about RealSense products.</p>
            <p className="text-xs mt-2 text-gray-600">Try: "What's the depth range of the D435i?"</p>
          </div>
        ) : (
          assistantMessages.map((message, i) => (
            <AssistantMessageBubble
              key={message.id}
              message={message}
              isLatestAssistant={message.role === 'assistant' && i === assistantMessages.length - 1}
            />
          ))
        )}

        {isAssistantLoading && !assistantMessages.some((m) => m.isStreaming && m.content) && (
          <div className="flex items-center gap-2 text-gray-400">
            <Loader2 className="w-4 h-4 animate-spin" />
            <span className="text-sm">Thinking...</span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <form onSubmit={handleSubmit} className="p-3 border-t bg-rs-darker border-gray-700">
        {(pendingImages.length > 0 || pendingFiles.length > 0) && (
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
                  className={ICON_BTN}
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
            placeholder="Ask about RealSense products..."
            disabled={isAssistantLoading}
            className="flex-1 px-3 py-2 border rounded-lg focus:outline-none focus:border-rs-blue text-sm bg-gray-800 border-gray-600 text-white placeholder-gray-500"
          />
          <button
            type="submit"
            disabled={(!inputValue.trim() && pendingImages.length === 0 && pendingFiles.length === 0) || isAssistantLoading}
            className="p-2 bg-rs-blue text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>

        <div className="flex items-center gap-1 mt-2">
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
            className={`p-1.5 rounded transition-colors ${ICON_BTN}`}
          >
            <Paperclip className="w-4 h-4" />
          </button>
          <StopGeneratingButton onClick={stopAssistantMessage} disabled={!isAssistantLoading} />
        </div>
      </form>
    </>
  )
}
