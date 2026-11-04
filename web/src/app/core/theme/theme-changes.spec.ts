// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { ThemeChanges } from './theme-changes';

describe('ThemeChanges', () => {
  const root = document.documentElement;
  afterEach(() => {
    root.style.removeProperty('--f-primary');
    root.classList.remove('f-print-panel');
    root.removeAttribute('data-x');
  });

  /** O MutationObserver avisa nunha microtarefa. */
  const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

  it('un só observador para toda a páxina: version() cambia co estilo ou a clase de <html>', async () => {
    const created = vi.spyOn(window, 'MutationObserver');
    const changes = TestBed.inject(ThemeChanges);
    expect(TestBed.inject(ThemeChanges)).toBe(changes);
    expect(created).toHaveBeenCalledTimes(1);
    const start = changes.version();
    root.setAttribute('data-x', '1');
    await tick();
    expect(changes.version()).toBe(start);
    root.style.setProperty('--f-primary', '#7a1f2b');
    await tick();
    expect(changes.version()).toBe(start + 1);
    root.classList.add('f-print-panel');
    await tick();
    expect(changes.version()).toBe(start + 2);
    created.mockRestore();
  });
});
