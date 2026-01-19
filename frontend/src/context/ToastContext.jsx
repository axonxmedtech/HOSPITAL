import React, { createContext, useContext, useState, useEffect } from 'react';

const ToastContext = createContext();

export const useToast = () => useContext(ToastContext);

export const ToastProvider = ({ children }) => {
    const [toasts, setToasts] = useState([]);

    const addToast = (message, type = 'info') => {
        const id = Date.now() + Math.random();
        setToasts(prev => [...prev, { id, message, type }]);

        // Auto-remove after 3 seconds
        setTimeout(() => {
            removeToast(id);
        }, 3000);
    };

    const removeToast = (id) => {
        setToasts(prev => prev.filter(toast => toast.id !== id));
    };

    const success = (message) => addToast(message, 'success');
    const error = (message) => addToast(message, 'error');
    const info = (message) => addToast(message, 'info');

    return (
        <ToastContext.Provider value={{ success, error, info }}>
            {children}
            <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
                {toasts.map(toast => (
                    <div
                        key={toast.id}
                        className={`
                            px-4 py-3 rounded-lg shadow-lg text-white font-medium transform transition-all duration-300 ease-in-out
                            ${toast.type === 'success' ? 'bg-green-500' :
                                toast.type === 'error' ? 'bg-red-500' : 'bg-blue-500'}
                            flex items-center gap-2 min-w-[300px]
                        `}
                    >
                        <span>
                            {toast.type === 'success' ? '✅' :
                                toast.type === 'error' ? '⚠️' : 'ℹ️'}
                        </span>
                        {toast.message}
                        <button
                            onClick={() => removeToast(toast.id)}
                            className="ml-auto text-white hover:text-gray-200"
                        >
                            ✕
                        </button>
                    </div>
                ))}
            </div>
        </ToastContext.Provider>
    );
};
