// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { SwitchComponent } from './switch.component';

@Component({
  imports: [ReactiveFormsModule, SwitchComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form">
      <p id="online-help">{{ 'A veciñanza reserva na web.' }}</p>
      <furar-switch id="online" formControlName="online" describedBy="online-help" [label]="'Reserva por internet'" />
      <furar-switch id="slots" formControlName="slots" hideLabel [label]="'Mostrar ocos'" />
      <furar-switch id="covered" formControlName="covered" [label]="'Cuberta'" />
      <furar-switch
        id="day"
        formControlName="day"
        [label]="'Aberto ese día'"
        [onText]="'Aberto'"
        [offText]="'Pechado'"
      />
    </form>
  `,
})
class HostComponent {
  readonly form = new FormGroup({
    online: new FormControl(true, { nonNullable: true }),
    slots: new FormControl(false, { nonNullable: true }),
    covered: new FormControl({ value: true, disabled: true }, { nonNullable: true }),
    day: new FormControl(false, { nonNullable: true }),
  });
}

describe('furar-switch', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
  });

  const host = (id: string) => (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(`#${id}`)!;
  const control = (id: string) => host(id).querySelector<HTMLButtonElement>('button')!;
  const form = () => fixture.componentInstance.form;

  it('é un botón role="switch" con aria-checked, nomeado polo rótulo visible', async () => {
    const button = control('online');
    expect(button.getAttribute('role')).toBe('switch');
    expect(button.getAttribute('type')).toBe('button');
    expect(button.getAttribute('aria-checked')).toBe('true');
    expect(button.getAttribute('aria-describedby')).toBe('online-help');
    const label = host('online').querySelector('label')!;
    expect(label.htmlFor).toBe(button.id);
    expect(label.textContent?.trim()).toBe('Reserva por internet');
    expect(label.classList.contains('sr-only')).toBe(false);
    expect(host('slots').querySelector('label')!.classList.contains('sr-only')).toBe(true);
    expect(control('slots').getAttribute('aria-checked')).toBe('false');
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
  });

  it('picar no botón ou no rótulo cambia o valor do formulario e marca tocado', async () => {
    control('online').click();
    await fixture.whenStable();
    expect(form().controls.online.value).toBe(false);
    expect(form().controls.online.touched).toBe(true);
    expect(control('online').getAttribute('aria-checked')).toBe('false');

    host('slots').querySelector('label')!.click();
    await fixture.whenStable();
    expect(form().controls.slots.value).toBe(true);
  });

  it('con onText e offText, á vista o estado e o rótulo como nome accesible (maqueta 54)', async () => {
    const button = control('day');
    const state = () => host('day').querySelector<HTMLLabelElement>('[data-switch-state]')!;
    expect(button.getAttribute('aria-label')).toBe('Aberto ese día');
    expect(state().textContent?.trim()).toBe('Pechado');
    expect(state().getAttribute('aria-hidden')).toBe('true');
    expect(host('day').textContent).not.toContain('Aberto ese día');
    state().click();
    await fixture.whenStable();
    expect(form().controls.day.value).toBe(true);
    expect(state().textContent?.trim()).toBe('Aberto');
    expect(button.getAttribute('aria-checked')).toBe('true');
    expect(control('online').hasAttribute('aria-label')).toBe(false);
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
  });

  it('o valor que pon o formulario vese no interruptor', async () => {
    form().controls.online.setValue(false);
    await fixture.whenStable();
    expect(control('online').getAttribute('aria-checked')).toBe('false');
  });

  it('desactivado polo formulario: botón disabled e non cambia', async () => {
    const button = control('covered');
    expect(button.disabled).toBe(true);
    expect(host('covered').hasAttribute('data-disabled')).toBe(true);
    host('covered').querySelector('label')!.click();
    await fixture.whenStable();
    expect(form().controls.covered.value).toBe(true);
    form().controls.covered.enable();
    await fixture.whenStable();
    expect(button.disabled).toBe(false);
  });

  it('perder o foco marca tocado', async () => {
    const button = control('slots');
    button.focus();
    button.blur();
    expect(form().controls.slots.touched).toBe(true);
  });
});
