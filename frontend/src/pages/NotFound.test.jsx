import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import NotFound from './NotFound';

describe('NotFound', () => {
  it('shows a 404 page with the requested path and a way back', () => {
    render(
      <MemoryRouter initialEntries={['/hospital/does-not-exist']}>
        <Routes>
          <Route path="/" element={<p>home</p>} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByText('/hospital/does-not-exist')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to dashboard' })).toHaveAttribute('href', '/');
    expect(screen.queryByText('home')).not.toBeInTheDocument();
  });
});
