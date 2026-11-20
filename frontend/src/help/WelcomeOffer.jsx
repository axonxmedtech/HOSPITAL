import { progressStore, useHelpProgress } from './progressStore';
import { TOURS } from './tourData';
import { tourStore } from './tourStore';

/**
 * Offers the one-minute tour to someone using AxonX for the first time. Shown once per user
 * (remembered on the server), never to automated browsers, and never over another tour.
 */
export default function WelcomeOffer({ firstName }) {
  const progress = useHelpProgress();
  const automated = typeof navigator !== 'undefined' && navigator.webdriver === true;
  if (automated || !progressStore.isLoaded() || progress.welcome || progress.tours.basics)
    return null;
  if (tourStore.get().tour) return null;

  const start = () => {
    progressStore.markWelcome();
    tourStore.start(TOURS.find((t) => t.id === 'basics'));
  };
  return (
    <section
      aria-label="Welcome tour"
      className="fixed bottom-4 left-4 z-30 w-[calc(100%-2rem)] sm:w-80 bg-white border border-sky-200 rounded-xl shadow-xl p-4 space-y-2"
    >
      <p className="text-sm font-semibold text-gray-900">
        Welcome{firstName ? `, ${firstName}` : ''}! New to AxonX?
      </p>
      <p className="text-sm text-gray-600">
        Take a one-minute tour of where everything is. You can find it later under Help → Guides.
      </p>
      <div className="flex justify-end gap-2 pt-1">
        <button
          type="button"
          onClick={progressStore.markWelcome}
          className="px-3 py-1.5 rounded-lg text-sm font-semibold text-gray-600 hover:bg-gray-100"
        >
          Not now
        </button>
        <button
          type="button"
          onClick={start}
          className="px-3 py-1.5 rounded-lg text-sm font-semibold bg-gray-900 text-white hover:bg-gray-800"
        >
          Take the tour
        </button>
      </div>
    </section>
  );
}
