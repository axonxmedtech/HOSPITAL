import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import authService from '../../services/authService';
import { validateForm } from '../../utils/validation';

/**
 * HospitalLogin - Hospital user login page
 * 
 * This page allows Hospital Admin and Doctor to log in.
 * Route: /login
 * 
 * @author HMS Team
 * @version Phase-1
 */
const HospitalLogin = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [selectedRole, setSelectedRole] = useState('HOSPITAL_ADMIN'); // Default role
    const [errors, setErrors] = useState({});
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrors({});

        // Validation
        const validationErrors = validateForm({ email, password }, {
            email: ['required', 'email'],
            password: ['required']
        });

        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            return;
        }

        setLoading(true);

        try {
            // Call Hospital user login API
            const response = await authService.hospitalLogin(email, password);

            // 1. Strict Role Validation (Frontend Check)
            // Ensure the user is logging in with the role they SELECTED
            if (response.role !== selectedRole) {
                authService.logout(); // Clear the successful session
                setErrors({ submit: `Access Denied: You are not registered as a ${selectedRole === 'HOSPITAL_ADMIN' ? 'Hospital Admin' : selectedRole === 'DOCTOR' ? 'Doctor' : selectedRole === 'PHARMACIST' ? 'Pharmacist' : 'Receptionist'}.` });
                return;
            }

            // 2. Redirect based on Verified Role
            if (response.role === 'HOSPITAL_ADMIN') {
                navigate('/hospital/admin');
            } else if (response.role === 'DOCTOR') {
                navigate('/hospital/doctor');
            } else if (response.role === 'RECEPTIONIST') {
                navigate('/hospital/receptionist');
            } else if (response.role === 'PHARMACIST') {
                navigate('/hospital/pharmacy');
            } else {
                setErrors({ submit: 'Invalid user role' });
            }
        } catch (err) {
            console.error('Login error:', err);
            // Extract error message from various possible error formats
            let errorMessage = 'Login failed. Please check your credentials.';

            if (err.response?.data) {
                // If backend returns a string message
                if (typeof err.response.data === 'string') {
                    errorMessage = err.response.data;
                }
                // If backend returns an object with message property
                else if (err.response.data.message) {
                    errorMessage = err.response.data.message;
                }
            } else if (err.message) {
                errorMessage = err.message;
            }

            setErrors({ submit: errorMessage });
        } finally {
            setLoading(false);
        }
    };

    const handleRoleChange = (e) => {
        setSelectedRole(e.target.value);
        setErrors({}); // Clear errors when switching roles context (optional but nice)
    };

    const handleEmailChange = (e) => {
        setEmail(e.target.value);
        if (errors.email) setErrors(prev => ({ ...prev, email: null }));
    };

    const handlePasswordChange = (e) => {
        setPassword(e.target.value);
        if (errors.password) setErrors(prev => ({ ...prev, password: null }));
    };

    return (
        <div className="min-h-screen bg-neutral-50 flex items-center justify-center px-6 py-12">
            {/* Background Pattern */}
            <div className="absolute inset-0 bg-gradient-to-br from-neutral-50 via-white to-neutral-100"></div>
            
            {/* Login Container */}
            <div className="relative w-full max-w-lg">
                <div className="bg-white rounded-2xl shadow-soft-lg border border-neutral-200 overflow-hidden">
                    
                    {/* Header Section */}
                    <div className="px-12 pt-12 pb-8 text-center bg-gradient-to-b from-white to-neutral-50">
                        <div className="inline-flex items-center justify-center w-20 h-20 bg-primary-50 rounded-2xl mb-6 shadow-inner-soft border border-primary-100">
                            <svg className="w-10 h-10 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 14l-7 7m0 0l-7-7m7 7V3" />
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12l2 2 4-4" />
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                        </div>
                        <h1 className="text-2xl font-semibold text-slate-800 mb-2">Hospital Staff Portal</h1>
                        <p className="text-slate-600 text-sm leading-relaxed">
                            Secure access to patient care systems
                        </p>
                    </div>

                    {/* Form Section */}
                    <div className="px-12 pb-12">
                        {errors.submit && (
                            <div className="mb-8 p-4 bg-alert-50 border border-alert-200 rounded-xl">
                                <div className="flex items-start">
                                    <svg className="w-5 h-5 text-alert-500 mt-0.5 mr-3 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                                    </svg>
                                    <p className="text-alert-700 text-sm">{errors.submit}</p>
                                </div>
                            </div>
                        )}

                        <form onSubmit={handleSubmit} className="space-y-8">
                            {/* Role Selection */}
                            <div className="space-y-3">
                                <label htmlFor="role" className="block text-sm font-medium text-slate-700">
                                    Department Access
                                </label>
                                <select
                                    id="role"
                                    value={selectedRole}
                                    onChange={handleRoleChange}
                                    className="w-full px-4 py-4 bg-neutral-50 border border-neutral-300 rounded-xl text-slate-800 focus:bg-white focus:border-primary-400 focus:ring-4 focus:ring-primary-100 transition-all duration-200"
                                >
                                    <option value="HOSPITAL_ADMIN">Hospital Administration</option>
                                    <option value="DOCTOR">Medical Staff</option>
                                    <option value="RECEPTIONIST">Reception & Registration</option>
                                    <option value="PHARMACIST">Pharmacy Services</option>
                                </select>
                            </div>

                            {/* Email Field */}
                            <div className="space-y-3">
                                <label htmlFor="email" className="block text-sm font-medium text-slate-700">
                                    Email Address
                                </label>
                                <input
                                    type="email"
                                    id="email"
                                    value={email}
                                    onChange={handleEmailChange}
                                    placeholder="your.name@hospital.com"
                                    disabled={loading}
                                    className={`w-full px-4 py-4 bg-neutral-50 border rounded-xl text-slate-800 placeholder-slate-400 focus:bg-white focus:border-primary-400 focus:ring-4 focus:ring-primary-100 transition-all duration-200 disabled:opacity-60 disabled:cursor-not-allowed ${errors.email ? 'border-alert-300 bg-alert-50' : 'border-neutral-300'}`}
                                />
                                {errors.email && <p className="text-alert-600 text-sm mt-2">{errors.email}</p>}
                            </div>

                            {/* Password Field */}
                            <div className="space-y-3">
                                <label htmlFor="password" className="block text-sm font-medium text-slate-700">
                                    Password
                                </label>
                                <input
                                    type="password"
                                    id="password"
                                    value={password}
                                    onChange={handlePasswordChange}
                                    placeholder="Enter your secure password"
                                    disabled={loading}
                                    className={`w-full px-4 py-4 bg-neutral-50 border rounded-xl text-slate-800 placeholder-slate-400 focus:bg-white focus:border-primary-400 focus:ring-4 focus:ring-primary-100 transition-all duration-200 disabled:opacity-60 disabled:cursor-not-allowed ${errors.password ? 'border-alert-300 bg-alert-50' : 'border-neutral-300'}`}
                                />
                                {errors.password && <p className="text-alert-600 text-sm mt-2">{errors.password}</p>}
                            </div>

                            {/* Submit Button */}
                            <div className="pt-4">
                                <button
                                    type="submit"
                                    disabled={loading}
                                    className="w-full bg-primary-600 hover:bg-primary-700 text-white font-medium py-4 px-6 rounded-xl transition-all duration-200 disabled:opacity-60 disabled:cursor-not-allowed focus:ring-4 focus:ring-primary-200 shadow-soft"
                                >
                                    {loading ? (
                                        <span className="flex items-center justify-center">
                                            <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                            </svg>
                                            Authenticating...
                                        </span>
                                    ) : (
                                        'Access Portal'
                                    )}
                                </button>
                            </div>
                        </form>

                        {/* Footer Link */}
                        <div className="mt-10 pt-8 border-t border-neutral-200 text-center">
                            <p className="text-slate-500 text-sm">
                                System Administrator?{' '}
                                <a href="/platform/login" className="text-primary-600 font-medium hover:text-primary-700 transition-colors">
                                    Platform Access
                                </a>
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default HospitalLogin;
