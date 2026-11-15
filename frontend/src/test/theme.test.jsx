import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ThemeToggle from '../components/ThemeToggle';
import { STORAGE_KEY } from '../theme/theme';

function mockSystemTheme(dark) {
  vi.stubGlobal('matchMedia', (query) => ({
    matches: dark && query.includes('dark'),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }));
}

describe('Thème clair / sombre', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.classList.remove('dark');
  });
  afterEach(() => {
    window.localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  it('suit le thème du système quand aucun choix n\'est mémorisé', () => {
    mockSystemTheme(true);
    render(<ThemeToggle />);
    expect(document.documentElement).toHaveClass('dark');
    expect(screen.getByRole('button', { name: 'Activer le thème clair' })).toBeInTheDocument();
  });

  it('le choix mémorisé l\'emporte sur le système', () => {
    mockSystemTheme(true);
    window.localStorage.setItem(STORAGE_KEY, 'light');
    render(<ThemeToggle />);
    expect(document.documentElement).not.toHaveClass('dark');
  });

  it('bascule le thème et mémorise le choix', async () => {
    mockSystemTheme(false);
    const user = userEvent.setup();
    render(<ThemeToggle />);
    expect(document.documentElement).not.toHaveClass('dark');

    await user.click(screen.getByRole('button', { name: 'Activer le thème sombre' }));
    expect(document.documentElement).toHaveClass('dark');
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('dark');

    await user.click(screen.getByRole('button', { name: 'Activer le thème clair' }));
    expect(document.documentElement).not.toHaveClass('dark');
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('light');
  });
});
