import React from 'react';

const Sidebar = ({ title, tabs, activeTab, onTabChange, footerTitle, footerData, variant = 'plain' }) => {
    // Professional color variants - only 2 colors: gray-900 and white
    const variants = {
        plain: {
            bgIcon: 'bg-white',
            textIcon: 'text-gray-900',
            activeBg: 'bg-gray-100',
            activeText: 'text-gray-900',
            activeIcon: 'text-gray-900',
            activeBorder: 'border-l-4 border-gray-900'
        },
        blue: {
            bgIcon: 'bg-blue-50',
            textIcon: 'text-blue-600',
            activeBg: 'bg-blue-50',
            activeText: 'text-blue-900',
            activeIcon: 'text-blue-600',
            activeBorder: 'border-l-4 border-blue-600'
        }
    };

    const theme = variants[variant] || variants.plain;

    return (
        <aside className="w-72 bg-white z-20 hidden md:flex flex-col h-full border-r border-gray-200 overflow-hidden">
            {/* Header Section */}
            <div className="p-6 border-b border-gray-200">
                <div className="mb-6">
                    <h1 className="text-xl font-bold text-gray-900">{title}</h1>
                    <p className="text-sm text-gray-600 mt-1">Management Portal</p>
                </div>

                {/* Navigation */}
                <nav className="space-y-1">
                    {tabs.map((tab) => (
                        <button
                            key={tab.id}
                            onClick={() => onTabChange(tab.id)}
                            className={`w-full flex items-center px-3 py-2 text-sm font-medium transition-colors duration-200 ${
                                activeTab === tab.id
                                    ? `${theme.activeBg} ${theme.activeText} ${theme.activeBorder}`
                                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                            }`}
                        >
                            {/* Active indicator */}
                            {activeTab === tab.id && (
                                <div className="absolute left-0 top-0 bottom-0 w-1 bg-gray-900"></div>
                            )}
                            
                            <span className="flex-1 text-left">{tab.label}</span>
                        </button>
                    ))}
                </nav>
            </div>

            {/* Footer Section */}
            <div className="mt-auto p-6 bg-gray-50 border-t border-gray-200">
                <div className="bg-white border border-gray-200 p-4">
                    <div className="flex items-center gap-3">
                        <div className="w-8 h-8 bg-gray-900 text-white font-bold text-xs flex items-center justify-center">
                            {footerData?.charAt(0)?.toUpperCase() || 'A'}
                        </div>
                        <div className="flex-1 min-w-0">
                            <p className="text-xs font-medium text-gray-600 uppercase mb-0.5">{footerTitle}</p>
                            <p className="text-sm font-medium text-gray-900 truncate" title={footerData}>{footerData}</p>
                        </div>
                    </div>
                </div>
            </div>
        </aside>
    );
};

export default Sidebar;
