// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { ButtonDirective } from './button.directive';
import { InputDirective } from '../input/input.directive';
import { FieldsetDirective } from '../card/fieldset.directive';
import { SegmentedDirective } from '../segmented/segmented.directive';

@Component({
  imports: [ButtonDirective, InputDirective, FieldsetDirective, SegmentedDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button id="b1" type="button" furarButton="primary" [busy]="busy()" [disabled]="busy()">{{ 'x' }}</button>
    <button id="b2" type="button" furarButton size="lg" block>{{ 'y' }}</button>
    <a id="b3" href="/" furarButton="danger">{{ 'z' }}</a>
    <label for="i1">{{ 'n' }}</label>
    <input id="i1" furarInput />
    <fieldset furarFieldset>
      <legend>{{ 'g' }}</legend>
    </fieldset>
    <div role="group" aria-label="l" furarSegmented="primary"></div>
    <div id="s2" role="group" aria-label="m" furarSegmented></div>
  `,
})
class HostComponent {
  readonly busy = signal(false);
}

describe('directivas de aspecto (button, input, fieldset, segmented)', () => {
  async function render() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('[furarButton] pon clase, variante, tamaño e ancho; secondary por defecto', async () => {
    const { el } = await render();
    const [primary, secondary, danger] = ['#b1', '#b2', '#b3'].map((id) => el.querySelector(id) as HTMLElement);
    expect(primary?.classList).toContain('f-button');
    expect(primary?.dataset['variant']).toBe('primary');
    expect(primary?.dataset['size']).toBe('md');
    expect(secondary?.dataset['variant']).toBe('secondary');
    expect(secondary?.dataset['size']).toBe('lg');
    expect(secondary?.dataset['block']).toBe('true');
    expect(danger?.dataset['variant']).toBe('danger');
    expect(primary?.hasAttribute('data-block')).toBe(false);
  });

  it('busy marca aria-busy (o rótulo pono quen chama)', async () => {
    const { fixture, el } = await render();
    const button = el.querySelector('#b1') as HTMLButtonElement;
    expect(button.hasAttribute('aria-busy')).toBe(false);
    fixture.componentInstance.busy.set(true);
    await fixture.whenStable();
    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.disabled).toBe(true);
  });

  it('input, fieldset e segmented só poñen as súas clases; o conxunto pasa axe', async () => {
    const { el } = await render();
    expect(el.querySelector('input')?.classList).toContain('f-input');
    expect(el.querySelector('fieldset')?.classList).toContain('f-fieldset');
    expect(el.querySelector('[furarSegmented="primary"]')?.getAttribute('data-surface')).toBe('primary');
    expect(el.querySelector('#s2')?.getAttribute('data-surface')).toBe('surface');
    await expectNoAxeViolations(el);
  });
});
