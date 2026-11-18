// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { ValueListComponent } from './value-list.component';

/** Un rectángulo de proba: `top` e `height` en píxeles. */
function rect(top: number, height = 20): DOMRect {
  return {
    top,
    bottom: top + height,
    height,
    left: 0,
    right: 0,
    width: 0,
    x: 0,
    y: top,
    toJSON: () => ({}),
  } as DOMRect;
}

describe('furar-value-list variant="plain"', () => {
  async function render(wrapsRow: string | null) {
    const fixture = TestBed.createComponent(ValueListComponent);
    fixture.componentRef.setInput('variant', 'plain');
    fixture.componentRef.setInput('items', [
      { id: 'a', label: 'Pavimento', value: 'Céspede' },
      { id: 'b', label: 'Capacidade da bancada', value: 'Moitas persoas' },
    ]);
    const el = fixture.nativeElement as HTMLElement;
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      const row = this.closest('.f-value-list__row')?.getAttribute('data-value');
      // O valor baixa á liña seguinte só na fila que non cabe.
      return this.tagName === 'DD' && row === wrapsRow ? rect(24) : rect(0);
    });
    fixture.detectChanges();
    await fixture.whenStable();
    return el;
  }

  afterEach(() => vi.restoreAllMocks());

  it('sen liñas: se todas caben, en liña', async () => {
    const el = await render(null);
    expect(el.getAttribute('data-variant')).toBe('plain');
    expect(el.hasAttribute('data-stacked')).toBe(false);
  });

  it('se unha non cabe, todas apiladas', async () => {
    const el = await render('b');
    expect(el.hasAttribute('data-stacked')).toBe(true);
  });
});
