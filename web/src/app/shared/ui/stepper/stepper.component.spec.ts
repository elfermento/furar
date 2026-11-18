// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { StepperLayout, StepperStep } from './stepper.component';
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
  template: `<button type="button" id="outside">Fóra</button>
    <furar-stepper [steps]="steps" [current]="2" [label]="'Pasos'" [layout]="layout()" (go)="picked.set($event)" />`,
})
class HostComponent {
  readonly steps = STEPS;
  readonly layout = signal<StepperLayout>('horizontal');
  readonly picked = signal<number | null>(null);
}

const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

describe('furar-stepper', () => {
  async function render(layout: StepperLayout = 'horizontal') {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.layout.set(layout);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const buttons = [...el.querySelectorAll<HTMLButtonElement>('nav ol button')];
    const toggle = el.querySelector<HTMLButtonElement>('[data-stepper-toggle]');
    const list = el.querySelector('nav ol')!;
    return { fixture, el, buttons, toggle, list };
  }

  it('lista numerada con nome; o actual con aria-current e o estado tamén en texto', async () => {
    const { el, buttons, toggle } = await render();
    expect(el.querySelector('furar-stepper')?.getAttribute('data-layout')).toBe('horizontal');
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe('Pasos');
    expect(el.querySelectorAll('nav ol > li')).toHaveLength(4);
    expect(buttons[2].getAttribute('aria-current')).toBe('step');
    expect(text(buttons[0])).toBe('Nome, feito');
    expect(text(buttons[1])).toBe('! Contacto, Opcional, falta algo');
    expect(text(buttons[3])).toBe('4 Revisar');
    expect(buttons[3].disabled).toBe(true);
    expect(toggle).toBeNull();
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

  describe('vertical', () => {
    it('os mesmos pasos e estados; no estreito, «Paso N de M» co nome, sen barra', async () => {
      const { el, buttons, toggle, list } = await render('vertical');
      expect(el.querySelector('furar-stepper')?.getAttribute('data-layout')).toBe('vertical');
      expect(buttons).toHaveLength(4);
      expect(buttons[2].getAttribute('aria-current')).toBe('step');
      expect(text(buttons[1])).toBe('! Contacto, Opcional, falta algo');
      expect(el.querySelector('[role="progressbar"]')).toBeNull();
      expect(toggle?.closest('nav')).toBe(el.querySelector('nav'));
      expect(text(toggle)).toBe('Paso 3 de 4 · Usos');
      expect(toggle?.getAttribute('aria-expanded')).toBe('false');
      expect(toggle?.getAttribute('aria-controls')).toBe(list.id);
      expect(list.hasAttribute('data-open')).toBe(false);
      await expectNoAxeViolations(el);
    });

    it('o botón abre a lista; escoller un paso feito vai a el e pecha', async () => {
      const { fixture, buttons, toggle, list } = await render('vertical');
      toggle!.click();
      await fixture.whenStable();
      expect(toggle!.getAttribute('aria-expanded')).toBe('true');
      expect(list.hasAttribute('data-open')).toBe(true);
      buttons[0].click();
      await fixture.whenStable();
      expect(fixture.componentInstance.picked()).toBe(0);
      expect(list.hasAttribute('data-open')).toBe(false);
    });

    it('pechase ao picar fóra e con Esc (o foco volve ao botón)', async () => {
      const { fixture, el, buttons, toggle, list } = await render('vertical');
      toggle!.click();
      await fixture.whenStable();
      expect(list.hasAttribute('data-open')).toBe(true);
      el.querySelector<HTMLButtonElement>('#outside')!.click();
      await fixture.whenStable();
      expect(list.hasAttribute('data-open')).toBe(false);

      toggle!.click();
      await fixture.whenStable();
      buttons[0].focus();
      buttons[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await fixture.whenStable();
      expect(list.hasAttribute('data-open')).toBe(false);
      expect(document.activeElement).toBe(toggle);
    });
  });
});
