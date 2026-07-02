import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import patientPortalService from '../../services/patientPortalService';

export default function PatientPortalLogin() {
  const { hospitalId } = useParams();
  const navigate = useNavigate();
  const [step, setStep] = useState('mobile'); // mobile | otp
  const [mobile, setMobile] = useState('');
  const [uhid, setUhid] = useState('');
  const [otp, setOtp] = useState('');
  const [error, setError] = useState('');
  const [needsUhid, setNeedsUhid] = useState(false);
  const [loading, setLoading] = useState(false);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const requestOtp = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await patientPortalService.requestOtp(Number(hospitalId), mobile, uhid || undefined);
      setStep('otp');
    } catch (ex) {
      const message = err(ex, 'Failed to send OTP');
      if (typeof message === 'string' && message.includes('Multiple records')) {
        setNeedsUhid(true);
      }
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const verifyOtp = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await patientPortalService.verifyOtp(Number(hospitalId), mobile, otp);
      const data = res?.data || res;
      sessionStorage.setItem('token', data.token);
      sessionStorage.setItem('user', JSON.stringify({ role: 'PATIENT', patientId: data.patientId, name: data.patientName }));
      sessionStorage.setItem('portalHospitalId', hospitalId);
      navigate('/portal/dashboard');
    } catch (ex) {
      setError(err(ex, 'Invalid OTP'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 max-w-md w-full space-y-5">
        <h1 className="text-xl font-bold text-gray-900">Patient Portal</h1>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-3 py-2">{error}</div>}

        {step === 'mobile' && (
          <form onSubmit={requestOtp} className="space-y-3">
            <input required placeholder="Mobile Number" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
              value={mobile} onChange={e => setMobile(e.target.value)} />
            {needsUhid && (
              <input required placeholder="Patient ID (UHID)" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                value={uhid} onChange={e => setUhid(e.target.value)} />
            )}
            <button disabled={loading} className="w-full bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white text-sm font-semibold py-2.5 rounded-lg">
              {loading ? 'Sending…' : 'Send OTP'}
            </button>
          </form>
        )}

        {step === 'otp' && (
          <form onSubmit={verifyOtp} className="space-y-3">
            <p className="text-sm text-gray-600">If that number is registered, an OTP has been sent to {mobile}.</p>
            <input required placeholder="6-digit OTP" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
              value={otp} onChange={e => setOtp(e.target.value)} />
            <button disabled={loading} className="w-full bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white text-sm font-semibold py-2.5 rounded-lg">
              {loading ? 'Verifying…' : 'Verify & Login'}
            </button>
            <button
              type="button"
              onClick={() => { setStep('mobile'); setOtp(''); setError(''); }}
              className="w-full text-center text-xs text-gray-500 hover:text-gray-700"
            >
              Wrong number? Go back
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
