import { useEffect, useState } from 'react';

/** Debounced search text, so typing doesn't fire a request per key. */
export default function useDebounced(value, ms = 350) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}
