import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';

const getStatusConfig = (status) => {
    const s = status?.toUpperCase() || '';
    
    const configs = {
        // Success states
        'COMPLETED': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'PAID': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'ACTIVE': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        
        // In progress states
        'SCHEDULED': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'PENDING': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'IN_PROGRESS': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        
        // Alert/Warning states
        'CANCELLED': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'UNPAID': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'OVERDUE': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        
        // Neutral states
        'INACTIVE': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'DRAFT': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'CONSULTING': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        
        // Error/inactive states
        'CANCELLED': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'UNPAID': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        },
        'INACTIVE': { 
            bg: 'bg-gray-100', 
            text: 'text-gray-800', 
            border: 'border-gray-200',
            dot: 'bg-gray-500',
            icon: null
        }
    };
    
    return configs[s] || {
        bg: 'bg-gray-100',
        text: 'text-gray-800',
        border: 'border-gray-200',
        dot: 'bg-gray-500',
        icon: null
    };
};

const StatusBadge = ({ status, options = [], onUpdate, type = 'default', showIcon = true }) => {
    const [isOpen, setIsOpen] = useState(false);
    const [position, setPosition] = useState({ top: 0, left: 0 });
    const badgeRef = useRef(null);
    const isInteractive = options.length > 0 && onUpdate;
    const config = getStatusConfig(status);

    useEffect(() => {
        const handleClickOutside = (event) => {
            if (badgeRef.current && !badgeRef.current.contains(event.target) && !event.target.closest('.status-badge-dropdown')) {
                setIsOpen(false);
            }
        };

        const handleScroll = () => {
            if (isOpen) setIsOpen(false);
        };

        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
            window.addEventListener('scroll', handleScroll, true);
            window.addEventListener('resize', handleScroll);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            window.removeEventListener('scroll', handleScroll, true);
            window.removeEventListener('resize', handleScroll);
        };
    }, [isOpen]);

    const toggleMenu = () => {
        if (!isInteractive) return;

        if (!isOpen && badgeRef.current) {
            const rect = badgeRef.current.getBoundingClientRect();
            setPosition({
                top: rect.bottom + 8,
                left: rect.left
            });
        }
        setIsOpen(!isOpen);
    };

    const badgeClasses = `status-badge ${config.bg} ${config.text} ${config.border} ${
        isInteractive ? 'cursor-pointer hover:shadow-soft hover:scale-105 active:scale-95' : ''
    } transition-all duration-200 ease-out`;

    const menu = (
        <div
            className="status-badge-dropdown fixed z-[9999] w-44 rounded-xl shadow-organic bg-white ring-1 ring-gray-200 focus:outline-none animate-slide-down overflow-hidden"
            style={{ top: position.top, left: position.left }}
        >
            <div className="py-2">
                {options.map((option) => {
                    const optionConfig = getStatusConfig(option);
                    return (
                        <button
                            key={option}
                            onClick={(e) => {
                                e.stopPropagation();
                                onUpdate(option);
                                setIsOpen(false);
                            }}
                            className="block w-full text-left px-4 py-3 text-sm text-gray-700 hover:bg-gray-50 transition-colors duration-200 flex items-center gap-3"
                        >
                            <div className={`w-2 h-2 rounded-full ${optionConfig.dot}`}></div>
                            <span className="flex-1">{option}</span>
                            {showIcon && config.icon && (
                                <span className="text-xs opacity-60">{optionConfig.icon}</span>
                            )}
                        </button>
                    );
                })}
            </div>
        </div>
    );

    if (!isInteractive) {
        return (
            <span className={badgeClasses}>
                {showIcon && config.icon && (
                    <span className="mr-1.5 text-xs opacity-80">{config.icon}</span>
                )}
                <span className="font-medium">{status}</span>
                <div className={`w-1.5 h-1.5 rounded-full ${config.dot} ml-2 animate-pulse`}></div>
            </span>
        );
    }

    return (
        <div className="relative inline-block text-left">
            <span
                ref={badgeRef}
                onClick={toggleMenu}
                className={`${badgeClasses} flex items-center gap-1.5 select-none group`}
            >
                {showIcon && config.icon && (
                    <span className="text-xs opacity-80 group-hover:scale-110 transition-transform duration-200">
                        {config.icon}
                    </span>
                )}
                <span className="font-medium">{status}</span>
                <svg 
                    xmlns="http://www.w3.org/2000/svg" 
                    className={`h-3 w-3 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`} 
                    viewBox="0 0 20 20" 
                    fill="currentColor"
                >
                    <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
                </svg>
            </span>

            {isOpen && createPortal(menu, document.body)}
        </div>
    );
};

export default StatusBadge;
