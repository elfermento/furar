// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterLink, provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { ActionItemComponent, ActionListDirective } from './action-list.component';

@Component({
  imports: [ActionItemComponent, ActionListDirective, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul furarActionList aria-label="Accións">
      <li>
        <furar-action-item icon="edit-calendar">
          <a [routerLink]="['/', 'naron', 'reservar']">{{ 'Cambiar' }}</a>
        </furar-action-item>
      </li>
      <li>
        <furar-action-item
          ><button type="button">{{ 'Sen icona' }}</button></furar-action-item
        >
      </li>
      <li>
        <furar-action-item icon="event-busy" tone="danger">
          <button type="button">{{ 'Cancelar' }}</button>
        </furar-action-item>
      </li>
    </ul>
  `,
})
class HostComponent {}

describe('ul[furarActionList] e furar-action-item', () => {
  it('unha acción por fila coa icona, o rótulo e o chevron; a destrutiva en vermello; axe', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const list = el.querySelector('ul')!;
    expect(list.classList).toContain('f-action-list');
    expect(list.getAttribute('aria-label')).toBe('Accións');
    const items = [...list.querySelectorAll<HTMLElement>('.f-action-item')];
    expect(items.map((item) => item.querySelector('.f-action-item__label')?.textContent?.trim())).toEqual([
      'Cambiar',
      'Sen icona',
      'Cancelar',
    ]);
    expect(items[0].querySelector('.f-action-item__label > a')?.getAttribute('href')).toBe('/naron/reservar');
    expect(items[1].querySelector('.f-action-item__label > button')).not.toBeNull();
    expect(items[0].querySelectorAll('furar-icon')).toHaveLength(2);
    expect(items[1].querySelectorAll('furar-icon')).toHaveLength(1);
    expect(items[1].hasAttribute('data-tone')).toBe(false);
    expect(items[2].getAttribute('data-tone')).toBe('danger');
    for (const item of items) expect(item.lastElementChild?.classList).toContain('f-action-item__chevron');
    await expectNoAxeViolations(el);
  });
});
