import { useState, useEffect, useCallback } from 'react';
import { extractApiError, isRequestCancelled } from '../utils/apiError';

// Usage:
// const { data, loading, error, refetch } = useFetch(
//   () => hospitalService.getPatients({ page, search }),
//   [page, search]
// );
export function useFetch(fetchFn, deps = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const execute = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchFn();
      setData(result);
    } catch (err) {
      if (!isRequestCancelled(err)) {
        setError(extractApiError(err, 'An error occurred'));
      }
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    execute();
  }, [execute]);

  return { data, loading, error, refetch: execute };
}
