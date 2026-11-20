import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../services/authService', () => ({
  default: { getProfile: vi.fn(), updateCurrentUser: vi.fn() },
}));

import { DATA_CHANGED } from './useLiveRefresh';
import useWebSocket from './useWebSocket';

const sockets = [];
class FakeSocket {
  constructor(url) {
    this.url = url;
    sockets.push(this);
  }
  close() {
    this.onclose?.();
  }
}

function Probe({ loadData }) {
  useWebSocket({ hospitalId: 3 }, vi.fn(), loadData);
  return null;
}

describe('useWebSocket live updates', () => {
  let changed;
  const onChanged = () => {
    changed += 1;
  };

  beforeEach(() => {
    vi.useFakeTimers();
    sockets.length = 0;
    changed = 0;
    vi.stubGlobal('WebSocket', FakeSocket);
    globalThis.addEventListener(DATA_CHANGED, onChanged);
  });
  afterEach(() => {
    globalThis.removeEventListener(DATA_CHANGED, onChanged);
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('a change reloads the dashboard and every live panel', async () => {
    const loadData = vi.fn();
    render(<Probe loadData={loadData} />);
    act(() => sockets[0].onopen());
    await act(async () => sockets[0].onmessage({ data: JSON.stringify({ type: 'REFRESH_DATA' }) }));
    expect(loadData).toHaveBeenCalledWith(false);
    expect(changed).toBe(1);
  });

  it('after the connection drops and comes back, catches up once', () => {
    const loadData = vi.fn();
    render(<Probe loadData={loadData} />);
    act(() => sockets[0].onopen()); // first connect: nothing missed yet
    expect(loadData).not.toHaveBeenCalled();

    act(() => sockets[0].close()); // dropped
    act(() => vi.advanceTimersByTime(1000)); // reconnects
    act(() => sockets[1].onopen());
    expect(loadData).toHaveBeenCalledTimes(1);
    expect(changed).toBe(1);
  });
});
