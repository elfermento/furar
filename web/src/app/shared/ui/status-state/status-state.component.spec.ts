// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { IconComponent } from '../icon/icon.component';
import { StatusStateComponent } from './status-state.component';

@Component({
  imports: [StatusStateComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-status-state id="empty" description="Aínda non hai nada" />
    <furar-status-state id="loading" mode="loading" />
    <furar-status-state id="error" mode="error" heading="Non existe" [headingLevel]="1" description="Revisa">
      <a href="/">{{ 'Inicio' }}</a>
    </furar-status-state>
    <furar-status-state id="h3" heading="Sen datos" [headingLevel]="3" />
    <furar-icon id="named" name="success" label="Correcto" [size]="24" />
    <furar-status-state id="rows" mode="skeleton" />
    <furar-status-state id="cards" mode="skeleton" shape="card" [rows]="4" description="Cargando instalacións" />
  `,
})
class HostComponent {}

describe('furar-status-state e furar-icon', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('baleiro: icona e descrición, sen rol nin título', async () => {
    const el = await render();
    const empty = el.querySelector('#empty') as HTMLElement;
    expect(empty.dataset['mode']).toBe('empty');
    expect(empty.hasAttribute('role')).toBe(false);
    expect(empty.querySelector('h1, h2, h3')).toBeNull();
    expect(empty.textContent).toContain('Aínda non hai nada');
  });

  it('cargando: role="status" co texto común traducido', async () => {
    const el = await render();
    const loading = el.querySelector('#loading') as HTMLElement;
    expect(loading.getAttribute('role')).toBe('status');
    expect(loading.textContent).toContain('Cargando');
  });

  it('erro: título do nivel pedido, sempre a mesma icona e acción proxectada', async () => {
    const el = await render();
    const error = el.querySelector('#error') as HTMLElement;
    expect(error.querySelector('h1')?.textContent).toContain('Non existe');
    expect(error.querySelector('furar-icon path')).not.toBeNull();
    expect(error.querySelector('.f-status__action a')?.getAttribute('href')).toBe('/');
    expect(el.querySelector('#h3 h3')?.textContent).toContain('Sen datos');
  });

  it('esqueleto: role="status" co texto oculto e bloques decorativos coa forma pedida', async () => {
    const el = await render();
    const rows = el.querySelector('#rows') as HTMLElement;
    expect(rows.getAttribute('role')).toBe('status');
    expect(rows.dataset['shape']).toBe('row');
    expect(rows.querySelector('.cdk-visually-hidden')?.textContent).toContain('Cargando');
    expect(rows.querySelector('.f-status__badge')).toBeNull();
    expect(rows.querySelector('.f-status__skeleton')?.getAttribute('aria-hidden')).toBe('true');
    expect(rows.querySelectorAll('.f-status__bone')).toHaveLength(3);
    const cards = el.querySelector('#cards') as HTMLElement;
    expect(cards.dataset['shape']).toBe('card');
    expect(cards.querySelectorAll('.f-status__bone')).toHaveLength(4);
    expect(cards.querySelectorAll('[data-size="pill"]')).toHaveLength(4);
    expect(cards.textContent?.trim()).toBe('Cargando instalacións');
    expect((el.querySelector('#loading') as HTMLElement).dataset['shape']).toBeUndefined();
  });

  it('a icona é decorativa salvo con label (role="img" co nome); o conxunto pasa axe', async () => {
    const el = await render();
    expect(el.querySelector('#empty furar-icon')?.getAttribute('aria-hidden')).toBe('true');
    const named = el.querySelector('#named') as HTMLElement;
    expect(named.getAttribute('role')).toBe('img');
    expect(named.getAttribute('aria-label')).toBe('Correcto');
    expect(named.hasAttribute('aria-hidden')).toBe(false);
    expect(named.dataset['size']).toBe('24');
    await expectNoAxeViolations(el);
  });
});
