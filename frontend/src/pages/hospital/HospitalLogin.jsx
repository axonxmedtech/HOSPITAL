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
        <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-500 to-secondary-500 px-4">
            <div className="bg-white rounded-xl shadow-2xl p-8 w-full max-w-md">
                <div className="text-center mb-8">
                    <div className="bg-primary-100 w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-4">
                        <span className="text-2xl">🏥</span>
                    </div>
                    <h1 className="text-3xl font-bold text-gray-800">Welcome Back</h1>
                    <p className="text-gray-600 text-sm mt-1">Sign in to manage your hospital</p>
                </div>

                {errors.submit && (
                    <div className="bg-red-50 border-l-4 border-red-500 text-red-700 p-4 mb-6 rounded flex items-center">
                        <span className="mr-2">⚠️</span>
                        {errors.submit}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="space-y-5">

                    {/* Role Selection Dropdown */}
                    <div>
                        <label htmlFor="role" className="block text-sm font-semibold text-gray-700 mb-2">
                            Select User Role
                        </label>
                        <select
                            id="role"
                            value={selectedRole}
                            onChange={handleRoleChange}
                            className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-gray-50 transition"
                        >
                            <option value="HOSPITAL_ADMIN">Hospital Admin</option>
                            <option value="DOCTOR">Doctor</option>
                            <option value="RECEPTIONIST">Receptionist</option>
                            <option value="PHARMACIST">Pharmacist</option>
                        </select>
                    </div>

                    <div>
                        <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
                            Email Address
                        </label>
                        <input
                            type="email"
                            id="email"
                            value={email}
                            onChange={handleEmailChange}
                            placeholder="name@hospital.com"
                            disabled={loading}
                            className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition ${errors.email ? 'border-red-500' : 'border-gray-300'}`}
                        />
                        {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email}</p>}
                    </div>

                    <div>
                        <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-2">
                            Password
                        </label>
                        <input
                            type="password"
                            id="password"
                            value={password}
                            onChange={handlePasswordChange}
                            placeholder="Enter your password"
                            disabled={loading}
                            className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition ${errors.password ? 'border-red-500' : 'border-gray-300'}`}
                        />
                        {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-gradient-to-r from-primary-600 to-secondary-600 text-white font-bold py-3 px-4 rounded-lg hover:shadow-lg transform hover:-translate-y-0.5 transition duration-200 disabled:opacity-70 disabled:cursor-not-allowed"
                    >
                        {loading ? 'Verifying Access...' : 'Login'}
                    </button>
                </form>

                <div className="mt-6 text-center text-sm text-gray-600">
                    <p>
                        Super Admin?{' '}
                        <a href="/platform/login" className="text-primary-600 font-semibold hover:underline">
                            Login here
                        </a>
                    </p>
                </div>
            </div>
        </div>
    );
};

export default HospitalLogin;
