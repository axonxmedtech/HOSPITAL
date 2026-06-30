import React, { useState, useEffect, useRef } from 'react';
import hospitalService from '../services/hospitalService';

export default function CommandPalette({ onClose }) {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const inputRef = useRef(null);
    const overlayRef = useRef(null);

    useEffect(() => {
        inputRef.current?.focus();
    }, []);

    useEffect(() => {
        if (!query.trim() || query.length < 2) {
            setResults([]);
            return;
        }
        const timer = setTimeout(async () => {
            setLoading(true);
            try {
                const data = await hospitalService.getPatients(query, 0, 8);
                setResults(data.content || data || []);
            } catch {
                setResults([]);
            } finally {
                setLoading(false);
            }
        }, 300);
        return () => clearTimeout(timer);
    }, [query]);

    useEffect(() => {
        const handleKey = (e) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', handleKey);
        return () => window.removeEventListener('keydown', handleKey);
    }, [onClose]);

    return (
        <div
            ref={overlayRef}
            className="fixed inset-0 z-50 flex items-start justify-center pt-[15vh] bg-black/40 backdrop-blur-sm"
            onClick={(e) => { if (e.target === overlayRef.current) onClose(); }}
        >
            <div className="w-full max-w-xl bg-white rounded-2xl shadow-2xl border border-gray-200 overflow-hidden mx-4">
                <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-100">
                    <svg className="w-5 h-5 text-gray-400 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                    <input
                        ref={inputRef}
                        type="text"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        placeholder="Search patients by name, phone, or UHID…"
                        className="flex-1 text-sm text-gray-800 placeholder-gray-400 outline-none bg-transparent"
                    />
                    {loading && <span className="text-xs text-gray-400">Searching…</span>}
                    <kbd className="text-xs text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded border border-gray-200">Esc</kbd>
                </div>

                {results.length > 0 && (
                    <ul className="max-h-80 overflow-y-auto divide-y divide-gray-50">
                        {results.map(p => (
                            <li key={p.id}>
                                <button
                                    type="button"
                                    className="w-full text-left px-4 py-3 hover:bg-gray-50 transition-colors"
                                    onClick={onClose}
                                >
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-semibold text-gray-800">{p.name}</p>
                                            <p className="text-xs text-gray-500 mt-0.5">
                                                {p.phone || p.mobile || '—'} &middot; {p.uhid || `ID ${p.id}`}
                                            </p>
                                        </div>
                                        <span className="text-xs text-gray-400 bg-gray-100 px-2 py-0.5 rounded-full">Patient</span>
                                    </div>
                                </button>
                            </li>
                        ))}
                    </ul>
                )}

                {query.length >= 2 && !loading && results.length === 0 && (
                    <div className="px-4 py-6 text-center text-sm text-gray-400">
                        No patients found for &ldquo;{query}&rdquo;
                    </div>
                )}

                {!query && (
                    <div className="px-4 py-4 text-xs text-gray-400 text-center">
                        Type to search patients &middot; Press <kbd className="bg-gray-100 px-1 py-0.5 rounded border border-gray-200">Esc</kbd> to close
                    </div>
                )}
            </div>
        </div>
    );
}
