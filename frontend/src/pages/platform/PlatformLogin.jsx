import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import authService from '../../services/authService';
import { validateForm } from '../../utils/validation';

/**
 * PlatformLogin - Super Admin login page
 * 
 * This page allows Super Admin to log in to the platform.
 * Route: /platform/login
 * 
 * @author HMS Team
 * @version Phase-1
 */
const PlatformLogin = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
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
            // Call Super Admin login API
            await authService.platformLogin(email, password);

            // Redirect to platform dashboard
            navigate('/platform/dashboard');
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
                <h1 className="text-3xl font-bold text-gray-800 text-center mb-2">Super Admin Login</h1>
                <p className="text-gray-600 text-center text-sm mb-8">Platform Management Portal</p>

                {errors.submit && (
                    <div className="bg-red-50 border-l-4 border-red-500 text-red-700 p-4 mb-6 rounded">
                        {errors.submit}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="space-y-6">
                    <div>
                        <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
                            Email
                        </label>
                        <input
                            type="email"
                            id="email"
                            value={email}
                            onChange={handleEmailChange}
                            placeholder="Enter your email"
                            disabled={loading}
                            className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent disabled:bg-gray-100 disabled:cursor-not-allowed transition ${errors.email ? 'border-red-500' : 'border-gray-300'}`}
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
                            className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent disabled:bg-gray-100 disabled:cursor-not-allowed transition ${errors.password ? 'border-red-500' : 'border-gray-300'}`}
                        />
                        {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-gradient-to-r from-primary-500 to-secondary-500 text-white font-semibold py-3 px-4 rounded-lg hover:shadow-lg transform hover:-translate-y-0.5 transition disabled:opacity-60 disabled:cursor-not-allowed disabled:transform-none"
                    >
                        {loading ? 'Logging in...' : 'Login'}
                    </button>
                </form>

                <div className="mt-6 text-center text-sm text-gray-600">
                    <p>
                        Hospital user?{' '}
                        <a href="/login" className="text-primary-600 font-semibold hover:underline">
                            Login here
                        </a>
                    </p>
                </div>
            </div>
        </div>
    );
};

export default PlatformLogin;
