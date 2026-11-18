// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { ControlError } from '../../custom-fields/custom-field-controls';
import { FormControl, Validators } from '@angular/forms';
import { BilingualFieldComponent } from '../bilingual-field/bilingual-field.component';
import { FieldComponent } from '../field.component';
import { InputDirective } from '../input/input.directive';
import { FieldActionComponent } from './field-action.component';
import { FieldGroupComponent } from './field-group.component';

const REQUIRED: ControlError = { key: 'errors.validation.required', params: {} };

@Component({
  imports: [FieldComponent, FieldGroupComponent, InputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-field-group id="who">
      <furar-field fieldId="name" label="Nome" [error]="nameError()">
        <input furarInput id="name" [attr.aria-invalid]="nameError() ? 'true' : null" aria-describedby="name-error" />
      </furar-field>
      <div class="f-field-cells">
        <furar-field fieldId="email" label="Correo" [error]="emailError()">
          <input furarInput id="email" [attr.aria-invalid]="emailError() ? 'true' : null" />
        </furar-field>
        @if (phone()) {
          <furar-field fieldId="phone" label="Teléfono" [error]="REQUIRED">
            <input furarInput id="phone" aria-invalid="true" aria-describedby="phone-error" />
          </furar-field>
        }
      </div>
    </furar-field-group>
    <furar-field-group id="hours" cells label="Horas">
      <furar-field fieldId="from" label="Desde"><input furarInput id="from" /></furar-field>
      <furar-field fieldId="to" label="Ata"><input furarInput id="to" /></furar-field>
    </furar-field-group>
  `,
})
class HostComponent {
  protected readonly REQUIRED = REQUIRED;
  readonly nameError = signal<ControlError | null>(null);
  readonly emailError = signal<ControlError | null>(null);
  readonly phone = signal(false);
}

async function render() {
  TestBed.configureTestingModule({ providers: provideTestI18n() });
  const fixture = TestBed.createComponent(HostComponent);
  await fixture.whenStable();
  const el = fixture.nativeElement as HTMLElement;
  const group = el.querySelector<HTMLElement>('#who')!;
  /** Os erros do grupo, na orde de lectura (cada un dentro do seu campo). */
  const errors = () => [...group.querySelectorAll<HTMLElement>('.f-field-error')];
  return { fixture, el, group, errors };
}

describe('furar-field-group', () => {
  it('unha cápsula cos campos dentro; cada erro, xusto debaixo do seu campo, sen o rótulo diante', async () => {
    const { fixture, group, errors } = await render();
    const box = group.querySelector(':scope > .f-field-group')!;
    expect(box.querySelectorAll('furar-field')).toHaveLength(2);
    expect(errors()).toEqual([]);

    fixture.componentInstance.emailError.set(REQUIRED);
    fixture.componentInstance.nameError.set(REQUIRED);
    await fixture.whenStable();
    expect(errors().map((e) => e.id)).toEqual(['name-error', 'email-error']);
    // Dentro do grupo e do seu campo (o último fillo, debaixo do control); xa non debaixo da cápsula.
    expect(group.querySelector(':scope > .f-field-error')).toBeNull();
    for (const error of errors()) {
      expect(error.parentElement?.matches('furar-field')).toBe(true);
      expect(error.parentElement?.lastElementChild).toBe(error);
    }
    expect(errors()[0]!.textContent?.trim()).toBe('Este campo é obrigatorio.');
    expect(group.ownerDocument.querySelectorAll('#name-error')).toHaveLength(1);
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('nunha fila de campos curtos, cada erro debaixo da súa metade', async () => {
    const { fixture, group } = await render();
    fixture.componentInstance.emailError.set(REQUIRED);
    fixture.componentInstance.phone.set(true);
    await fixture.whenStable();
    const cells = group.querySelector('.f-field-cells')!;
    expect([...cells.children].map((field) => field.querySelector('.f-field-error')?.id)).toEqual([
      'email-error',
      'phone-error',
    ]);
    fixture.componentInstance.phone.set(false);
    await fixture.whenStable();
    expect(group.querySelector('#phone-error')).toBeNull();
  });

  it('con cells, os campos curtos van nunha fila; con label, a cápsula é un grupo con nome', async () => {
    const { el } = await render();
    const hours = el.querySelector('#hours > .f-field-group')!;
    expect(hours.hasAttribute('data-cells')).toBe(true);
    expect(hours.getAttribute('role')).toBe('group');
    expect(hours.getAttribute('aria-label')).toBe('Horas');
    expect(el.querySelector('#who > .f-field-group')!.hasAttribute('role')).toBe(false);
  });

  it('fields.css: marco do grupo, liñas entre campos, celas que parten e campos sen marco propio dentro', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/styles/components/fields.css'), 'utf8');
    const rule = (selector: string) =>
      css.slice(css.indexOf(`${selector} {`), css.indexOf('}', css.indexOf(`${selector} {`)));
    expect(rule('  .f-field-group')).toContain('--f-cap-line: transparent;');
    expect(rule('  .f-field-group')).toContain('border: var(--f-border) solid var(--f-line);');
    expect(rule('  .f-field-group > * + *')).toContain('border-top: var(--f-border) solid var(--f-divider);');
    expect(rule('  :is(.f-field-cells, .f-field-group[data-cells]) > *')).toContain('flex: 1 1 var(--f-cell, 10rem);');
    // O erro dentro do grupo, co sangrado do texto do campo.
    expect(css).toContain('.f-field-group :is(.f-field, .f-bilingual, .f-field-popover) > .f-field-error {');
  });
});

@Component({
  imports: [BilingualFieldComponent, FieldGroupComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-field-group id="pay">
      <furar-bilingual-field fieldId="how-pay" label="Como se paga" multiline [gl]="payGl" [es]="payEs" submitted />
      <furar-bilingual-field fieldId="how-refund" label="Como se devolve" multiline [gl]="refundGl" [es]="refundEs" />
    </furar-field-group>
  `,
})
class BilingualHostComponent {
  readonly payGl = new FormControl('No mostrador', { nonNullable: true, validators: [Validators.required] });
  readonly payEs = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly refundGl = new FormControl('', { nonNullable: true });
  readonly refundEs = new FormControl('', { nonNullable: true });
}

describe('furar-field-group con campos bilingües', () => {
  it('o erro do bilingüe vai debaixo del, dentro do grupo, co idioma diante e o id que describe o campo', async () => {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(BilingualHostComponent);
    await fixture.whenStable();
    const group = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('#pay')!;
    const errors = [...group.querySelectorAll<HTMLElement>('.f-field-error')];
    expect(errors.map((e) => e.id)).toEqual(['how-pay-error']);
    expect(errors[0]!.parentElement?.matches('furar-bilingual-field')).toBe(true);
    expect(errors[0]!.textContent?.replace(/\s+/g, ' ').trim()).toBe('Castelán: Este campo é obrigatorio.');
    // O campo pasou ao idioma co erro e descríbese co seu erro.
    expect(group.querySelector('#how-pay')?.getAttribute('aria-describedby')).toBe('how-pay-error');
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('a celda dun botón ao final da fila (furar-field-action) e o número pegado á unidade (affix="tight")', async () => {
    @Component({
      imports: [FieldComponent, FieldGroupComponent, FieldActionComponent, InputDirective],
      changeDetection: ChangeDetectionStrategy.OnPush,
      template: `
        <furar-field-group>
          <div class="f-field-cells">
            <furar-field fieldId="a" label="Abre"><input furarInput id="a" /></furar-field>
            <furar-field-action><button type="button" aria-label="Quitar o tramo">x</button></furar-field-action>
          </div>
        </furar-field-group>
        <furar-field fieldId="n" label="Polo menos" affix="tight">
          <span class="f-field-affix"
            ><input furarInput id="n" /><span class="f-field-affix__unit">horas antes</span></span
          >
        </furar-field>
      `,
    })
    class ActionHost {}
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(ActionHost);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.f-field-cells > furar-field-action.f-field-action button')).not.toBeNull();
    expect(el.querySelector('furar-field[data-affix="tight"]')).not.toBeNull();
    const css = readFileSync(resolve(process.cwd(), 'src/styles/components/fields.css'), 'utf8');
    expect(css).toContain('> .f-field-action {');
    expect(css).toContain(".f-field[data-affix='tight'] .f-field-affix__unit");
    await expectNoAxeViolations(el);
  });
});
