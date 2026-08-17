import React, { useEffect, useRef, useState } from 'react';

const MAX_EDGE = 2000;

/**
 * `getUserMedia` preview, shutter, retake, downscale.
 *
 * The stream is stopped in every exit path — unmount, Cancel, and the case where the component
 * unmounts while the permission prompt is still pending — because a leaked camera track leaves the
 * device's recording light on, which reads to a user as the app spying on them.
 */
const CameraCapture = ({ onCapture, onCancel }) => {
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const [error, setError] = useState(null);
  // The frame just captured, held here so Retake can discard it without ever calling onCapture.
  const [preview, setPreview] = useState(null); // { url, file } | null

  useEffect(() => {
    let cancelled = false;

    // getUserMedia only exists on a secure origin. On plain HTTP the whole object is undefined
    // with no prompt and no error, so say so rather than leaving a button that does nothing.
    if (!navigator.mediaDevices?.getUserMedia) {
      setError('The camera needs a secure (https) connection. Use Browse instead.');
      return undefined;
    }

    navigator.mediaDevices
      // Rear camera on a phone; ignored by desktops with one webcam.
      .getUserMedia({ video: { facingMode: 'environment' }, audio: false })
      .then((stream) => {
        // The component can unmount while this promise is in flight. Without this the stream is
        // assigned to a dead component and never stopped, and the camera light stays on.
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current) videoRef.current.srcObject = stream;
      })
      .catch((e) => {
        if (cancelled) return;
        setError(
          e?.name === 'NotAllowedError'
            ? 'Camera permission was refused. Allow it in your browser, or use Browse.'
            : 'No camera is available on this device. Use Browse instead.'
        );
      });

    return () => {
      cancelled = true;
      streamRef.current?.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    };
  }, []);

  // Revoke the preview object URL whenever it is replaced or the component goes away.
  useEffect(() => {
    return () => {
      if (preview) URL.revokeObjectURL(preview.url);
    };
  }, [preview]);

  /**
   * Capture, downscaled to 2000px on the long edge.
   *
   * A modern phone photo is 4-12 MB of detail nobody needs to read a lab printout, and that
   * difference is an upload that works on clinic wifi versus one that times out.
   */
  const capture = () => {
    const video = videoRef.current;
    if (!video) return;

    const scale = Math.min(1, MAX_EDGE / Math.max(video.videoWidth, video.videoHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(video.videoWidth * scale);
    canvas.height = Math.round(video.videoHeight * scale);
    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob(
      (blob) => {
        if (!blob) return;
        const stamp = new Date().toISOString().slice(0, 10);
        const file = new File([blob], `record-${stamp}.jpg`, { type: 'image/jpeg' });
        setPreview({ url: URL.createObjectURL(blob), file });
      },
      'image/jpeg',
      0.85
    );
  };

  const retake = () => {
    if (preview) URL.revokeObjectURL(preview.url);
    setPreview(null);
  };

  const usePhoto = () => {
    if (preview) onCapture(preview.file);
  };

  if (error) {
    return (
      <div className="flex flex-col items-center gap-4 py-8 text-center">
        <svg
          className="w-8 h-8 text-amber-500"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
        <p className="text-sm text-gray-600 max-w-xs">{error}</p>
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
        >
          Back
        </button>
      </div>
    );
  }

  if (preview) {
    return (
      <div className="flex flex-col items-center gap-4">
        <img
          src={preview.url}
          alt="Captured record, ready to use or retake"
          className="max-h-80 rounded-lg border border-gray-200"
        />
        <div className="flex gap-3">
          <button
            type="button"
            onClick={retake}
            className="px-4 py-2 text-sm text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            Retake
          </button>
          <button
            type="button"
            onClick={usePhoto}
            className="px-4 py-2 text-sm font-semibold text-white bg-gray-900 rounded-lg hover:bg-gray-800 transition-colors"
          >
            Use Photo
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-4">
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        className="w-full max-h-80 rounded-lg border border-gray-200 bg-black"
      />
      <div className="flex gap-3">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={capture}
          className="px-5 py-2 text-sm font-semibold text-white bg-gray-900 rounded-lg hover:bg-gray-800 transition-colors"
        >
          Take Photo
        </button>
      </div>
    </div>
  );
};

export default CameraCapture;
