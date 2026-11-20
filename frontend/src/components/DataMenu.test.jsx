import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import DataMenu from './DataMenu';

describe('DataMenu', () => {
  it('opens on click and runs the chosen action', async () => {
    const user = userEvent.setup();
    const importFn = vi.fn();
    const pdf = vi.fn();
    render(
      <DataMenu
        items={[
          { label: 'Import Patients', onClick: importFn },
          { label: 'Download PDF', onClick: pdf },
        ]}
      />
    );
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Data/ }));
    await user.click(screen.getByRole('menuitem', { name: 'Download PDF' }));
    expect(pdf).toHaveBeenCalled();
    expect(importFn).not.toHaveBeenCalled();
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('leaves out actions a role does not have, and closes on Escape', async () => {
    const user = userEvent.setup();
    render(<DataMenu items={[false, { label: 'Download PDF', onClick: vi.fn() }]} />);
    await user.click(screen.getByRole('button', { name: /Data/ }));
    expect(screen.getAllByRole('menuitem')).toHaveLength(1);
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});
