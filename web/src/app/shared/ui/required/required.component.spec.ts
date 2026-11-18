// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../../testing/i18n';
import { RequiredMarkComponent } from './required.component';

describe('RequiredMarkComponent', () => {
  it('pinta o «*» oculto ao lector e di «(obrigatorio)» só para el', async () => {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(RequiredMarkComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.f-required')?.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelector('.f-required')?.textContent).toBe('*');
    expect(el.querySelector('.sr-only')?.textContent?.trim()).toBe('(obrigatorio)');
  });
});
