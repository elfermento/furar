// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ApplicationRef, ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { ChecklistItem } from './checklist.component';
import { ChecklistComponent } from './checklist.component';

@Component({
  imports: [ChecklistComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-checklist heading="Para abrir as reservas" [items]="items" />`,
})
class HostComponent {
  items: ChecklistItem[] = ITEMS;
}

const ITEMS: ChecklistItem[] = [
  { key: 'a', label: 'Primeiro', done: true, action: { label: 'Facer', link: '/a' } },
  {
    key: 'b',
    label: 'Segundo',
    next: 'fai o segundo',
    done: false,
    action: { label: 'Crear', link: '/b', queryParams: { nova: 1 } },
  },
  { key: 'c', label: 'Terceiro', done: false },
];

@Component({
  imports: [ChecklistComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-checklist heading="Para abrir as reservas" [items]="items" compact />`,
})
class CompactHostComponent {
  items: ChecklistItem[] = ITEMS;
}

describe('furar-checklist', () => {
  async function render(host: typeof HostComponent | typeof CompactHostComponent = HostComponent) {
    TestBed.configureTestingModule({ providers: [...provideTestI18n(), provideRouter([])] });
    const fixture = TestBed.createComponent(host);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('rexión co título, contador, pasos en orde e ligazón só nos que faltan; axe', async () => {
    const el = await render();
    const region = el.querySelector('furar-checklist')!;
    expect(region.getAttribute('role')).toBe('region');
    expect(el.querySelector(`#${region.getAttribute('aria-labelledby')}`)?.textContent).toBe('Para abrir as reservas');
    expect(el.querySelector('[data-count]')?.textContent?.trim()).toBe('1 de 3');
    const items = [...el.querySelectorAll('li')];
    expect(items.map((li) => li.getAttribute('data-done'))).toEqual(['true', 'false', 'false']);
    // O feito vai tamén en texto, non só no visto.
    expect(items[0].querySelector('.f-checklist__label')?.textContent).toContain('(feito)');
    expect(items[0].querySelector('a')).toBeNull();
    expect(items[1].querySelector('a')?.getAttribute('href')).toBe('/b?nova=1');
    expect(items[2].querySelector('a')).toBeNull();
    expect(el.querySelector('.f-checklist__bar')?.getAttribute('aria-hidden')).toBe('true');
    await expectNoAxeViolations(el);
  });

  it('compacta: pregada, o seguinte paso con «paso N de M» e «Ir»; desprégase co título; axe', async () => {
    const el = await render(CompactHostComponent);
    const region = el.querySelector('furar-checklist')!;
    expect(region.hasAttribute('data-compact')).toBe(true);
    expect(el.querySelector(`#${region.getAttribute('aria-labelledby')}`)?.textContent).toBe('Para abrir as reservas');
    const toggle = el.querySelector<HTMLButtonElement>('button.f-checklist__toggle')!;
    const body = el.querySelector<HTMLElement>(`#${toggle.getAttribute('aria-controls')}`)!;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(body.hidden).toBe(true);
    expect(el.querySelector('[data-next]')?.textContent).toBe('fai o segundo');
    expect(el.querySelector('[data-count]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('paso 2 de 3');
    const go = el.querySelector<HTMLAnchorElement>('a[data-go]')!;
    expect(go.textContent?.trim()).toBe('Ir');
    expect(go.getAttribute('aria-label')).toBe('Ir: fai o segundo');
    expect(go.getAttribute('href')).toBe('/b?nova=1');
    await expectNoAxeViolations(el);

    toggle.click();
    await TestBed.inject(ApplicationRef).whenStable();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(region.hasAttribute('data-open')).toBe(true);
    expect(body.hidden).toBe(false);
    expect(el.querySelector('a[data-go]')).toBeNull();
    expect(el.querySelector('[data-next]')).toBeNull();
    expect(el.querySelector('[data-count]')?.textContent?.trim()).toBe('1 de 3');
    expect(body.querySelectorAll('li')).toHaveLength(3);
    expect(body.querySelector('[data-item="b"] a')?.getAttribute('href')).toBe('/b?nova=1');
    await expectNoAxeViolations(el);

    toggle.click();
    await TestBed.inject(ApplicationRef).whenStable();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(body.hidden).toBe(true);
  });
});
