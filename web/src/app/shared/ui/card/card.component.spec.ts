// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { PageHeaderComponent } from '../page-header/page-header.component';
import { CardComponent } from './card.component';

@Component({
  imports: [CardComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-page-header heading="Pantalla" intro="Introdución">
      <button pageActions type="button">{{ 'acción' }}</button>
    </furar-page-header>
    <furar-card id="c1" heading="Datos">{{ 'contido' }}</furar-card>
    <furar-card id="c2" heading="Subgrupo" [headingLevel]="3" tone="tint" />
    <furar-card id="c3">{{ 'sen título' }}</furar-card>
    <furar-page-header id="p2" heading="Portada" size="display" />
  `,
})
class HostComponent {}

describe('furar-card e furar-page-header', () => {
  it('a card con título é unha rexión co nome do título; sen título, só caixa', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const c1 = el.querySelector('#c1') as HTMLElement;
    const heading = c1.querySelector('h2') as HTMLElement;
    expect(c1.getAttribute('role')).toBe('region');
    expect(c1.getAttribute('aria-labelledby')).toBe(heading.id);
    expect(heading.textContent?.trim()).toBe('Datos');
    const c2 = el.querySelector('#c2') as HTMLElement;
    expect(c2.querySelector('h3')?.textContent).toContain('Subgrupo');
    expect(c2.dataset['tone']).toBe('tint');
    expect(c2.getAttribute('aria-labelledby')).not.toBe(heading.id);
    const c3 = el.querySelector('#c3') as HTMLElement;
    expect(c3.hasAttribute('role')).toBe(false);
    expect(c3.querySelector('h2, h3')).toBeNull();
    expect(c3.classList).toContain('f-card');
  });

  it('a cabeceira ten o h1, a introdución e as accións proxectadas; pasa axe', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const header = el.querySelector('furar-page-header') as HTMLElement;
    expect(header.querySelector('h1')?.textContent).toContain('Pantalla');
    expect(header.querySelector('p')?.textContent).toContain('Introdución');
    expect(header.querySelector('.f-page-header__actions button')).not.toBeNull();
    const display = el.querySelector('#p2') as HTMLElement;
    expect(display.dataset['size']).toBe('display');
    expect(display.querySelector('p')).toBeNull();
    // Dous h1 só neste host de proba: axe non o marca (page-has-heading-one é boa práctica).
    await expectNoAxeViolations(el);
  });
});
