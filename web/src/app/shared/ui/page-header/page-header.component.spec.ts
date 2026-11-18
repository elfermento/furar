// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { PageHeaderComponent } from './page-header.component';

@Component({
  imports: [PageHeaderComponent],
  template: `
    <furar-page-header heading="Pista de pádel 1" intro="Rúa Buxo, 16 · 15572 Narón" introIcon="location">
      <span pageHeadingAside data-aside>Activa</span>
      <button pageActions type="button">Editar</button>
    </furar-page-header>
  `,
})
class HostComponent {}

describe('PageHeaderComponent', () => {
  it('un h1 co que vai na súa liña, a introdución coa icona decorativa e as accións', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('h1')).toHaveLength(1);
    expect(el.querySelector('.f-page-header__row [data-aside]')).not.toBeNull();
    const intro = el.querySelector('.f-page-header__intro')!;
    expect(intro.textContent?.trim()).toBe('Rúa Buxo, 16 · 15572 Narón');
    expect(intro.querySelector('furar-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelector('.f-page-header__actions button')?.textContent).toBe('Editar');
    await expectNoAxeViolations(el);
  });
});
