import React from 'react';
import UserMenu from './UserMenu';

const Navbar = ({ title, user, onLogout, onProfile, actions }) => {
    return (
        <header className="bg-white/95 backdrop-blur-sm border-b border-neutral-200 shadow-soft z-10 w-full sticky top-0">
            <div className="flex justify-between items-center px-8 py-6">
                <div className="flex items-center gap-6">
                    {/* Breadcrumb-style title */}
                    <div className="flex items-center gap-3">
                        <div className="w-2 h-2 bg-primary-500 rounded-full animate-pulse"></div>
                        <div>
                            <h2 className="text-2xl font-bold text-slate-800 tracking-tight capitalize flex items-center gap-2">
                                {title}
                                <span className="text-primary-500 text-lg">•</span>
                            </h2>
                            <p className="text-sm text-slate-500 mt-0.5 font-medium">
                                {getSubtitleForTab(title)}
                            </p>
                        </div>
                    </div>
                </div>

                <div className="flex items-center gap-6">
                    {/* Quick stats or actions */}
                    {actions && (
                        <div className="flex items-center gap-3">
                            {actions}
                        </div>
                    )}

                    {/* Divider */}
                    <div className="h-8 w-px bg-neutral-200 hidden sm:block"></div>

                    {/* User menu */}
                    <UserMenu
                        user={user}
                        onLogout={onLogout}
                        onProfile={onProfile}
                    />
                </div>
            </div>
        </header>
    );
};

// Helper function to get contextual subtitles
const getSubtitleForTab = (tab) => {
    const subtitles = {
        dashboard: 'Overview and quick insights',
        patients: 'Manage patient records and information',
        doctors: 'Healthcare provider management',
        appointments: 'Schedule and track consultations',
        billing: 'Financial records and payments',
        receptionists: 'Front desk staff management',
        pharmacists: 'Pharmacy team administration',
        'audit-logs': 'System activity and changes',
        pharmacy: 'Inventory and prescriptions',
        pathology: 'Lab tests and results',
        ipd: 'Inpatient department management'
    };
    
    return subtitles[tab] || 'Manage your activities and view reports';
};

export default Navbar;
