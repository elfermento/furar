// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { BilingualFieldComponent } from './bilingual-field.component';

@Component({
  imports: [BilingualFieldComponent],
  template: ` <furar-bilingual-field fieldId="name" label="Nome" help="Nos dous idiomas." [gl]="gl" [es]="es" /> `,
})
class HostComponent {
  readonly gl = new FormControl('Pista', { nonNullable: true, validators: Validators.required });
  readonly es = new FormControl('', { nonNullable: true, validators: Validators.required });
}

describe('BilingualFieldComponent', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const input = () => el.querySelector<HTMLInputElement>('#name')!;
    const lang = (code: string) => el.querySelector<HTMLButtonElement>(`button[data-lang="${code}"]`)!;
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    return { fixture, el, input, lang, settle };
  }

  it('un só campo co rótulo, a axuda e o conmutador GL/ES; edita o idioma escollido', async () => {
    const { fixture, el, input, lang, settle } = await render();
    expect(el.querySelector('label[for="name"]')?.textContent).toContain('Nome');
    expect(input().value).toBe('Pista');
    expect(input().getAttribute('lang')).toBe('gl');
    expect(input().getAttribute('aria-describedby')).toBe('name-help');
    expect(lang('gl').getAttribute('aria-pressed')).toBe('true');
    expect(el.querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Idioma de «Nome»');
    await expectNoAxeViolations(el);
    lang('es').click();
    await settle();
    expect(input().getAttribute('lang')).toBe('es');
    input().value = 'Pista ES';
    input().dispatchEvent(new Event('input'));
    await settle();
    expect(fixture.componentInstance.es.value).toBe('Pista ES');
    expect(fixture.componentInstance.gl.value).toBe('Pista');
  });

  it('o erro do outro idioma pasa a ese idioma e dío no texto', async () => {
    const { fixture, el, input, settle } = await render();
    fixture.componentInstance.es.markAsTouched();
    fixture.componentInstance.es.updateValueAndValidity();
    await settle();
    expect(input().getAttribute('lang')).toBe('es');
    expect(input().getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector('#name-error')?.textContent).toContain('Castelán:');
    expect(input().getAttribute('aria-describedby')).toBe('name-help name-error');
  });
});
