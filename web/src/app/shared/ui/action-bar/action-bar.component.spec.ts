// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { ActionBarComponent } from './action-bar.component';

@Component({
  imports: [ActionBarComponent],
  template: `
    <furar-action-bar label="Edición da instalación">
      <p actionBarStatus>Cambios sen gardar</p>
      <button type="button">Cancelar</button>
      <button type="button">Gardar</button>
    </furar-action-bar>
  `,
})
class HostComponent {}

describe('ActionBarComponent', () => {
  it('é unha rexión con nome, co estado á esquerda e os botóns ao final, en cristal', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const region = el.querySelector('[role="region"]')!;
    expect(region.getAttribute('aria-label')).toBe('Edición da instalación');
    expect(region.classList).toContain('f-glass');
    expect(region.querySelector('.f-action-bar__status')?.textContent).toContain('Cambios sen gardar');
    expect([...region.querySelectorAll('.f-action-bar__actions button')].map((b) => b.textContent)).toEqual([
      'Cancelar',
      'Gardar',
    ]);
    await expectNoAxeViolations(el);
  });
});
