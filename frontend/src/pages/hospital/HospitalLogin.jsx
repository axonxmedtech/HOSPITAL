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
        <div className="min-h-screen bg-white flex">
            {/* Left Side - Background Image */}
            <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden">
                {/* Background Image */}
                <div 
                    className="absolute inset-0 bg-cover bg-center bg-no-repeat"
                    style={{ backgroundImage: 'url(/main-login-page.png)' }}
                ></div>
                
                {/* Overlay for better text readability */}
                <div className="absolute inset-0 bg-black bg-opacity-40"></div>

                {/* Content */}
                <div className="relative z-10 flex flex-col justify-center items-center w-full px-16">
                    {/* Logo/Icon */}
                    <div className="mb-12">
                        <div className="w-24 h-24 bg-white rounded-2xl shadow-lg flex items-center justify-center mb-8">
                            <svg className="w-12 h-12 text-gray-900" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                            </svg>
                        </div>
                        <h1 className="text-4xl font-bold text-white text-center mb-4">Hospital Management System</h1>
                        <p className="text-xl text-white text-center leading-relaxed opacity-90">
                            Comprehensive Patient Care Platform
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
                                <div className="mb-6 bg-white border border-gray-200 p-4">
                                    <div className="flex items-start">
                                        <span className="text-gray-900 font-medium mr-3">Error:</span>
                                        <p className="text-sm text-gray-900">{errors.submit}</p>
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
                                        className="w-full px-3 py-2 bg-white border border-gray-200 text-gray-900 focus:border-gray-900"
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
                                        className={`w-full px-3 py-2 bg-white border text-gray-900 placeholder-gray-500 focus:border-gray-900 disabled:opacity-60 disabled:cursor-not-allowed ${errors.email ? 'border-gray-900' : 'border-gray-200'}`}
                                    />
                                    {errors.email && <p className="text-gray-900 text-sm mt-1">{errors.email}</p>}
                                </div>

                                {/* Password Field */}
                                <div className="space-y-2">
                                    <label htmlFor="password" className="block text-sm font-medium text-gray-900">
                                        Password
                                    </label>
                                    <input
                                        type="password"
                                        id="password"
                                        value={password}
                                        onChange={handlePasswordChange}
                                        placeholder="Enter your secure password"
                                        disabled={loading}
                                        className={`w-full px-3 py-2 bg-white border text-gray-900 placeholder-gray-500 focus:border-gray-900 disabled:opacity-60 disabled:cursor-not-allowed ${errors.password ? 'border-gray-900' : 'border-gray-200'}`}
                                    />
                                    {errors.password && <p className="text-gray-900 text-sm mt-1">{errors.password}</p>}
                                </div>

                                {/* Submit Button */}
                                <div className="pt-4">
                                    <button
                                        type="submit"
                                        disabled={loading}
                                        className="w-full bg-gray-900 hover:bg-gray-700 text-white font-medium py-3 px-4 transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
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

                        {/* Footer */}
                        <div className="px-6 py-4 bg-gray-50 border-t border-gray-200">
                            <div className="text-center">
                                <p className="text-gray-600 text-sm">
                                    System Administrator?{' '}
                                    <a href="/platform/login" className="text-gray-900 font-medium hover:underline">
                                        Platform Access
                                    </a>
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default HospitalLogin;
