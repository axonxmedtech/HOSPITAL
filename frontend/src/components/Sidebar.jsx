import React from 'react';

const Sidebar = ({ title, tabs, activeTab, onTabChange, footerTitle, footerData, variant = 'primary' }) => {
    // Color variants mapping with more organic colors
    const variants = {
        primary: {
            bgIcon: 'bg-primary-100',
            textIcon: 'text-primary-600',
            activeBg: 'bg-gradient-to-r from-primary-50 to-primary-100/50',
            activeText: 'text-primary-700',
            activeIcon: 'text-primary-600',
            activeBorder: 'border-l-4 border-primary-500'
        },
        purple: {
            bgIcon: 'bg-secondary-100',
            textIcon: 'text-secondary-600',
            activeBg: 'bg-gradient-to-r from-secondary-50 to-secondary-100/50',
            activeText: 'text-secondary-700',
            activeIcon: 'text-secondary-600',
            activeBorder: 'border-l-4 border-secondary-500'
        }
    };

    const theme = variants[variant] || variants.primary;

    return (
        <aside className="w-72 bg-white shadow-organic z-20 hidden md:flex flex-col h-full border-r border-neutral-100 font-sans">
            {/* Header Section */}
            <div className="p-8 border-b border-neutral-50">
                <div className="flex items-center gap-4 mb-8">
                    <div className={`${theme.bgIcon} p-3 rounded-2xl ${theme.textIcon} shadow-soft hover:shadow-soft-lg transition-all duration-300 hover:scale-105`}>
                        <span className="text-2xl">
                            {variant === 'purple' ? '🏢' : '🏥'}
                        </span>
                    </div>
                    <div>
                        <h1 className="text-xl font-bold text-neutral-800 tracking-tight leading-tight">{title}</h1>
                        <p className="text-sm text-neutral-500 mt-0.5">Management Portal</p>
                    </div>
                </div>

                {/* Navigation */}
                <nav className="space-y-2">
                    {tabs.map((tab, index) => (
                        <button
                            key={tab.id}
                            onClick={() => onTabChange(tab.id)}
                            className={`w-full flex items-center px-4 py-3.5 text-sm font-medium rounded-xl transition-all duration-300 group relative overflow-hidden ${
                                activeTab === tab.id
                                    ? `${theme.activeBg} ${theme.activeText} shadow-inner-soft ${theme.activeBorder} transform translate-x-1`
                                    : 'text-neutral-600 hover:bg-neutral-50 hover:text-neutral-900 hover:shadow-soft hover:translate-x-0.5'
                            }`}
                            style={{
                                animationDelay: `${index * 50}ms`
                            }}
                        >
                            {/* Active indicator */}
                            {activeTab === tab.id && (
                                <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary-500 rounded-r-full"></div>
                            )}
                            
                            <span className={`mr-4 text-lg transition-all duration-300 ${
                                activeTab === tab.id 
                                    ? `${theme.activeIcon} scale-110` 
                                    : 'text-neutral-400 group-hover:text-neutral-600 group-hover:scale-105'
                            }`}>
                                {tab.icon}
                            </span>
                            
                            <span className="flex-1 text-left">{tab.label}</span>
                            
                            {/* Subtle arrow for active tab */}
                            {activeTab === tab.id && (
                                <svg className="w-4 h-4 text-primary-500 animate-bounce-subtle" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                </svg>
                            )}
                        </button>
                    ))}
                </nav>
            </div>

            {/* Footer Section */}
            <div className="mt-auto p-6 bg-gradient-to-t from-neutral-50/50 to-transparent">
                <div className="bg-white rounded-2xl p-4 shadow-soft border border-neutral-100">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-secondary-500 rounded-xl flex items-center justify-center text-white font-bold text-sm shadow-soft">
                            {footerData?.charAt(0)?.toUpperCase() || 'H'}
                        </div>
                        <div className="flex-1 min-w-0">
                            <p className="text-xs font-semibold text-neutral-400 uppercase tracking-wider mb-0.5">{footerTitle}</p>
                            <p className="text-sm font-semibold text-neutral-800 truncate" title={footerData}>{footerData}</p>
                        </div>
                    </div>
                </div>
            </div>
        </aside>
    );
};

export default Sidebar;
