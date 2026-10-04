import React, { useState, useEffect } from 'react';
import { Lock, Folder, Trash2, Plus, ArrowLeft, Eye, EyeOff, ShieldCheck, FileText, X } from 'lucide-react';

interface SecureFile {
  id: string;
  name: string;
  content: string; // Plaintext for demo representation (would be AES-256 encrypted)
  size: number;
  createdAt: string;
}

export default function VaultDashboard() {
  const [isUnlocked, setIsUnlocked] = useState(false);
  const [enteredPin, setEnteredPin] = useState('');
  const [pinError, setPinError] = useState<string | null>(null);
  const [correctPin] = useState('1234'); // Default master PIN

  // Stateful mocked file explorer (simulates persistent state)
  const [secureFiles, setSecureFiles] = useState<SecureFile[]>([
    {
      id: '1',
      name: 'personal_credentials',
      content: 'API_KEY: ds_9281a89d182bf90a81c\nDB_PASS: super_secret_db_2026',
      size: 58,
      createdAt: 'Jun 24, 2026 14:15',
    },
    {
      id: '2',
      name: 'recovery_seeds',
      content: 'apple banana cherry dog elephant fox grape horse ink jackal king lion',
      size: 69,
      createdAt: 'Jun 25, 2026 09:12',
    }
  ]);

  const [selectedFile, setSelectedFile] = useState<SecureFile | null>(null);
  const [isDecrypting, setIsDecrypting] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newFileName, setNewFileName] = useState('');
  const [newFileContent, setNewFileContent] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);

  // Keyboard/Keypad action handler
  const handleKeypress = (key: string) => {
    setPinError(null);
    if (key === 'C') {
      setEnteredPin('');
    } else if (key === '⌫') {
      setEnteredPin(prev => prev.slice(0, -1));
    } else {
      if (enteredPin.length < 4) {
        const updated = enteredPin + key;
        setEnteredPin(updated);
        if (updated.length === 4) {
          if (updated === correctPin) {
            setIsUnlocked(true);
            setEnteredPin('');
          } else {
            setPinError('Incorrect PIN. Access Denied.');
            setEnteredPin('');
          }
        }
      }
    }
  };

  // Create file handler
  const handleCreateFile = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFileName.trim() || !newFileContent.trim()) {
      setCreateError('All fields are required.');
      return;
    }

    const newFile: SecureFile = {
      id: Date.now().toString(),
      name: newFileName.trim().replace(/\s+/g, '_').toLowerCase(),
      content: newFileContent,
      size: new Blob([newFileContent]).size,
      createdAt: new Date().toLocaleString('en-US', {
        month: 'short',
        day: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      })
    };

    setSecureFiles(prev => [newFile, ...prev]);
    setShowCreateModal(false);
    setNewFileName('');
    setNewFileContent('');
    setCreateError(null);
  };

  // Delete file handler
  const handleDeleteFile = (id: string) => {
    setSecureFiles(prev => prev.filter(f => f.id !== id));
    if (selectedFile?.id === id) {
      setSelectedFile(null);
    }
  };

  // Simulate decrypting loader
  const handleOpenFile = (file: SecureFile) => {
    setIsDecrypting(true);
    setSelectedFile(file);
    setTimeout(() => {
      setIsDecrypting(false);
    }, 600);
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between font-sans">
      {/* 1. SECURE PASSWORD ENTRY SCREEN */}
      {!isUnlocked ? (
        <div className="flex-1 flex flex-col justify-center items-center px-6 py-12 max-w-md mx-auto w-full">
          <div className="text-center mb-8">
            <div className="inline-flex p-4 bg-emerald-500/10 rounded-full text-emerald-400 mb-4 animate-pulse">
              <Lock className="w-10 h-10" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-white mb-2">DASMO VAULT</h1>
            <p className="text-slate-400 text-sm">
              Enter your 4-digit Master PIN to decrypt your sensitive storage.
            </p>
          </div>

          {/* Code Indicator Dots */}
          <div className="flex justify-center space-x-4 mb-6">
            {[1, 2, 3, 4].map((i) => (
              <div
                key={i}
                className={`w-4 h-4 rounded-full border-2 transition-all duration-200 ${
                  enteredPin.length >= i
                    ? 'bg-emerald-400 border-emerald-400 scale-110 shadow-[0_0_10px_rgba(52,211,153,0.5)]'
                    : 'bg-transparent border-slate-700'
                }`}
              />
            ))}
          </div>

          {/* Pin Error Indicator */}
          <div className="h-6 mb-4">
            {pinError && (
              <span className="text-rose-400 text-sm font-medium bg-rose-500/10 px-3 py-1 rounded-full">
                {pinError}
              </span>
            )}
          </div>

          {/* Numerical Pin Pad Grid */}
          <div className="grid grid-cols-3 gap-4 w-full px-4 mb-8">
            {['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', '⌫'].map((key) => (
              <button
                key={key}
                onClick={() => handleKeypress(key)}
                className={`h-16 rounded-2xl font-semibold text-lg flex items-center justify-center transition-all duration-150 active:scale-95 ${
                  key === 'C' || key === '⌫'
                    ? 'bg-slate-900 text-slate-400 hover:bg-slate-800'
                    : 'bg-slate-900/50 hover:bg-slate-900 border border-slate-800/80 hover:border-slate-700 text-white'
                }`}
              >
                {key}
              </button>
            ))}
          </div>
        </div>
      ) : (
        /* 2. ENCRYPTED STORAGE FILE EXPLORER VIEW */
        <div className="flex-1 flex flex-col max-w-4xl mx-auto w-full px-6 py-8">
          {/* Dashboard Header */}
          <div className="flex justify-between items-center border-b border-slate-800 pb-6 mb-8">
            <div>
              <div className="flex items-center space-x-2 text-xs font-semibold tracking-wider text-emerald-400 uppercase mb-1">
                <ShieldCheck className="w-4 h-4" />
                <span>AES-256 Decrypted Session</span>
              </div>
              <h1 className="text-3xl font-bold tracking-tight text-white">Secure Vault</h1>
            </div>

            <div className="flex items-center space-x-3">
              <button
                onClick={() => setIsUnlocked(false)}
                className="inline-flex items-center space-x-2 bg-slate-900 border border-slate-800 text-slate-300 hover:text-white px-4 py-2.5 rounded-xl text-sm font-medium hover:bg-slate-800 transition"
              >
                <Lock className="w-4 h-4 text-emerald-400" />
                <span>Lock Session</span>
              </button>
            </div>
          </div>

          {/* Folder Subheading & Actions */}
          <div className="flex justify-between items-center mb-6">
            <h2 className="text-lg font-semibold text-slate-300 flex items-center space-x-2">
              <Folder className="w-5 h-5 text-indigo-400" />
              <span>Private Documents / Notes</span>
            </h2>

            <button
              onClick={() => setShowCreateModal(true)}
              className="inline-flex items-center space-x-2 bg-emerald-500 hover:bg-emerald-600 text-slate-950 px-4 py-2.5 rounded-xl text-sm font-semibold shadow-lg shadow-emerald-500/20 transition active:scale-95"
            >
              <Plus className="w-4 h-4 stroke-[3px]" />
              <span>Encrypt New Note</span>
            </button>
          </div>

          {/* Secure Grid File Explorer */}
          {secureFiles.length === 0 ? (
            <div className="flex-1 border-2 border-dashed border-slate-800 rounded-3xl flex flex-col items-center justify-center p-12 text-center my-6">
              <Folder className="w-16 h-16 text-slate-700 mb-4" />
              <h3 className="text-xl font-bold text-slate-300 mb-2">Vault is Empty</h3>
              <p className="text-slate-500 text-sm max-w-sm">
                Add highly sensitive credentials, recovery seeds, or secure journals. Files are encrypted locally and safely isolated.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {secureFiles.map((file) => (
                <div
                  key={file.id}
                  onClick={() => handleOpenFile(file)}
                  className="group bg-slate-900/40 hover:bg-slate-900 border border-slate-900 hover:border-slate-800 rounded-2xl p-5 cursor-pointer transition-all duration-200 hover:-translate-y-0.5 flex flex-col justify-between h-48 relative overflow-hidden"
                >
                  <div className="absolute top-0 right-0 w-24 h-24 bg-emerald-500/5 rounded-full blur-xl group-hover:bg-emerald-500/10 transition" />
                  
                  <div>
                    <div className="flex justify-between items-start mb-4">
                      <div className="p-3 bg-indigo-500/10 rounded-xl text-indigo-400">
                        <Lock className="w-5 h-5" />
                      </div>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDeleteFile(file.id);
                        }}
                        className="p-1.5 text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition"
                        title="Secure Shred"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>

                    <h3 className="font-bold text-white text-base truncate mb-1">
                      {file.name}
                    </h3>
                    <span className="text-slate-500 text-xs">
                      {file.size} Bytes
                    </span>
                  </div>

                  <div className="text-xs text-slate-400 font-medium border-t border-slate-800/80 pt-3 flex justify-between items-center">
                    <span>{file.createdAt}</span>
                    <span className="text-emerald-400 font-semibold group-hover:underline flex items-center space-x-1">
                      <span>Decrypt</span>
                      <Eye className="w-3.5 h-3.5" />
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 3. MODAL: CREATE ENCRYPTED NOTE */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-lg p-6 relative shadow-2xl">
            <button
              onClick={() => setShowCreateModal(false)}
              className="absolute top-4 right-4 p-1.5 text-slate-400 hover:text-white hover:bg-slate-800 rounded-lg transition"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-xl font-bold text-white mb-2">New Secure Note</h3>
            <p className="text-slate-400 text-sm mb-6">
              Write sensitive info. Upon clicking save, it is automatically encrypted with AES-256 and stored.
            </p>

            <form onSubmit={handleCreateFile} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
                  File Name
                </label>
                <input
                  type="text"
                  placeholder="e.g. personal_passwords"
                  value={newFileName}
                  onChange={(e) => setNewFileName(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 focus:border-slate-700 text-white rounded-xl px-4 py-3 text-sm focus:outline-none transition"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
                  Sensitive Content
                </label>
                <textarea
                  rows={4}
                  placeholder="Type passwords, accounts, seed phrases, private conversations..."
                  value={newFileContent}
                  onChange={(e) => setNewFileContent(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 focus:border-slate-700 text-white rounded-xl px-4 py-3 text-sm focus:outline-none transition resize-none"
                />
              </div>

              {createError && (
                <p className="text-rose-400 text-xs font-medium bg-rose-500/10 px-3 py-1.5 rounded-lg">
                  {createError}
                </p>
              )}

              <div className="flex justify-end space-x-3 pt-4">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-400 hover:text-white hover:bg-slate-800 transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2.5 rounded-xl text-sm font-semibold bg-emerald-500 hover:bg-emerald-600 text-slate-950 transition active:scale-95 shadow-lg shadow-emerald-500/10"
                >
                  Encrypt & Save
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 4. MODAL: DECRYPTED FILE READER VIEW */}
      {selectedFile && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-lg p-6 relative shadow-2xl">
            <button
              onClick={() => setSelectedFile(null)}
              className="absolute top-4 right-4 p-1.5 text-slate-400 hover:text-white hover:bg-slate-800 rounded-lg transition"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex justify-between items-start mb-6">
              <div>
                <div className="flex items-center space-x-2 text-xs font-semibold text-emerald-400 uppercase mb-1">
                  <ShieldCheck className="w-3.5 h-3.5" />
                  <span>AES-256 Decrypted Preview</span>
                </div>
                <h3 className="text-xl font-bold text-white">
                  {selectedFile.name}
                </h3>
              </div>
              <button
                onClick={() => {
                  handleDeleteFile(selectedFile.id);
                }}
                className="inline-flex items-center space-x-1 text-rose-400 hover:text-rose-300 text-xs font-semibold bg-rose-500/10 hover:bg-rose-500/20 px-3 py-1.5 rounded-lg transition"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Secure Shred</span>
              </button>
            </div>

            <div className="bg-slate-950 border border-slate-800 rounded-2xl p-5 min-h-[140px] max-h-[300px] overflow-y-auto mb-6 font-mono text-sm leading-relaxed text-slate-300">
              {isDecrypting ? (
                <div className="flex flex-col items-center justify-center py-6">
                  <div className="w-6 h-6 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin mb-3" />
                  <span className="text-xs text-slate-500 font-semibold tracking-wider uppercase animate-pulse">
                    Decrypting Payload...
                  </span>
                </div>
              ) : (
                <pre className="whitespace-pre-wrap">{selectedFile.content}</pre>
              )}
            </div>

            <div className="flex justify-end">
              <button
                onClick={() => setSelectedFile(null)}
                className="px-5 py-2.5 rounded-xl text-sm font-semibold bg-slate-800 hover:bg-slate-700 text-white transition active:scale-95"
              >
                Close View
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Footer Branding */}
      <footer className="text-center py-6 text-xs text-slate-600 border-t border-slate-900/50">
        <span>Powered by DASMO AES-256 Cryptography engine. All operations local.</span>
      </footer>
    </div>
  );
}
