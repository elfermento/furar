// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { DeleteButtonComponent } from './delete-button.component';

@Component({
  imports: [DeleteButtonComponent],
  template: `
    <furar-delete-button label="Eliminar a foto 2" (pressed)="count.set(count() + 1)" />
    <furar-delete-button label="Eliminar esta foto" text="Eliminar" [disabled]="off()" />
  `,
})
class HostComponent {
  readonly count = signal(0);
  readonly off = signal(false);
}

describe('DeleteButtonComponent', () => {
  it('é un botón danger coa papeleira e o nome accesible, redondo sen texto', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const [round, labelled] = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    expect(round.getAttribute('aria-label')).toBe('Eliminar a foto 2');
    expect(round.dataset['variant']).toBe('danger');
    expect(round.dataset['round']).toBe('true');
    expect(round.querySelector('furar-icon')).not.toBeNull();
    expect(labelled.dataset['round']).toBeUndefined();
    expect(labelled.textContent?.trim()).toBe('Eliminar');
    expect(labelled.getAttribute('aria-label')).toMatch(/^Eliminar/);
    round.click();
    expect(fixture.componentInstance.count()).toBe(1);
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('desactivado non emite', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.off.set(true);
    fixture.detectChanges();
    const labelled = fixture.nativeElement.querySelectorAll('button')[1] as HTMLButtonElement;
    expect(labelled.disabled).toBe(true);
  });
});
