import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import PlatformLogin from './pages/platform/PlatformLogin';
import PlatformDashboard from './pages/platform/PlatformDashboard';
import HospitalLogin from './pages/hospital/HospitalLogin';
import HospitalAdminDashboard from './pages/hospital/HospitalAdminDashboard';
import DoctorDashboard from './pages/hospital/DoctorDashboard';
import ReceptionistDashboard from './pages/hospital/ReceptionistDashboard';
import PharmacyDashboard from './pages/hospital/PharmacyDashboard';
import IpdDetails from './pages/hospital/IpdDetails';
import { ToastProvider } from './context/ToastContext';
import PageMeta from './components/PageMeta';
import authService from './services/authService';

const ProtectedRoute = ({ children, allowedRoles }) => {
    const user = authService.getCurrentUser();
    
    if (!user || !authService.isAuthenticated()) {
        return <Navigate to="/login" replace />;
    }
    
    if (allowedRoles && !allowedRoles.includes(user.role)) {
        return <Navigate to="/" replace />;
    }
    
    return children;
};

const LandingRedirect = () => {
    const user = authService.getCurrentUser();
    if (!user) {
        return <Navigate to="/login" replace />;
    }
    switch (user.role) {
        case 'SUPER_ADMIN':
            return <Navigate to="/platform/dashboard" replace />;
        case 'HOSPITAL_ADMIN':
            return <Navigate to="/hospital/admin" replace />;
        case 'DOCTOR':
            return <Navigate to="/hospital/doctor" replace />;
        case 'RECEPTIONIST':
            return <Navigate to="/hospital/receptionist" replace />;
        case 'PHARMACIST':
            return <Navigate to="/hospital/pharmacy" replace />;
        default:
            return <Navigate to="/login" replace />;
    }
};

function App() {
    return (
        <ToastProvider>
            <Router>
                <Routes>

                    {/* Public / Login Pages */}
                    <Route
                        path="/platform/login"
                        element={
                            <PageMeta title="HMS - Super Admin Login">
                                <PlatformLogin />
                            </PageMeta>
                        }
                    />

                    <Route
                        path="/login"
                        element={
                            <PageMeta title="HMS - Staff Login">
                                <HospitalLogin />
                            </PageMeta>
                        }
                    />

                    {/* DASHBOARDS — PROTECTED */}
                    <Route
                        path="/platform/dashboard"
                        element={
                            <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
                                <PageMeta title="HMS - Platform Admin">
                                    <PlatformDashboard />
                                </PageMeta>
                            </ProtectedRoute>
                        }
                    />

                    <Route
                        path="/hospital/admin"
                        element={
                            <ProtectedRoute allowedRoles={['HOSPITAL_ADMIN']}>
                                <PageMeta title="HMS - Hospital Admin">
                                    <HospitalAdminDashboard />
                                </PageMeta>
                            </ProtectedRoute>
                        }
                    />

                    <Route
                        path="/hospital/doctor"
                        element={
                            <ProtectedRoute allowedRoles={['DOCTOR']}>
                                <PageMeta title="HMS - Doctor Portal">
                                    <DoctorDashboard />
                                </PageMeta>
                            </ProtectedRoute>
                        }
                    />

                    <Route
                        path="/hospital/receptionist"
                        element={
                            <ProtectedRoute allowedRoles={['RECEPTIONIST']}>
                                <PageMeta title="HMS - Reception">
                                    <ReceptionistDashboard />
                                </PageMeta>
                            </ProtectedRoute>
                        }
                    />

                    <Route
                        path="/hospital/pharmacy"
                        element={
                            <ProtectedRoute allowedRoles={['PHARMACIST']}>
                                <PageMeta title="HMS - Pharmacy">
                                    <PharmacyDashboard />
                                </PageMeta>
                            </ProtectedRoute>
                        }
                    />

                    <Route
                        path="/ipd/:id"
                        element={
                            <ProtectedRoute allowedRoles={['RECEPTIONIST', 'DOCTOR', 'HOSPITAL_ADMIN']}>
                                <PageMeta title="HMS - IPD Details">
                                    <IpdDetails />
                                </PageMeta>
                            </ProtectedRoute>
                        }
                    />

                    {/* Default landing */}
                    <Route path="/" element={<LandingRedirect />} />

                    {/* Catch-all */}
                    <Route path="*" element={<Navigate to="/" replace />} />

                </Routes>
            </Router>
        </ToastProvider>
    );
}

export default App;
