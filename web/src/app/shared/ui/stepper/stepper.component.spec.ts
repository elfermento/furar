// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { StepperStep } from './stepper.component';
import { StepperComponent } from './stepper.component';

const STEPS: StepperStep[] = [
  { label: 'Nome', optional: false, state: 'done', reachable: true },
  { label: 'Contacto', optional: true, state: 'todo', reachable: true },
  { label: 'Usos', optional: false, state: 'current', reachable: true },
  { label: 'Revisar', optional: false, state: 'pending', reachable: false },
];

@Component({
  imports: [StepperComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-stepper [steps]="steps" [current]="2" [label]="'Pasos'" (go)="picked.set($event)" />`,
})
class HostComponent {
  readonly steps = STEPS;
  readonly picked = signal<number | null>(null);
}

const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

describe('furar-stepper', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const buttons = [...el.querySelectorAll<HTMLButtonElement>('nav button')];
    return { fixture, el, buttons };
  }

  it('lista numerada con nome; o actual con aria-current e o estado tamén en texto', async () => {
    const { el, buttons } = await render();
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe('Pasos');
    expect(el.querySelectorAll('nav ol > li')).toHaveLength(4);
    expect(buttons[2].getAttribute('aria-current')).toBe('step');
    expect(text(buttons[0])).toBe('Nome, feito');
    expect(text(buttons[1])).toBe('! Contacto, Opcional, falta algo');
    expect(text(buttons[3])).toBe('4 Revisar');
    expect(buttons[3].disabled).toBe(true);
    const bar = el.querySelector('[role="progressbar"]');
    expect(bar?.getAttribute('aria-valuenow')).toBe('3');
    expect(bar?.getAttribute('aria-valuetext')).toBe('Paso 3 de 4: Usos');
    await expectNoAxeViolations(el);
  });

  it('escoller un paso visitado avisa; o actual e os que faltan non', async () => {
    const { fixture, buttons } = await render();
    buttons[0].click();
    expect(fixture.componentInstance.picked()).toBe(0);
    fixture.componentInstance.picked.set(null);
    buttons[2].click();
    buttons[3].click();
    expect(fixture.componentInstance.picked()).toBeNull();
  });
});
