import { useEffect, useState } from 'react';
import { shotFor } from './shots';

/**
 * A screenshot for a help answer or guide step: a small preview that opens full size.
 * Renders nothing when the answer has no screenshot or the image is missing.
 */
export default function Shot({ shotKey }) {
  const shot = shotFor(shotKey);
  const [broken, setBroken] = useState(false);
  const [big, setBig] = useState(false);

  useEffect(() => {
    if (!big) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        setBig(false);
      }
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [big]);

  if (!shot || broken) return null;
  return (
    <>
      <figure className="space-y-1">
        <button
          type="button"
          onClick={() => setBig(true)}
          aria-label={`Enlarge screenshot: ${shot.caption}`}
          className="block w-full overflow-hidden rounded-lg border border-gray-200 hover:border-sky-300"
        >
          <img
            src={shot.src}
            alt={shot.caption}
            loading="lazy"
            onError={() => setBroken(true)}
            className="w-full h-auto"
          />
        </button>
        <figcaption className="text-[11px] text-gray-500">
          {shot.caption} · click to enlarge
        </figcaption>
      </figure>
      {big && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={shot.caption}
          className="fixed inset-0 z-[60] flex items-center justify-center p-4"
        >
          <button
            type="button"
            aria-label="Close screenshot"
            onClick={() => setBig(false)}
            className="absolute inset-0 bg-gray-900/70 cursor-zoom-out"
          />
          <figure className="relative max-w-6xl w-full space-y-2">
            <img
              src={shot.src}
              alt={shot.caption}
              className="w-full h-auto rounded-lg shadow-2xl"
            />
            <figcaption className="text-center text-sm text-white">{shot.caption}</figcaption>
          </figure>
        </div>
      )}
    </>
  );
}
