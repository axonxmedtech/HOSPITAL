import StatusBadge from "./StatusBadge";

// Overview Dashboard Component for Hospital Admin
const OverviewDashboard = ({ stats, todaysAppointments, loading }) => {
    if (loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="relative">
                    <div className="w-16 h-16 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin"></div>
                    <div className="absolute inset-0 w-16 h-16 border-4 border-transparent border-t-secondary-400 rounded-full animate-spin" style={{ animationDirection: 'reverse', animationDuration: '1.5s' }}></div>
                </div>
            </div>
        );
    }

    const statCards = [
        {
            title: 'Total Patients',
            value: stats.totalPatients || 0,
            icon: '👥',
            color: 'primary',
            bgGradient: 'from-primary-500 to-primary-600',
            iconBg: 'bg-primary-100',
            change: '+12%',
            changeType: 'positive'
        },
        {
            title: 'Total Doctors',
            value: stats.totalDoctors || 0,
            icon: '👨‍⚕️',
            color: 'success',
            bgGradient: 'from-success-500 to-success-600',
            iconBg: 'bg-success-100',
            change: '+3%',
            changeType: 'positive'
        },
        {
            title: "Today's Appointments",
            value: stats.todaysAppointments || 0,
            icon: '📅',
            color: 'secondary',
            bgGradient: 'from-secondary-500 to-secondary-600',
            iconBg: 'bg-secondary-100',
            change: '+8%',
            changeType: 'positive'
        }
    ];

    return (
        <div className="space-y-8">
            {/* Enhanced Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {statCards.map((card, index) => (
                    <div 
                        key={card.title}
                        className="card-hover group relative overflow-hidden"
                        style={{ animationDelay: `${index * 100}ms` }}
                    >
                        {/* Background gradient overlay */}
                        <div className={`absolute inset-0 bg-gradient-to-br ${card.bgGradient} opacity-0 group-hover:opacity-5 transition-opacity duration-300`}></div>
                        
                        <div className="relative p-6">
                            <div className="flex items-center justify-between">
                                <div className="flex-1">
                                    <p className="text-sm font-semibold text-slate-600 uppercase tracking-wider mb-2">
                                        {card.title}
                                    </p>
                                    <div className="flex items-baseline gap-2">
                                        <p className="text-3xl font-bold text-slate-900 group-hover:scale-105 transition-transform duration-300">
                                            {card.value.toLocaleString()}
                                        </p>
                                        <span className={`text-xs font-medium px-2 py-1 rounded-full ${
                                            card.changeType === 'positive' 
                                                ? 'text-success-700 bg-success-100' 
                                                : 'text-alert-700 bg-alert-100'
                                        }`}>
                                            {card.change}
                                        </span>
                                    </div>
                                </div>
                                
                                <div className={`${card.iconBg} p-4 rounded-2xl shadow-soft group-hover:shadow-soft-lg group-hover:scale-110 transition-all duration-300`}>
                                    <span className="text-3xl">{card.icon}</span>
                                </div>
                            </div>
                            
                            {/* Progress bar */}
                            <div className="mt-4 w-full bg-neutral-100 rounded-full h-1.5 overflow-hidden">
                                <div 
                                    className={`h-full bg-gradient-to-r ${card.bgGradient} rounded-full transition-all duration-1000 ease-out`}
                                    style={{ width: `${Math.min((card.value / 100) * 100, 100)}%` }}
                                ></div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* Enhanced Today's Appointments Table */}
            <div className="card overflow-hidden">
                <div className="bg-gradient-to-r from-neutral-50 to-neutral-100/50 px-8 py-6 border-b border-neutral-200">
                    <div className="flex items-center justify-between">
                        <div>
                            <h3 className="text-xl font-bold text-slate-900 flex items-center gap-3">
                                <span className="w-2 h-2 bg-primary-500 rounded-full animate-pulse"></span>
                                Today's Appointments
                            </h3>
                            <p className="text-sm text-slate-600 mt-1">
                                Quick overview of all scheduled appointments for today
                            </p>
                        </div>
                        <div className="flex items-center gap-2 text-sm text-slate-500">
                            <span className="w-2 h-2 bg-success-400 rounded-full"></span>
                            Live updates
                        </div>
                    </div>
                </div>

                {todaysAppointments.length > 0 ? (
                    <div className="overflow-x-auto">
                        <table className="min-w-full">
                            <thead className="bg-neutral-50/50">
                                <tr>
                                    <th className="px-8 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                                        Patient
                                    </th>
                                    <th className="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                                        Doctor
                                    </th>
                                    <th className="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                                        Time
                                    </th>
                                    <th className="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                                        Status
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-neutral-100">
                                {todaysAppointments.map((appointment, index) => (
                                    <tr 
                                        key={appointment.id} 
                                        className="hover:bg-neutral-50 transition-colors duration-200 group"
                                        style={{ animationDelay: `${index * 50}ms` }}
                                    >
                                        <td className="px-8 py-5 whitespace-nowrap">
                                            <div className="flex items-center gap-3">
                                                <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-secondary-500 rounded-xl flex items-center justify-center text-white font-semibold text-sm shadow-soft">
                                                    {appointment.patientName?.charAt(0)?.toUpperCase()}
                                                </div>
                                                <div>
                                                    <p className="text-sm font-semibold text-slate-900 group-hover:text-primary-600 transition-colors">
                                                        {appointment.patientName}
                                                    </p>
                                                    <p className="text-xs text-slate-500">
                                                        ID: {appointment.patientId}
                                                    </p>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-5 whitespace-nowrap">
                                            <div className="flex items-center gap-2">
                                                <span className="text-lg">👨‍⚕️</span>
                                                <span className="text-sm font-medium text-slate-700">
                                                    {appointment.doctorName}
                                                </span>
                                            </div>
                                        </td>
                                        <td className="px-6 py-5 whitespace-nowrap">
                                            <div className="flex items-center gap-2">
                                                <span className="text-lg">🕐</span>
                                                <span className="text-sm text-slate-600">
                                                    {new Date(appointment.appointmentDate).toLocaleDateString()}
                                                </span>
                                            </div>
                                        </td>
                                        <td className="px-6 py-5 whitespace-nowrap">
                                            <StatusBadge status={appointment.status} />
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : (
                    <div className="p-16 text-center">
                        <div className="w-20 h-20 bg-neutral-100 rounded-3xl flex items-center justify-center mx-auto mb-4 shadow-inner-soft">
                            <span className="text-4xl text-neutral-400">📅</span>
                        </div>
                        <h4 className="text-lg font-semibold text-slate-900 mb-2">No appointments today</h4>
                        <p className="text-slate-500 max-w-sm mx-auto">
                            It looks like there are no appointments scheduled for today. Enjoy the quiet day!
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
};

export default OverviewDashboard;
