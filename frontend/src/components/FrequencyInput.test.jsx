import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React, { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import FrequencyInput, { FREQUENCY_PRESETS } from './FrequencyInput';

const Harness = ({ initialValue = '', disabled = false }) => {
  const [value, setValue] = useState(initialValue);
  return (
    <>
      <FrequencyInput value={value} onChange={setValue} disabled={disabled} />
      <output data-testid="frequency-value">{value}</output>
    </>
  );
};

const boxes = () => ({
  morning: screen.getByRole('textbox', { name: 'Morning dose' }),
  afternoon: screen.getByRole('textbox', { name: 'Afternoon dose' }),
  night: screen.getByRole('textbox', { name: 'Night dose' }),
});

/** The chip for a preset, addressed the way a prescriber reads it: "1-0-1 BD". */
const chip = (preset) => screen.getByRole('button', { name: `${preset.value} ${preset.label}` });

describe('FrequencyInput', () => {
  it('replaces a clicked dose digit predictably without changing the frequency format', async () => {
    const user = userEvent.setup();
    render(<Harness initialValue="1-0-1" />);

    const morning = screen.getByRole('textbox', { name: 'Morning dose' });
    await user.click(morning);
    await user.keyboard('2');

    expect(morning).toHaveValue('2');
    expect(screen.getByTestId('frequency-value')).toHaveTextContent('2-0-1');
  });

  it('does not turn a pasted multi-digit value into an arbitrary last digit', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    const night = screen.getByRole('textbox', { name: 'Night dose' });
    await user.click(night);
    await user.paste('12');

    expect(night).toHaveValue('1');
    expect(screen.getByTestId('frequency-value')).toHaveTextContent('0-0-1');
  });
});

describe('FrequencyInput — quick presets', () => {
  it('offers exactly the four approved presets, and not QID', () => {
    render(<Harness />);

    expect(FREQUENCY_PRESETS.map((p) => p.value)).toEqual(['1-0-0', '1-0-1', '1-1-1', '0-0-1']);
    FREQUENCY_PRESETS.forEach((preset) => expect(chip(preset)).toBeInTheDocument());
    // QID needs a fourth dose slot, which this frequency contract does not have.
    expect(screen.queryByRole('button', { name: /1-1-1-1/ })).not.toBeInTheDocument();
  });

  it.each(FREQUENCY_PRESETS)('$label fills the boxes and emits $value', async (preset) => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(chip(preset));

    expect(screen.getByTestId('frequency-value')).toHaveTextContent(preset.value);
    const [m, a, n] = preset.value.split('-');
    expect(boxes().morning).toHaveValue(m);
    expect(boxes().afternoon).toHaveValue(a);
    expect(boxes().night).toHaveValue(n);
  });

  it('marks the preset that matches the current value, including one typed by hand', async () => {
    const user = userEvent.setup();
    const bd = FREQUENCY_PRESETS.find((p) => p.value === '1-0-1');
    const tds = FREQUENCY_PRESETS.find((p) => p.value === '1-1-1');
    render(<Harness initialValue="1-0-1" />);

    // Typed or clicked, the chip only ever describes the value it matches.
    expect(chip(bd)).toHaveAttribute('aria-pressed', 'true');
    expect(chip(tds)).toHaveAttribute('aria-pressed', 'false');

    await user.click(chip(tds));
    expect(chip(tds)).toHaveAttribute('aria-pressed', 'true');
    expect(chip(bd)).toHaveAttribute('aria-pressed', 'false');
  });

  it('leaves every preset unmarked for a value none of them describes', () => {
    render(<Harness initialValue="2-0-1" />);

    FREQUENCY_PRESETS.forEach((preset) =>
      expect(chip(preset)).toHaveAttribute('aria-pressed', 'false')
    );
  });

  it('clears "As Per Required" when a preset is chosen', async () => {
    const user = userEvent.setup();
    render(<Harness initialValue="As Per Required" />);

    const sos = screen.getByRole('checkbox');
    expect(sos).toBeChecked();
    expect(boxes().morning).toBeDisabled();

    await user.click(chip(FREQUENCY_PRESETS.find((p) => p.value === '1-1-1')));

    expect(sos).not.toBeChecked();
    expect(screen.getByTestId('frequency-value')).toHaveTextContent('1-1-1');
    expect(boxes().morning).toBeEnabled();
  });

  it('keeps manual entry working after a preset is used', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(chip(FREQUENCY_PRESETS.find((p) => p.value === '1-0-1')));
    expect(screen.getByTestId('frequency-value')).toHaveTextContent('1-0-1');

    // The boxes are still the source of truth: editing one adjusts the preset's value.
    const afternoon = boxes().afternoon;
    await user.click(afternoon);
    await user.keyboard('2');

    expect(afternoon).toHaveValue('2');
    expect(screen.getByTestId('frequency-value')).toHaveTextContent('1-2-1');
  });

  it('never submits the surrounding form', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn((e) => e.preventDefault());
    const preset = FREQUENCY_PRESETS[0];
    render(
      <form onSubmit={onSubmit}>
        <FrequencyInput value="" onChange={() => {}} />
      </form>
    );

    expect(chip(preset)).toHaveAttribute('type', 'button');
    await user.click(chip(preset));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('honours the disabled state', async () => {
    const user = userEvent.setup();
    const preset = FREQUENCY_PRESETS[0];
    render(<Harness disabled />);

    expect(chip(preset)).toBeDisabled();
    await user.click(chip(preset));
    expect(screen.getByTestId('frequency-value')).toBeEmptyDOMElement();
  });
});
