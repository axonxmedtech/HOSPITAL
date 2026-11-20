import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import useLiveRefresh, { announceDataChanged } from './useLiveRefresh';

function Probe({ reload, enabled = true }) {
  useLiveRefresh(reload, enabled);
  return null;
}

const setHidden = (hidden) => {
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
  document.dispatchEvent(new Event('visibilitychange'));
};

describe('useLiveRefresh', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.useRealTimers();
    setHidden(false);
  });

  it('reloads once for a burst of changes', () => {
    const reload = vi.fn();
    render(<Probe reload={reload} />);
    act(() => {
      announceDataChanged();
      announceDataChanged();
      announceDataChanged();
      vi.advanceTimersByTime(300);
    });
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('does nothing when switched off, or after unmount', () => {
    const reload = vi.fn();
    const { rerender, unmount } = render(<Probe reload={reload} enabled={false} />);
    act(() => {
      announceDataChanged();
      vi.advanceTimersByTime(300);
    });
    expect(reload).not.toHaveBeenCalled();
    rerender(<Probe reload={reload} />);
    unmount();
    act(() => {
      announceDataChanged();
      vi.advanceTimersByTime(300);
    });
    expect(reload).not.toHaveBeenCalled();
  });

  it('a background tab catches up once when it is looked at again', () => {
    const reload = vi.fn();
    render(<Probe reload={reload} />);
    act(() => setHidden(true));
    act(() => {
      announceDataChanged();
      vi.advanceTimersByTime(300);
      announceDataChanged();
      vi.advanceTimersByTime(300);
    });
    expect(reload).not.toHaveBeenCalled();
    act(() => setHidden(false));
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('always calls the latest reload', () => {
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(<Probe reload={first} />);
    rerender(<Probe reload={second} />);
    act(() => {
      announceDataChanged();
      vi.advanceTimersByTime(300);
    });
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);
  });
});
