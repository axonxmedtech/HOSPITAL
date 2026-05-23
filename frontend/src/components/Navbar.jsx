import React from 'react';
import UserMenu from './UserMenu';

const Navbar = ({ title, user, onLogout, onProfile, onSupport, actions, onToggleSidebar }) => {
    return (
        <header className="bg-white border-b border-gray-200 z-10 w-full sticky top-0">
            <div className="flex justify-between items-center px-6 py-3">
                {/* Left side - Sidebar toggle button */}
                <button
                    onClick={onToggleSidebar}
                    className="p-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors"
                    title="Toggle sidebar"
                >
                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                    </svg>
                </button>

                {/* Right side - User menu only */}
                <div className="flex items-center gap-4">
                    {/* Quick actions if provided */}
                    {actions && (
                        <div className="flex items-center gap-3">
                            {actions}
                        </div>
                    )}

                    {/* User menu */}
                    <UserMenu
                        user={user}
                        onLogout={onLogout}
                        onProfile={onProfile}
                        onSupport={onSupport}
                    />
                </div>
            </div>
        </header>
    );
};

export default Navbar;
