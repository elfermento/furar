// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { DisclosureComponent } from './disclosure.component';

@Component({
  imports: [DisclosureComponent],
  template: `
    <furar-disclosure heading="Detalles"><p>Un</p></furar-disclosure>
    <furar-disclosure heading="Columnas"><p>Dous</p></furar-disclosure>
  `,
})
class HostComponent {}

describe('furar-disclosure', () => {
  it('pechado por defecto; cada un ábrese só co seu botón e está ligado ao panel; axe', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const buttons = [...el.querySelectorAll<HTMLButtonElement>('.f-disclosure__toggle')];
    const panels = [...el.querySelectorAll<HTMLElement>('.f-disclosure__panel')];
    expect(buttons.map((b) => b.getAttribute('aria-expanded'))).toEqual(['false', 'false']);
    expect(panels.every((p) => p.hidden)).toBe(true);
    expect(buttons[0].getAttribute('aria-controls')).toBe(panels[0].id);

    buttons[0].click();
    fixture.detectChanges();
    expect(buttons[0].getAttribute('aria-expanded')).toBe('true');
    expect(panels[0].hidden).toBe(false);
    expect(panels[1].hidden).toBe(true);
    await expectNoAxeViolations(el);

    buttons[0].click();
    fixture.detectChanges();
    expect(panels[0].hidden).toBe(true);
  });
});
