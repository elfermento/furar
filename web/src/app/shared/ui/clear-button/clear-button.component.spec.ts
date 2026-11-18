// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { ClearButtonComponent } from './clear-button.component';

@Component({
  imports: [ClearButtonComponent],
  template: `<furar-clear-button label="Quitar a data" (pressed)="count.set(count() + 1)" />`,
})
class HostComponent {
  readonly count = signal(0);
}

describe('ClearButtonComponent', () => {
  it('é un botón co ✕ e o nome accesible; avisa ao pulsalo; pasa axe', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.type).toBe('button');
    expect(button.classList.contains('f-clear-button')).toBe(true);
    expect(button.getAttribute('aria-label')).toBe('Quitar a data');
    expect(button.querySelector('furar-icon[name="close"]')?.getAttribute('aria-hidden')).toBe('true');
    button.click();
    expect(fixture.componentInstance.count()).toBe(1);
    await expectNoAxeViolations(fixture.nativeElement);
  });
});
