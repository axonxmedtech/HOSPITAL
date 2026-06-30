import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import PlatformLogin from './pages/platform/PlatformLogin';
import PlatformDashboard from './pages/platform/PlatformDashboard';
import HospitalLogin from './pages/hospital/HospitalLogin';
import HospitalAdminDashboard from './pages/hospital/HospitalAdminDashboard';
import DoctorDashboard from './pages/hospital/DoctorDashboard';
import ReceptionistDashboard from './pages/hospital/ReceptionistDashboard';
import PharmacyDashboard from './pages/hospital/PharmacyDashboard';
import IpdDetails from './pages/hospital/IpdDetails';
import NurseDashboard from './pages/hospital/NurseDashboard';
import LabTechnicianDashboard from './pages/hospital/LabTechnicianDashboard';
import RadiologyTechnicianDashboard from './pages/hospital/RadiologyTechnicianDashboard';
import MrdArchive from './pages/hospital/MrdArchive';
import { ToastProvider } from './context/ToastContext';
import PageMeta from './components/PageMeta';
import authService from './services/authService';

const ProtectedRoute = ({ children, allowedRoles }) => {
    const user = authService.getCurrentUser();
    
    if (!user || !authService.isAuthenticated()) {
        return <Navigate to="/login/hospital" replace />;
    }
    
    // In Single Doctor Hospital mode, the Hospital Admin role can access BOTH doctor and admin routes
    // In Standalone Pharmacy Mode, the Hospital Admin role can access BOTH pharmacist and admin routes
    const isAllowed = !allowedRoles || allowedRoles.some(role => {
        if (role === user.role) return true;
        if (role === 'DOCTOR' && user.role === 'HOSPITAL_ADMIN' && user.isSingleDoctor) return true;
        const isStandalonePharmacy = user.modules?.includes('PHARMACY') && !user.modules?.includes('OPD');
        if (role === 'PHARMACIST' && user.role === 'HOSPITAL_ADMIN' && isStandalonePharmacy) return true;
        return false;
    });

    if (allowedRoles && !isAllowed) {
        return <Navigate to="/" replace />;
    }
    
    return children;
};

const LandingRedirect = () => {
    const user = authService.getCurrentUser();
    if (!user) {
        return <Navigate to="/login/hospital" replace />;
    }
    switch (user.role) {
        case 'SUPER_ADMIN':
            return <Navigate to="/platform/dashboard" replace />;
        case 'HOSPITAL_ADMIN': {
            const isStandalonePharmacy = user.modules?.includes('PHARMACY') && !user.modules?.includes('OPD');
            if (user.isSingleDoctor) {
                const preference = sessionStorage.getItem('activeDashboard');
                if (preference === 'admin') {
                    return <Navigate to="/hospital/admin" replace />;
                }
                return <Navigate to="/hospital/doctor" replace />;
            } else if (isStandalonePharmacy) {
                const preference = sessionStorage.getItem('activeDashboard');
                if (preference === 'admin') {
                    return <Navigate to="/hospital/admin" replace />;
                }
                return <Navigate to="/hospital/pharmacy" replace />;
            }
            return <Navigate to="/hospital/admin" replace />;
        }
        case 'DOCTOR':
            return <Navigate to="/hospital/doctor" replace />;
        case 'RECEPTIONIST':
            return <Navigate to="/hospital/receptionist" replace />;
        case 'PHARMACIST':
            return <Navigate to="/hospital/pharmacy" replace />;
        case 'NURSE':
            return <Navigate to="/nurse-dashboard" replace />;
        case 'LAB_TECHNICIAN':
            return <Navigate to="/lab-dashboard" replace />;
        case 'RADIOLOGY_TECHNICIAN':
            return <Navigate to="/radiology-dashboard" replace />;
        default:
            return <Navigate to="/login/hospital" replace />;
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

                    <Route path="/login" element={<Navigate to="/login/hospital" replace />} />

                    <Route
                        path="/login/hospital"
                        element={
                            <PageMeta title="HMS - Hospital Login">
                                <HospitalLogin portalType="HOSPITAL" />
                            </PageMeta>
                        }
                    />

                    <Route
                        path="/login/clinic"
                        element={
                            <PageMeta title="HMS - Clinic Login">
                                <HospitalLogin portalType="CLINIC" />
                            </PageMeta>
                        }
                    />

                    <Route
                        path="/login/pharmacy"
                        element={
                            <PageMeta title="HMS - Pharmacy Login">
                                <HospitalLogin portalType="PHARMACY" />
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
                        path="/hospital/admin/mrd"
                        element={
                            <ProtectedRoute allowedRoles={['HOSPITAL_ADMIN']}>
                                <PageMeta title="HMS - MRD Archive">
                                    <MrdArchive />
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
                        path="/hospital/doctor/mrd"
                        element={
                            <ProtectedRoute allowedRoles={['DOCTOR', 'HOSPITAL_ADMIN']}>
                                <PageMeta title="HMS - MRD Archive">
                                    <MrdArchive />
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
                        path="/hospital/receptionist/mrd"
                        element={
                            <ProtectedRoute allowedRoles={['RECEPTIONIST', 'HOSPITAL_ADMIN']}>
                                <PageMeta title="HMS - MRD Archive">
                                    <MrdArchive />
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

                    <Route
                        path="/nurse-dashboard"
                        element={
                            <ProtectedRoute allowedRoles={['NURSE']}>
                                <PageMeta title="HMS - Nurse Dashboard">
                                    <NurseDashboard />
                                </PageMeta>
                            </ProtectedRoute>
                        }
                    />

                    <Route
                        path="/lab-dashboard"
                        element={
                            <ProtectedRoute allowedRoles={['LAB_TECHNICIAN']}>
                                <PageMeta title="HMS - Lab Technician">
                                    <LabTechnicianDashboard />
                                </PageMeta>
                            </ProtectedRoute>
                        }
                    />

                    <Route
                        path="/radiology-dashboard"
                        element={
                            <ProtectedRoute allowedRoles={['RADIOLOGY_TECHNICIAN']}>
                                <PageMeta title="HMS - Radiology Technician">
                                    <RadiologyTechnicianDashboard />
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
