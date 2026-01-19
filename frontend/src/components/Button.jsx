import React from 'react';

const Button = ({ 
    children, 
    variant = 'primary', 
    size = 'md', 
    disabled = false, 
    loading = false,
    icon,
    iconPosition = 'left',
    className = '',
    ...props 
}) => {
    const baseClasses = 'inline-flex items-center justify-center font-medium rounded-xl transition-all duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed';
    
    const variants = {
        primary: 'text-white bg-primary-600 border border-transparent shadow-soft hover:bg-primary-700 hover:shadow-soft-lg hover:-translate-y-0.5 focus:ring-primary-500 active:scale-95',
        secondary: 'text-neutral-700 bg-white border border-neutral-300 shadow-soft hover:bg-neutral-50 hover:shadow-soft-lg hover:-translate-y-0.5 focus:ring-primary-500 active:scale-95',
        success: 'text-white bg-success-600 border border-transparent shadow-soft hover:bg-success-700 hover:shadow-soft-lg hover:-translate-y-0.5 focus:ring-success-500 active:scale-95',
        warning: 'text-white bg-warning-600 border border-transparent shadow-soft hover:bg-warning-700 hover:shadow-soft-lg hover:-translate-y-0.5 focus:ring-warning-500 active:scale-95',
        error: 'text-white bg-error-600 border border-transparent shadow-soft hover:bg-error-700 hover:shadow-soft-lg hover:-translate-y-0.5 focus:ring-error-500 active:scale-95',
        ghost: 'text-neutral-600 bg-transparent border border-transparent hover:bg-neutral-100 hover:text-neutral-900 focus:ring-primary-500 active:scale-95',
        outline: 'text-primary-600 bg-transparent border border-primary-300 hover:bg-primary-50 hover:border-primary-400 focus:ring-primary-500 active:scale-95'
    };
    
    const sizes = {
        sm: 'px-3 py-2 text-sm',
        md: 'px-4 py-2.5 text-sm',
        lg: 'px-6 py-3 text-base',
        xl: 'px-8 py-4 text-lg'
    };
    
    const iconSizes = {
        sm: 'w-4 h-4',
        md: 'w-4 h-4',
        lg: 'w-5 h-5',
        xl: 'w-6 h-6'
    };
    
    const classes = `${baseClasses} ${variants[variant]} ${sizes[size]} ${className}`;
    
    return (
        <button
            className={classes}
            disabled={disabled || loading}
            {...props}
        >
            {loading && (
                <svg className={`animate-spin -ml-1 mr-2 ${iconSizes[size]}`} fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
            )}
            
            {icon && iconPosition === 'left' && !loading && (
                <span className={`mr-2 ${iconSizes[size]} flex items-center justify-center`}>
                    {icon}
                </span>
            )}
            
            {children}
            
            {icon && iconPosition === 'right' && !loading && (
                <span className={`ml-2 ${iconSizes[size]} flex items-center justify-center`}>
                    {icon}
                </span>
            )}
        </button>
    );
};

export default Button;