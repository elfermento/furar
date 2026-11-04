// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { ValueListComponent } from './value-list.component';

describe('furar-value-list', () => {
  it('pinta unha fila dt/dd por dato, coa clave en data-value; axe', async () => {
    const fixture = TestBed.createComponent(ValueListComponent);
    fixture.componentRef.setInput('items', [
      { id: 'day', label: 'Día', value: 'martes, 27 de outubro' },
      { id: 'time', label: 'Hora', value: '18:00–19:00' },
    ]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.classList).toContain('f-value-list');
    const rows = [...el.querySelectorAll('dl > div[data-value]')].map((row) => [
      row.getAttribute('data-value'),
      row.querySelector('dt')?.textContent?.trim(),
      row.querySelector('dd')?.textContent?.trim(),
    ]);
    expect(rows).toEqual([
      ['day', 'Día', 'martes, 27 de outubro'],
      ['time', 'Hora', '18:00–19:00'],
    ]);
    await expectNoAxeViolations(el);
  });
});
