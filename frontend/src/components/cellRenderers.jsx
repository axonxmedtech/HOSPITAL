import React from 'react';

/**
 * Cell Renderers for AG Grid
 * 
 * Reusable cell renderer components for common data types
 * 
 * @author HMS Team
 * @version Phase-1
 */

/**
 * Status Badge Renderer
 * Displays colored badges for status values
 */
export const StatusBadgeRenderer = (params) => {
    const { value } = params;
    if (!value) return null;

    const statusColors = {
        'SCHEDULED': 'bg-yellow-100 text-yellow-800',
        'COMPLETED': 'bg-green-100 text-green-800',
        'CANCELLED': 'bg-red-100 text-red-800',
        'ACTIVE': 'bg-green-100 text-green-800',
        'INACTIVE': 'bg-gray-100 text-gray-800',
        'PAID': 'bg-green-100 text-green-800',
        'PENDING': 'bg-yellow-100 text-yellow-800',
        'FREE': 'bg-blue-100 text-blue-800',
        'PREMIUM': 'bg-purple-100 text-purple-800',
    };

    const colorClass = statusColors[value.toUpperCase()] || 'bg-gray-100 text-gray-800';

    return (
        <span className={`px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${colorClass}`}>
            {value}
        </span>
    );
};

/**
 * Date Formatter Renderer
 * Formats dates consistently
 */
export const DateRenderer = (params) => {
    const { value } = params;
    if (!value) return null;

    try {
        const date = new Date(value);
        return date.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    } catch (error) {
        return value;
    }
};

/**
 * DateTime Formatter Renderer
 * Formats date and time
 */
export const DateTimeRenderer = (params) => {
    const { value } = params;
    if (!value) return null;

    try {
        const date = new Date(value);
        return date.toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (error) {
        return value;
    }
};

/**
 * Action Buttons Renderer
 * Renders action buttons in a cell
 */
export const ActionButtonsRenderer = (params) => {
    const { actions } = params;
    if (!actions || actions.length === 0) return null;

    return (
        <div className="flex gap-2 justify-end">
            {actions.map((action, index) => (
                <button
                    key={index}
                    onClick={(e) => {
                        e.stopPropagation();
                        action.onClick(params.data);
                    }}
                    className={`${action.className || 'text-blue-500 hover:text-blue-700'} bg-opacity-10 hover:bg-opacity-20 p-2 rounded-full transition`}
                    title={action.tooltip}
                    disabled={action.disabled}
                >
                    {action.icon}
                </button>
            ))}
        </div>
    );
};

/**
 * Boolean Renderer
 * Displays checkmark or cross for boolean values
 */
export const BooleanRenderer = (params) => {
    const { value } = params;

    return (
        <span className={`text-xl ${value ? 'text-green-500' : 'text-red-500'}`}>
            {value ? '✓' : '✗'}
        </span>
    );
};

/**
 * Currency Renderer
 * Formats numbers as currency
 */
export const CurrencyRenderer = (params) => {
    const { value } = params;
    if (value === null || value === undefined) return null;

    return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
    }).format(value);
};

/**
 * ID Renderer with Copy
 * Displays ID with copy to clipboard functionality
 */
export const IDRenderer = (params) => {
    const { value } = params;
    if (!value) return null;

    const handleCopy = (e) => {
        e.stopPropagation();
        navigator.clipboard.writeText(value);
        // Could add toast notification here
    };

    return (
        <div className="flex items-center gap-2">
            <span className="font-mono text-sm">{value}</span>
            <button
                onClick={handleCopy}
                className="text-gray-400 hover:text-gray-600 transition"
                title="Copy ID"
            >
                📋
            </button>
        </div>
    );
};

/**
 * Truncated Text Renderer
 * Shows truncated text with tooltip
 */
export const TruncatedTextRenderer = (params) => {
    const { value, maxLength = 50 } = params;
    if (!value) return null;

    const truncated = value.length > maxLength ? `${value.substring(0, maxLength)}...` : value;

    return (
        <span title={value} className="truncate">
            {truncated}
        </span>
    );
};
