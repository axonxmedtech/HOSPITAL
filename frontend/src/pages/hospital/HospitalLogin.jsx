import React, { useState } from 'react';
import { EyeIcon, EyeSlashIcon } from '@heroicons/react/24/outline';
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
    const [showPassword, setShowPassword] = useState(false);
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
            // In Single Doctor Hospital mode, the Hospital Admin can log in as either Hospital Admin or Doctor
            // In Standalone Pharmacy Mode, the Hospital Admin can log in as either Hospital Admin or Pharmacist
            const isStandalonePharmacy = response.modules?.includes('PHARMACY') && !response.modules?.includes('OPD');
            const isValidRole = response.role === selectedRole ||
                (response.role === 'HOSPITAL_ADMIN' && response.isSingleDoctor && selectedRole === 'DOCTOR') ||
                (response.role === 'HOSPITAL_ADMIN' && isStandalonePharmacy && selectedRole === 'PHARMACIST');

            if (!isValidRole) {
                authService.logout(); // Clear the successful session
                setErrors({ submit: `Access Denied: You are not registered as a ${selectedRole === 'HOSPITAL_ADMIN' ? 'Hospital Admin' : selectedRole === 'DOCTOR' ? 'Doctor' : selectedRole === 'PHARMACIST' ? 'Pharmacist' : 'Receptionist'}.` });
                return;
            }

            // Save the selected dashboard context preference to session storage
            if (response.role === 'HOSPITAL_ADMIN' && (response.isSingleDoctor || isStandalonePharmacy)) {
                sessionStorage.setItem('activeDashboard', selectedRole === 'DOCTOR' ? 'doctor' : selectedRole === 'PHARMACIST' ? 'pharmacy' : 'admin');
            }

            // 2. Redirect based on Verified Role
            if (response.role === 'HOSPITAL_ADMIN') {
                if (response.isSingleDoctor && selectedRole === 'DOCTOR') {
                    navigate('/hospital/doctor');
                } else if (isStandalonePharmacy && selectedRole === 'PHARMACIST') {
                    navigate('/hospital/pharmacy');
                } else {
                    navigate('/hospital/admin');
                }
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
        <div className="min-h-screen bg-white flex">
            <div className="z-10 absolute top-0 left-5 space-y-10">
                <img src="/company-logo.png" alt="company-logo" className="z-20 w-24 h-12" />
            </div>

            {/* Left Side - Background Image */}
            <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden">
                {/* Background Image */}
                <div
                    className="absolute inset-0 bg-cover bg-center bg-no-repeat"
                    style={{ backgroundImage: 'url(/main-login-page.png)' }}
                ></div>

                {/* Overlay for better text readability */}
                <div className="absolute inset-0 bg-black bg-opacity-40 z-10"></div>

                {/* Content */}
                <div className="relative z-10 flex flex-col justify-center items-center w-full px-16">
                    {/* Logo/Icon */}
                    <div className="mb-12">
                        <div className="w-[60%] h-[20%] bg-gray-400 shadow-lg flex items-center justify-center mb-8 m-auto">
                            <img src="/logo.png" alt="Hospital Icon" className="w-full h-full rounded-[30%]" />
                        </div>
                        <h1 className="text-4xl font-bold text-white text-center mb-4">Hospital Management System</h1>
                        <p className="text-xl text-white text-center leading-relaxed opacity-90">
                            Comprehensive Patient Care & Management Platform
                        </p>
                    </div>
                </div>
            </div>

            {/* Right Side - Login Form */}
            <div className="w-full lg:w-1/2 flex items-center justify-center p-8 bg-gray-50">
                <div className="w-full max-w-md">
                    {/* Mobile Logo (visible only on small screens) */}
                    <div className="lg:hidden text-center mb-8">
                        <div className="inline-flex items-center justify-center w-16 h-16 bg-white border border-gray-200 mb-4">
                            <svg className="w-8 h-8 text-gray-900" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                            </svg>
                        </div>
                        <h1 className="text-2xl font-bold text-gray-900 mb-2">Hospital Staff Portal</h1>
                        <p className="text-gray-600 text-sm">Secure access to patient care systems</p>
                    </div>

                    {/* Login Card */}
                    <div className="bg-white border border-gray-200 overflow-hidden">
                        {/* Header */}
                        <div className="p-6 border-b border-gray-200 text-center">
                            <h2 className="text-xl font-bold text-gray-900 mb-2">Staff Login</h2>
                            <p className="text-gray-600">Access your department portal</p>
                        </div>

                        {/* Form Section */}
                        <div className="p-6">
                            {errors.submit && (
                                <div className="mb-6 bg-red-50 border border-red-300 p-4 rounded-lg">
                                    <div className="flex items-start">
                                        <span className="text-red-700 font-medium mr-3">Error:</span>
                                        <p className="text-sm text-red-700">{errors.submit}</p>
                                    </div>
                                </div>
                            )}

                            <form onSubmit={handleSubmit} className="space-y-4">
                                {/* Role Selection */}
                                <div className="space-y-2">
                                    <label htmlFor="role" className="block text-sm font-medium text-gray-900">
                                        Department Access
                                    </label>
                                    <select
                                        id="role"
                                        value={selectedRole}
                                        onChange={handleRoleChange}
                                        className="w-full h-11 px-4 py-2.5 text-sm bg-white border border-gray-300 rounded-lg text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                    >
                                        <option value="HOSPITAL_ADMIN">Hospital Administration</option>
                                        <option value="DOCTOR">Medical Staff</option>
                                        <option value="RECEPTIONIST">Reception & Registration</option>
                                        <option value="PHARMACIST">Pharmacy Services</option>
                                    </select>
                                </div>

                                {/* Email Field */}
                                <div className="space-y-2">
                                    <label htmlFor="email" className="block text-sm font-medium text-gray-900">
                                        Email Address
                                    </label>
                                    <input
                                        type="email"
                                        id="email"
                                        value={email}
                                        onChange={handleEmailChange}
                                        placeholder="your.name@hospital.com"
                                        disabled={loading}
                                        className={`w-full h-11 px-4 py-2.5 text-sm bg-white border rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-60 disabled:cursor-not-allowed ${errors.email ? 'border-red-400 bg-red-50' : 'border-gray-300'}`}
                                    />
                                    {errors.email && <p className="text-red-600 text-sm mt-1">{errors.email}</p>}
                                </div>

                                {/* Password Field */}
                                <div className="space-y-2">
                                    <label htmlFor="password" className="block text-sm font-medium text-gray-900">
                                        Password
                                    </label>
                                    <div className="relative">
                                        <input
                                            type={showPassword ? 'text' : 'password'}
                                            id="password"
                                            value={password}
                                            onChange={handlePasswordChange}
                                            placeholder="Enter your secure password"
                                            disabled={loading}
                                            className={`w-full h-11 px-4 py-2.5 pr-10 text-sm bg-white border rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-60 disabled:cursor-not-allowed ${errors.password ? 'border-red-400 bg-red-50' : 'border-gray-300'}`}
                                        />
                                        <button
                                            type="button"
                                            onClick={() => setShowPassword(prev => !prev)}
                                            className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 focus:outline-none"
                                            tabIndex={-1}
                                            aria-label={showPassword ? 'Hide password' : 'Show password'}
                                        >
                                            {showPassword
                                                ? <EyeSlashIcon className="w-4 h-4" />
                                                : <EyeIcon className="w-4 h-4" />}
                                        </button>
                                    </div>
                                    {errors.password && <p className="text-red-600 text-sm mt-1">{errors.password}</p>}
                                </div>

                                {/* Submit Button */}
                                <div className="pt-4">
                                    <button
                                        type="submit"
                                        disabled={loading}
                                        className="w-full h-11 bg-gray-900 hover:bg-gray-700 text-white text-sm font-semibold rounded-lg px-4 transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
                                    >
                                        {loading ? (
                                            <span className="flex items-center justify-center">
                                                <div className="w-4 h-4 border-2 border-white border-t-transparent animate-spin mr-2"></div>
                                                Authenticating...
                                            </span>
                                        ) : (
                                            'Access Portal'
                                        )}
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default HospitalLogin;
