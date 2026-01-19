import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import PlatformLogin from './pages/platform/PlatformLogin';
import PlatformDashboard from './pages/platform/PlatformDashboard';
import HospitalLogin from './pages/hospital/HospitalLogin';
import HospitalAdminDashboard from './pages/hospital/HospitalAdminDashboard';
import DoctorDashboard from './pages/hospital/DoctorDashboard';
import ReceptionistDashboard from './pages/hospital/ReceptionistDashboard';
import PharmacyDashboard from './pages/hospital/PharmacyDashboard';
import ProtectedRoute from './components/auth/ProtectedRoute';
import authService from './services/authService';
import { ToastProvider } from './context/ToastContext';
import PageMeta from './components/PageMeta';

/**
 * App - Main application component
 * 
 * This component sets up routing for the entire application:
 * - Super Admin routes (/platform/*)
 * - Hospital user routes (/login, /hospital/*)
 * - Protected routes with role-based access control
 * - Dynamic Title & Favicon management via PageMeta
 * 
 * @author HMS Team
 * @version Phase-1
 */
function App() {
    return (
        <ToastProvider>
            <Router>
                <Routes>
                    {/* Public Routes */}
                    <Route
                        path="/platform/login"
                        element={
                            <PageMeta title="HMS - Super Admin Login" emoji="🔐">
                                <PlatformLogin />
                            </PageMeta>
                        }
                    />
                    <Route
                        path="/login"
                        element={
                            <PageMeta title="HMS - Staff Login" emoji="🏥">
                                <HospitalLogin />
                            </PageMeta>
                        }
                    />

                    {/* Super Admin Routes */}
                    <Route
                        path="/platform/dashboard"
                        element={
                            <PageMeta title="HMS - Platform Admin" emoji="🛡️">
                                <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
                                    <PlatformDashboard />
                                </ProtectedRoute>
                            </PageMeta>
                        }
                    />

                    {/* Hospital Admin Routes */}
                    <Route
                        path="/hospital/admin"
                        element={
                            <PageMeta title="HMS - Hospital Admin" emoji="🏥">
                                <ProtectedRoute allowedRoles={['HOSPITAL_ADMIN']}>
                                    <HospitalAdminDashboard />
                                </ProtectedRoute>
                            </PageMeta>
                        }
                    />

                    {/* Doctor Routes */}
                    <Route
                        path="/hospital/doctor"
                        element={
                            <PageMeta title="HMS - Doctor Portal" emoji="🩺">
                                <ProtectedRoute allowedRoles={['DOCTOR']}>
                                    <DoctorDashboard />
                                </ProtectedRoute>
                            </PageMeta>
                        }
                    />

                    {/* Receptionist Routes */}
                    <Route
                        path="/hospital/receptionist"
                        element={
                            <PageMeta title="HMS - Reception" emoji="🛎️">
                                <ProtectedRoute allowedRoles={['RECEPTIONIST']}>
                                    <ReceptionistDashboard />
                                </ProtectedRoute>
                            </PageMeta>
                        }
                    />

                    {/* Pharmacist Routes */}
                    <Route
                        path="/hospital/pharmacy"
                        element={
                            <PageMeta title="HMS - Pharmacy" emoji="💊">
                                <ProtectedRoute allowedRoles={['PHARMACIST']}>
                                    <PharmacyDashboard />
                                </ProtectedRoute>
                            </PageMeta>
                        }
                    />

                    {/* Default Route - Redirect based on authentication */}
                    <Route
                        path="/"
                        element={
                            authService.isAuthenticated() ? (
                                authService.isSuperAdmin() ? (
                                    <Navigate to="/platform/dashboard" replace />
                                ) : authService.isHospitalAdmin() ? (
                                    <Navigate to="/hospital/admin" replace />
                                ) : authService.isDoctor() ? (
                                    <Navigate to="/hospital/doctor" replace />
                                ) : authService.isReceptionist() ? (
                                    <Navigate to="/hospital/receptionist" replace />
                                ) : authService.isPharmacist() ? (
                                    <Navigate to="/hospital/pharmacy" replace />
                                ) : (
                                    <Navigate to="/login" replace />
                                )
                            ) : (
                                <Navigate to="/login" replace />
                            )
                        }
                    />

                    {/* Catch all - redirect to home */}
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </Router>
        </ToastProvider>
    );
}

export default App;
