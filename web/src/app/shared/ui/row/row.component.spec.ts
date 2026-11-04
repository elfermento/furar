// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { RowListDirective } from './row-list.directive';
import { RowComponent } from './row.component';

@Component({
  imports: [RowListDirective, RowComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul furarRowList aria-label="Concellos">
      <li><furar-row label="Narón" [link]="['/', 'naron']" /></li>
      <li>
        <furar-row label="Teléfono" icon="info">
          <span class="font-mono">{{ 'phone' }}</span>
          <span rowValue>{{ '3' }}</span>
          <button rowActions type="button">{{ 'Editar' }}</button>
        </furar-row>
      </li>
    </ul>
  `,
})
class HostComponent {}

describe('ul[furarRowList] e furar-row', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('lista nativa (ul > li) co nome de quen chama', async () => {
    const el = await render();
    const list = el.querySelector('ul') as HTMLElement;
    expect(list.classList).toContain('f-row-list');
    expect(list.getAttribute('aria-label')).toBe('Concellos');
    expect(list.querySelectorAll(':scope > li')).toHaveLength(2);
  });

  it('con link: a fila enteira é unha ligazón (estirada) co rótulo como nome e frecha decorativa', async () => {
    const el = await render();
    const first = el.querySelector('li furar-row') as HTMLElement;
    const link = first.querySelector('a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/naron');
    expect(link.textContent?.trim()).toBe('Narón');
    expect(link.classList).toContain('f-row__link');
    expect(first.querySelector('.f-row__chevron')?.getAttribute('aria-hidden')).toBe('true');
    expect(first.querySelectorAll('button')).toHaveLength(0);
  });

  it('con accións: rótulo en texto, detalle, valor e botóns ao final, sen ligazón; pasa axe', async () => {
    const el = await render();
    const second = el.querySelectorAll('li furar-row')[1] as HTMLElement;
    expect(second.querySelector('a')).toBeNull();
    expect(second.querySelector('.f-row__label')?.textContent).toContain('Teléfono');
    expect(second.querySelector('.f-row__detail')?.textContent).toContain('phone');
    expect(second.querySelector('.f-row__value')?.textContent).toContain('3');
    expect(second.querySelector('.f-row__actions button')?.textContent).toContain('Editar');
    expect(second.querySelector('.f-row__icon')).not.toBeNull();
    await expectNoAxeViolations(el);
  });
});
