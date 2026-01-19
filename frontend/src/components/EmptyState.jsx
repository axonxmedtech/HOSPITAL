import React from 'react';

const EmptyState = ({ icon, title, message, actionLabel, onAction, variant = 'default' }) => {
    const variants = {
        default: {
            iconBg: 'bg-neutral-100',
            iconText: 'text-neutral-400',
            titleText: 'text-neutral-900',
            messageText: 'text-neutral-500'
        },
        primary: {
            iconBg: 'bg-primary-100',
            iconText: 'text-primary-400',
            titleText: 'text-neutral-900',
            messageText: 'text-neutral-500'
        },
        success: {
            iconBg: 'bg-success-100',
            iconText: 'text-success-400',
            titleText: 'text-neutral-900',
            messageText: 'text-neutral-500'
        }
    };

    const theme = variants[variant] || variants.default;

    return (
        <div className="flex flex-col items-center justify-center py-16 px-6 text-center bg-white rounded-2xl shadow-soft border border-neutral-100 animate-fade-in-up">
            {/* Icon container with organic styling */}
            <div className={`w-24 h-24 ${theme.iconBg} rounded-3xl flex items-center justify-center mb-6 shadow-inner-soft`}>
                <span className={`text-4xl ${theme.iconText}`}>{icon}</span>
            </div>
            
            {/* Content */}
            <div className="max-w-sm space-y-3">
                <h3 className={`text-xl font-semibold ${theme.titleText} tracking-tight`}>
                    {title}
                </h3>
                <p className={`${theme.messageText} leading-relaxed`}>
                    {message}
                </p>
            </div>
            
            {/* Action button */}
            {onAction && (
                <div className="mt-8">
                    <button
                        onClick={onAction}
                        className="btn-primary hover-lift group"
                    >
                        <span className="mr-2 group-hover:scale-110 transition-transform duration-200">+</span>
                        {actionLabel}
                    </button>
                </div>
            )}
            
            {/* Decorative elements */}
            <div className="absolute top-4 right-4 w-2 h-2 bg-primary-200 rounded-full opacity-60"></div>
            <div className="absolute bottom-6 left-6 w-1.5 h-1.5 bg-secondary-200 rounded-full opacity-40"></div>
        </div>
    );
};

export default EmptyState;
