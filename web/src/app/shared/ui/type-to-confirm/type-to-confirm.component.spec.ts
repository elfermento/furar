// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { TypeToConfirmComponent, normalizePhrase, phraseMatches } from './type-to-confirm.component';

@Component({
  imports: [TypeToConfirmComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p id="what">Deixa de entrar na xestión.</p>
    <furar-type-to-confirm
      phrase="conserxe"
      describedBy="what"
      [(matched)]="matched"
      (entered)="entered = entered + 1"
    />
  `,
})
class HostComponent {
  readonly matched = signal(false);
  entered = 0;
}

describe('furar-type-to-confirm', () => {
  it('normaliza sen maiúsculas, tildes nin espazos de máis', () => {
    expect(normalizePhrase('  Eliminar   INSTALACIÓN ')).toBe('eliminar instalacion');
    expect(phraseMatches('CONSERXÉ', 'conserxe')).toBe(true);
    expect(phraseMatches('conserje', 'conserxe')).toBe(false);
    expect(phraseMatches('conserx', 'conserxe')).toBe(false);
    // Unha frase baleira nunca coincide.
    expect(phraseMatches('', '  ')).toBe(false);
  });

  it('campo co rótulo visible, a descrición e matched ao coincidir; Intro emite entered; axe', async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector<HTMLInputElement>('[data-confirm-phrase]')!;
    const label = el.querySelector<HTMLLabelElement>(`label[for="${input.id}"]`)!;
    expect(label.textContent?.trim()).toBe('Para confirmar, escribe «conserxe»');
    expect(label.id).toBe(`${input.id}-label`);
    expect(input.getAttribute('aria-describedby')).toBe('what');
    expect(input.getAttribute('autocomplete')).toBe('off');
    expect(input.getAttribute('spellcheck')).toBe('false');

    input.value = 'Conserxe';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.componentInstance.matched()).toBe(true);
    input.value = 'conserx';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.componentInstance.matched()).toBe(false);

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(fixture.componentInstance.entered).toBe(1);
    await expectNoAxeViolations(el);
  });

  it('cada campo ten o seu id', async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const first = TestBed.createComponent(HostComponent);
    const second = TestBed.createComponent(HostComponent);
    await first.whenStable();
    await second.whenStable();
    const id = (f: typeof first) => (f.nativeElement as HTMLElement).querySelector('input')!.id;
    expect(id(first)).not.toBe(id(second));
  });
});
