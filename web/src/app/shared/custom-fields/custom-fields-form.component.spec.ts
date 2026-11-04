// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal, viewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SELECT_OPTIONS, definition } from '../../../testing/fixtures';
import { provideTestI18n } from '../../../testing/i18n';
import { pickOption, selectText } from '../../../testing/select';
import { CustomFieldsFormComponent } from './custom-fields-form.component';
import { buildCustomFieldsForm } from './custom-field-controls';
import type { CustomFieldDefinition, CustomFieldType } from './custom-field.types';

const DEFS: CustomFieldDefinition[] = [
  definition({ code: 'phone', required: true, rules: { format: 'PHONE' } }),
  definition({ code: 'notes', type: 'LONG_TEXT', help: undefined }),
  definition({ code: 'courts', type: 'NUMBER', rules: { min: 0, decimals: 0 } }),
  definition({ code: 'opened', type: 'DATE' }),
  definition({
    code: 'defib',
    type: 'BOOLEAN',
    required: true,
    label: { gl: 'Desfibrilador', es: 'Desfibrilador' },
  }),
  definition({ code: 'main', type: 'SELECT', options: SELECT_OPTIONS }),
  definition({ code: 'sports', type: 'MULTI_SELECT', options: SELECT_OPTIONS }),
];

@Component({
  imports: [CustomFieldsFormComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-custom-fields-form [definitions]="defs" [form]="form" [lang]="lang()" [showErrors]="show()" />`,
})
class HostComponent {
  readonly defs = DEFS;
  readonly form = buildCustomFieldsForm(DEFS, { main: 'old' });
  readonly lang = signal('gl');
  readonly show = signal(false);
  readonly component = viewChild.required(CustomFieldsFormComponent);
}

describe('CustomFieldsFormComponent', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return { fixture, host: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  it('pinta cada tipo co control axeitado e etiqueta visible ligada', async () => {
    const { el } = await render();
    expect(el.querySelector('label[for="cf-phone"]')?.textContent).toContain('Teléfono');
    expect(el.querySelector('label[for="cf-phone"]')?.textContent).toContain('obrigatorio');
    expect(el.querySelector('#cf-phone')?.getAttribute('type')).toBe('tel');
    expect(el.querySelector('#cf-phone')?.getAttribute('aria-describedby')).toBe('cf-phone-help');
    expect(el.querySelector('textarea#cf-notes')).not.toBeNull();
    expect(el.querySelector('#cf-courts')?.getAttribute('type')).toBe('number');
    expect(el.querySelector('#cf-courts')?.getAttribute('step')).toBe('1');
    expect(el.querySelector('#cf-opened')?.getAttribute('type')).toBe('date');
    expect(el.querySelectorAll('input[type="radio"][name="cf-defib"]')).toHaveLength(2);
    expect(el.querySelector('furar-select button#cf-main')).not.toBeNull();
    expect(el.querySelectorAll('input[type="checkbox"]')).toHaveLength(2);
  });

  it('sen grupos nin orde manual: os campos na orde da API', async () => {
    const { el } = await render();
    const ids = [...el.querySelectorAll('furar-custom-field [id^="cf-"]')].map((e) => e.id);
    expect(ids.indexOf('cf-phone')).toBeLessThan(ids.indexOf('cf-notes'));
    expect(ids.indexOf('cf-notes')).toBeLessThan(ids.indexOf('cf-sports'));
  });

  it('a opción desactivada só aparece se xa estaba escollida', async () => {
    const { fixture, el } = await render();
    expect(selectText(el.querySelector('#cf-main')!)).toBe('Vella');
    el.querySelector<HTMLButtonElement>('#cf-main')!.click();
    await fixture.whenStable();
    const options = [...document.querySelectorAll('.f-select__option')].map((o) => o.textContent?.trim());
    expect(options).toEqual(['Escolle unha opción', 'Piscina', 'Ximnasio', 'Vella']);
    el.querySelector<HTMLButtonElement>('#cf-main')!.click();
    await fixture.whenStable();
    expect(
      [...el.querySelectorAll('input[type="checkbox"]')].map((c) => c.closest('label')?.textContent?.trim()),
    ).toEqual(['Piscina', 'Ximnasio']);
  });

  it('ao enviar amosa os erros ligados con aria-describedby e leva o foco ao primeiro', async () => {
    const { fixture, host, el } = await render();
    host.show.set(true);
    await fixture.whenStable();
    const phone = el.querySelector<HTMLInputElement>('#cf-phone');
    expect(phone?.getAttribute('aria-invalid')).toBe('true');
    expect(phone?.getAttribute('aria-describedby')).toBe('cf-phone-help cf-phone-error');
    expect(el.querySelector('#cf-phone-error')?.textContent).toContain('Este campo é obrigatorio.');
    expect(host.component().focusFirstInvalid()).toBe(true);
    // Orde do documento (a da API): «Teléfono» é o primeiro obrigatorio.
    expect(document.activeElement?.id).toBe('cf-phone');
  });

  it('o erro desaparece ao escribir un valor válido', async () => {
    const { fixture, host, el } = await render();
    host.show.set(true);
    await fixture.whenStable();
    expect(el.querySelector('#cf-phone-error')).not.toBeNull();
    const phone = el.querySelector<HTMLInputElement>('#cf-phone');
    if (!phone) throw new Error('sen #cf-phone');
    phone.value = '981 000 000';
    phone.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(host.form.controls['phone']?.valid).toBe(true);
    expect(el.querySelector('#cf-phone-error')).toBeNull();
    expect(phone.getAttribute('aria-invalid')).toBeNull();
  });

  it('marcar e desmarcar opcións múltiples actualiza o valor', async () => {
    const { fixture, host, el } = await render();
    const boxes = el.querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
    boxes[1]?.click();
    boxes[0]?.click();
    await fixture.whenStable();
    expect(host.form.controls['sports']?.value).toEqual(['gym', 'pool']);
    boxes[1]?.click();
    expect(host.form.controls['sports']?.value).toEqual(['pool']);
    expect(host.form.controls['sports']?.touched).toBe(true);
  });

  it('a lista escóllese no despregable e «Escolle unha opción» déixaa baleira', async () => {
    const { fixture, host, el } = await render();
    const main = el.querySelector<HTMLButtonElement>('#cf-main')!;
    await pickOption(main, 'Piscina', () => fixture.whenStable());
    await fixture.whenStable();
    expect(host.form.controls['main']?.value).toBe('pool');
    expect(document.activeElement).toBe(main);
    await pickOption(main, 'Escolle unha opción', () => fixture.whenStable());
    await fixture.whenStable();
    expect(host.form.controls['main']?.value).toBeNull();
  });

  it('cambia de idioma as etiquetas', async () => {
    const { fixture, host, el } = await render();
    host.lang.set('es');
    await fixture.whenStable();
    expect(el.textContent).toContain('Gimnasio');
  });
});

// QA P7 (D26): cada FieldType anuncia axuda e erro ao lector de pantalla, non só o texto.
const TYPES: CustomFieldType[] = ['TEXT', 'LONG_TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'MULTI_SELECT'];
let oneDefinition: CustomFieldDefinition[] = [];

@Component({
  imports: [CustomFieldsFormComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-custom-fields-form [definitions]="defs" [form]="form" [lang]="'gl'" [showErrors]="show()" />`,
})
class OneFieldHostComponent {
  readonly defs = oneDefinition;
  readonly form = buildCustomFieldsForm(oneDefinition, {});
  readonly show = signal(false);
}

describe.each(TYPES)('CustomFieldsFormComponent, tipo %s', (type) => {
  it('liga axuda e erro con aria-describedby e marca aria-invalid ao enviar', async () => {
    oneDefinition = [
      definition({
        code: 'x',
        type,
        required: true,
        options: type === 'SELECT' || type === 'MULTI_SELECT' ? SELECT_OPTIONS : [],
      }),
    ];
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(OneFieldHostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const control = el.querySelector('#cf-x');
    if (!control) throw new Error(`${type}: sen #cf-x`);
    // Nos grupos (sí/non, múltiple) os atributos van no fieldset, que é o que anuncia o lector.
    const owner = type === 'BOOLEAN' || type === 'MULTI_SELECT' ? control.closest('fieldset') : control;
    expect(owner, `${type}: elemento con aria`).not.toBeNull();
    expect(owner?.getAttribute('aria-describedby')).toBe('cf-x-help');
    expect(owner?.getAttribute('aria-invalid')).toBeNull();
    if (type === 'SELECT') {
      // O botón non admite aria-required: o «(obrigatorio)» vai no nome, coma no rótulo.
      expect(control.getAttribute('aria-label')).toContain('(obrigatorio)');
    }

    fixture.componentInstance.show.set(true);
    await fixture.whenStable();
    expect(owner?.getAttribute('aria-describedby')).toBe('cf-x-help cf-x-error');
    expect(owner?.getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector('#cf-x-help')?.textContent).toContain('Para avisos');
    expect(el.querySelector('#cf-x-error')?.textContent?.trim()).not.toBe('');
  });
});
