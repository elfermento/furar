// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { activityRequest, flushPage, renderActivity } from '../../../../testing/activity';
import { backend } from '../../../../testing/api';

/** Anchos das columnas do rexistro (`furar-column-resize`), por persoa e por rexistro. */
describe('Rexistro de actividade: anchos das columnas', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('anchos: o bordo de cada cabeceira ensancha a columna co teclado; gárdase por persoa; dobre clic devolve o ancho e non hai «Restablecer anchos»', async () => {
    const key = 'furar.columns.user-1.activity-municipality';
    localStorage.removeItem(key);
    try {
      const { el, settle } = await renderActivity();
      await flushPage(await activityRequest(), settle);
      const col = () => el.querySelector<HTMLElement>('col[data-col="person"]')!;
      expect(col().style.width).toBe('224px');
      // A Acción colle o que sobra: sen ancho nin tirador.
      expect(el.querySelector<HTMLElement>('col[data-col="action"]')!.style.width).toBe('');
      expect(el.querySelector('thead [data-col="action"] [role="separator"]')).toBeNull();
      expect(el.querySelector('[data-reset-widths]')).toBeNull();
      const handle = el.querySelector<HTMLElement>('thead [data-col="person"] [role="separator"]')!;
      expect(handle.getAttribute('aria-label')).toBe('Ancho da columna «Persoa»');
      expect(handle.getAttribute('aria-valuenow')).toBe('224');
      handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
      await settle();
      expect(col().style.width).toBe('240px');
      expect(handle.getAttribute('aria-valuenow')).toBe('240');
      expect(JSON.parse(localStorage.getItem(key) ?? '{}')).toEqual({ person: 240 });
      expect(el.querySelector('[data-reset-widths]')).toBeNull();
      handle.dispatchEvent(new MouseEvent('dblclick'));
      await settle();
      expect(col().style.width).toBe('224px');
      expect(localStorage.getItem(key)).toBeNull();
      expect(el.querySelector('[data-reset-widths]')).toBeNull();
    } finally {
      localStorage.removeItem(key);
    }
  });
});
