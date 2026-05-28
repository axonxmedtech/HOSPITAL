import { useEffect, useRef } from 'react';
import authService from '../services/authService';

/**
 * useWebSocket - Custom hook for managing multi-tenant real-time WebSocket sync.
 * Handles auto-reconnection with exponential backoff and synchronizes profile settings
 * or triggers silent data reload.
 * 
 * @param {Object} user - The current logged in user state
 * @param {Function} setUser - React state setter for the logged in user
 * @param {Function} loadData - Silent data reload trigger function
 */
export default function useWebSocket(user, setUser, loadData) {
    const wsRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);
    const delayRef = useRef(1000); // Start with 1 second delay for reconnection

    const connect = () => {
        if (!user || !user.hospitalId) return;

        // Cleanup existing connection
        if (wsRef.current) {
            wsRef.current.close();
            wsRef.current = null;
        }

        const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
        const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        
        const token = sessionStorage.getItem('token');
        if (API_BASE_URL.startsWith('http://') || API_BASE_URL.startsWith('https://')) {
            wsUrl = API_BASE_URL.replace(/^http/, 'ws') + `/ws/hospital/${user.hospitalId}`;
        } else {
            // Relative base URL
            const host = window.location.host;
            wsUrl = `${wsProto}//${host}${API_BASE_URL.startsWith('/') ? '' : '/'}${API_BASE_URL}/ws/hospital/${user.hospitalId}`;
        }

        if (token) {
            wsUrl += `?token=${encodeURIComponent(token)}`;
        }

        console.log(`Connecting to WebSocket: ${wsUrl}`);
        const ws = new WebSocket(wsUrl);
        wsRef.current = ws;

        ws.onopen = () => {
            console.log('WebSocket connection established.');
            delayRef.current = 1000; // Reset reconnection delay
        };

        ws.onmessage = async (event) => {
            console.log('WebSocket message received:', event.data);
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'SETTINGS_UPDATED') {
                    console.log('Settings changed! Fetching fresh profile...');
                    try {
                        const profile = await authService.getProfile();
                        const updatedUser = authService.updateCurrentUser(profile);
                        if (setUser && updatedUser) {
                            setUser(updatedUser);
                        }
                    } catch (err) {
                        console.error('Failed to sync profile after settings update:', err);
                    }
                } else if (data.type === 'REFRESH_DATA') {
                    console.log('Data change detected! Triggering refresh...');
                    if (loadData) {
                        loadData(false); // Call loadData silently without full screen loading
                    }
                }
            } catch (err) {
                console.error('Error handling WebSocket message:', err);
            }
        };

        ws.onclose = (e) => {
            console.log(`WebSocket connection closed. Reason: ${e.reason || 'None'}, Code: ${e.code}. Reconnecting...`);
            wsRef.current = null;
            scheduleReconnect();
        };

        ws.onerror = (err) => {
            console.error('WebSocket error occurred:', err);
            ws.close();
        };
    };

    const scheduleReconnect = () => {
        if (reconnectTimeoutRef.current) return;

        // Exponential backoff with a cap of 30 seconds
        const delay = delayRef.current;
        delayRef.current = Math.min(delay * 2, 30000);

        console.log(`Scheduling reconnect in ${delay}ms`);
        reconnectTimeoutRef.current = setTimeout(() => {
            reconnectTimeoutRef.current = null;
            connect();
        }, delay);
    };

    useEffect(() => {
        connect();

        return () => {
            if (wsRef.current) {
                // Remove onclose listener to prevent reconnect when deliberately unmounting
                wsRef.current.onclose = null;
                wsRef.current.close();
                wsRef.current = null;
            }
            if (reconnectTimeoutRef.current) {
                clearTimeout(reconnectTimeoutRef.current);
                reconnectTimeoutRef.current = null;
            }
        };
    }, [user?.hospitalId]); // Reconnect if hospitalId changes (e.g. login/logout)
}
