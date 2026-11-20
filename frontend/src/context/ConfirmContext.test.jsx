import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmProvider, useConfirm } from './ConfirmContext';

function Asker({ onAnswer }) {
  const confirm = useConfirm();
  return (
    <button
      type="button"
      onClick={async () =>
        onAnswer(
          await confirm({
            title: 'Remove bed 4?',
            message: 'It leaves service.',
            confirmLabel: 'Remove bed',
          })
        )
      }
    >
      ask
    </button>
  );
}

describe('useConfirm', () => {
  it('shows the question with the action as the button, and answers yes', async () => {
    const onAnswer = vi.fn();
    render(
      <ConfirmProvider>
        <Asker onAnswer={onAnswer} />
      </ConfirmProvider>
    );
    await userEvent.click(screen.getByRole('button', { name: 'ask' }));
    expect(screen.getByText('Remove bed 4?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove bed' }));
    expect(onAnswer).toHaveBeenCalledWith(true);
    expect(screen.queryByText('Remove bed 4?')).not.toBeInTheDocument();
  });

  it('answers no on Cancel or Esc', async () => {
    const onAnswer = vi.fn();
    render(
      <ConfirmProvider>
        <Asker onAnswer={onAnswer} />
      </ConfirmProvider>
    );
    await userEvent.click(screen.getByRole('button', { name: 'ask' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel and close dialog' }));
    await userEvent.click(screen.getByRole('button', { name: 'ask' }));
    await userEvent.keyboard('{Escape}');
    expect(onAnswer.mock.calls).toEqual([[false], [false]]);
  });

  it('outside a provider it answers yes, so a component works on its own', async () => {
    const onAnswer = vi.fn();
    render(<Asker onAnswer={onAnswer} />);
    await userEvent.click(screen.getByRole('button', { name: 'ask' }));
    expect(onAnswer).toHaveBeenCalledWith(true);
  });
});
