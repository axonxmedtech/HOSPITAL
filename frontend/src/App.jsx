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

                    {/* DASHBOARDS — UNPROTECTED (UI MODE) */}
                    <Route
                        path="/platform/dashboard"
                        element={
                            <PageMeta title="HMS - Platform Admin">
                                <PlatformDashboard />
                            </PageMeta>
                        }
                    />

                    <Route
                        path="/hospital/admin"
                        element={
                            <PageMeta title="HMS - Hospital Admin">
                                <HospitalAdminDashboard />
                            </PageMeta>
                        }
                    />

                    <Route
                        path="/hospital/doctor"
                        element={
                            <PageMeta title="HMS - Doctor Portal">
                                <DoctorDashboard />
                            </PageMeta>
                        }
                    />

                    <Route
                        path="/hospital/receptionist"
                        element={
                            <PageMeta title="HMS - Reception">
                                <ReceptionistDashboard />
                            </PageMeta>
                        }
                    />

                    <Route
                        path="/hospital/pharmacy"
                        element={
                            <PageMeta title="HMS - Pharmacy">
                                <PharmacyDashboard />
                            </PageMeta>
                        }
                    />

                    <Route
                        path="/ipd/:id"
                        element={
                            <PageMeta title="HMS - IPD Details">
                                <IpdDetails />
                            </PageMeta>
                        }
                    />

                    {/* Default landing (pick one for UI work) */}
                    <Route path="/" element={<Navigate to="/hospital/admin" replace />} />

                    {/* Catch-all */}
                    <Route path="*" element={<Navigate to="/" replace />} />

                </Routes>
            </Router>
        </ToastProvider>
    );
}

export default App;
