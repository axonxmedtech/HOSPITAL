import { useCallback, useEffect, useState } from 'react';
import useDebounced from '../../hooks/useDebounced';
import useLiveRefresh from '../../hooks/useLiveRefresh';
import apiClient from '../../services/apiService';
import { extractApiError } from '../../utils/apiError';
import { rangeFor, rangeIsBackwards, rangeParams } from '../../utils/dateRange';

/**
 * Shared state for a server-filtered, paged history list: the view tab, date window, search
 * and page, and the rows the server returns for them.
 */
export default function useHistoryList(url, { defaultView, defaultRange = '30d', size = 20 }) {
  const [view, setView] = useState(defaultView);
  const [range, setRange] = useState(() => rangeFor(defaultRange));
  const [search, setSearch] = useState('');
  const q = useDebounced(search);
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => setPage(0), [view, range, q]);

  const load = useCallback(async () => {
    if (rangeIsBackwards(range)) {
      // The date picker already says what is wrong; wait for a valid range.
      setData({ content: [], totalPages: 0, totalElements: 0 });
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const res = await apiClient.get(url, {
        params: {
          view,
          page,
          size,
          ...(q.trim() ? { search: q.trim() } : {}),
          ...rangeParams(range),
        },
      });
      setData(res.data || { content: [] });
    } catch (e) {
      setError(extractApiError(e, 'Could not load records'));
      setData({ content: [], totalPages: 0, totalElements: 0 });
    } finally {
      setLoading(false);
    }
  }, [url, view, page, size, q, range]);

  useEffect(() => {
    void load();
  }, [load]);
  useLiveRefresh(load);

  return { view, setView, range, setRange, search, setSearch, page, setPage, data, loading, error };
}
