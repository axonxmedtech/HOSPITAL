import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import GroupedTabs, { groupTabs } from './GroupedTabs';

const all = [
  { id: 'overview', label: 'Overview' },
  { id: 'investigations', label: 'Investigations' },
  { id: 'vitals', label: 'Vitals' },
  { id: 'medication', label: 'Medication' },
  { id: 'assessment', label: 'Initial Assessment' },
  { id: 'sugar', label: 'Sugar Chart' },
  { id: 'surprise', label: 'Something new' },
];

function Host({ tabs = all, start = 'overview' }) {
  const [tab, setTab] = useState(start);
  return (
    <>
      <GroupedTabs tabs={tabs} value={tab} onChange={setTab} />
      <div data-testid="open">{tab}</div>
    </>
  );
}

describe('groupTabs', () => {
  it('sorts tabs into four groups, keeps their order and hides empty groups', () => {
    const groups = groupTabs(all.filter((t) => t.id !== 'assessment'));
    expect(groups.map((g) => g.id)).toEqual(['summary', 'orders', 'charts']);
    expect(groups[1].items.map((t) => t.id)).toEqual(['medication', 'investigations']); // most used first
    expect(groups[0].items.map((t) => t.id)).toEqual(['overview', 'surprise']); // unknown → Summary
  });
});

describe('GroupedTabs', () => {
  it('opens a group on its first tab and shows its tabs as a second row', () => {
    render(<Host />);
    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual([
      'Summary',
      'Orders',
      'Charts',
      'Forms',
      'Overview',
      'Something new',
    ]);
    fireEvent.click(screen.getByRole('tab', { name: 'Charts' }));
    expect(screen.getByTestId('open')).toHaveTextContent('vitals');
    expect(screen.getByRole('tab', { name: 'Sugar Chart' })).toBeInTheDocument();
  });

  it('goes back to the tab last used in a group', () => {
    render(<Host />);
    fireEvent.click(screen.getByRole('tab', { name: 'Charts' }));
    fireEvent.click(screen.getByRole('tab', { name: 'Sugar Chart' }));
    fireEvent.click(screen.getByRole('tab', { name: 'Orders' }));
    fireEvent.click(screen.getByRole('tab', { name: 'Charts' }));
    expect(screen.getByTestId('open')).toHaveTextContent('sugar');
  });

  it('shows no second row for a group with a single tab', () => {
    render(<Host start="assessment" />);
    expect(screen.getByRole('tab', { name: 'Forms' })).toHaveAttribute(
      'aria-selected',
      'true'
    );
    expect(screen.queryByRole('tab', { name: 'Initial Assessment' })).toBeNull();
  });
});
