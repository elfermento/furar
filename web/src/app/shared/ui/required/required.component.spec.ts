// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../../testing/i18n';
import { RequiredNoteComponent } from './required.component';

describe('RequiredNoteComponent', () => {
  it('di «* Campo obrigatorio» co «*» oculto ao lector', async () => {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(RequiredNoteComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.classList).toContain('f-required-note');
    expect(el.textContent?.replace(/\s+/g, ' ').trim()).toBe('* Campo obrigatorio');
    expect(el.querySelector('.f-required')?.getAttribute('aria-hidden')).toBe('true');
  });
});
