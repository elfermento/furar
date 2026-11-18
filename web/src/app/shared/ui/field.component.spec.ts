// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { provideTestI18n } from '../../../testing/i18n';
import type { ControlError } from '../custom-fields/custom-field-controls';
import { FieldGroupComponent } from './field-group/field-group.component';
import { FieldComponent } from './field.component';
import { describedBy, fieldError } from './field-errors';

/** Mesmo patrón que as pantallas (municipality-create.page.html): o control proxectado leva os aria-*. */
@Component({
  selector: 'furar-field-host',
  imports: [FieldComponent, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-field fieldId="f-name" [label]="label" [help]="help" [required]="true" [error]="error()">
      <button id="f-aside" type="button" fieldLabelAside aria-label="Axuda">?</button>
      <input
        id="f-name"
        [formControl]="control"
        [attr.aria-invalid]="error() ? 'true' : null"
        [attr.aria-describedby]="describedBy('f-name', true, error())"
      />
    </furar-field>
  `,
})
class HostComponent {
  readonly label = 'Nome';
  readonly help = 'Como aparece na web';
  readonly control = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly error = signal<ControlError | null>(null);
  protected readonly describedBy = describedBy;
}

async function render() {
  TestBed.configureTestingModule({ providers: provideTestI18n() });
  const fixture = TestBed.createComponent(HostComponent);
  await fixture.whenStable();
  const el = fixture.nativeElement as HTMLElement;
  const input = el.querySelector('input') as HTMLInputElement;
  /** Elementos aos que apunta aria-describedby: todos teñen que existir. */
  const described = () =>
    (input.getAttribute('aria-describedby') ?? '')
      .split(' ')
      .filter(Boolean)
      .map((id) => el.querySelector(`#${id}`));
  return { fixture, el, input, described };
}

describe('FieldComponent', () => {
  it('liga a etiqueta visible ao control co for (nome accesible)', async () => {
    const { el, input } = await render();
    const label = el.querySelector('label') as HTMLLabelElement;
    expect(label.htmlFor).toBe('f-name');
    expect([...(input.labels ?? [])]).toEqual([label]);
    expect(label.textContent).toContain('Nome');
    expect(label.textContent).toContain('obrigatorio');
  });

  it('obrigatorio: «*» á vista e oculto ao lector; «(obrigatorio)» só para o lector', async () => {
    const { el } = await render();
    const label = el.querySelector('label') as HTMLLabelElement;
    const star = label.querySelector('.f-required') as HTMLElement;
    expect(star.textContent?.trim()).toBe('*');
    expect(star.getAttribute('aria-hidden')).toBe('true');
    expect(label.querySelector('.sr-only')?.textContent?.trim()).toBe('(obrigatorio)');
    expect(label.textContent).not.toMatch(/\(obrigatorio\)\s*\*/);
  });

  it('axuda entre o rótulo e o control: rótulo, axuda, control (orde do DOM)', async () => {
    const { el, input } = await render();
    const host = el.querySelector('furar-field') as HTMLElement;
    const order = [...host.children].map((child) =>
      child.querySelector('label') ? 'label' : child.contains(input) ? 'control' : child.className,
    );
    expect(order).toEqual(['label', 'f-help', 'control']);
  });

  it('`fieldLabelAside` vai na liña do rótulo, despois del e fóra do <label>', async () => {
    const { el, input } = await render();
    const head = el.querySelector('.f-field__head') as HTMLElement;
    const aside = el.querySelector('#f-aside') as HTMLElement;
    expect(aside.parentElement).toBe(head);
    expect(head.firstElementChild?.matches('label')).toBe(true);
    expect(aside.closest('label')).toBeNull();
    expect(el.querySelector('.f-field__control')?.contains(aside)).toBe(false);
    expect(el.querySelector('.f-field__control')?.contains(input)).toBe(true);
  });

  it('sen erro: aria-describedby só á axuda, sen aria-invalid', async () => {
    const { input, described } = await render();
    expect(input.hasAttribute('aria-invalid')).toBe(false);
    expect(described().map((n) => n?.id)).toEqual(['f-name-help']);
    expect(described()[0]?.textContent).toContain('Como aparece na web');
  });

  it('con erro: aria-invalid e aria-describedby apuntan a axuda e erro, que existen e están traducidos', async () => {
    const { fixture, input, described } = await render();
    const host = fixture.componentInstance;
    host.control.markAsTouched();
    host.error.set(fieldError(host.control, false));
    await fixture.whenStable();
    expect(input.getAttribute('aria-invalid')).toBe('true');
    const nodes = described();
    expect(nodes.map((n) => n?.id)).toEqual(['f-name-help', 'f-name-error']);
    const errorText = nodes[1]?.textContent?.trim() ?? '';
    expect(errorText).not.toBe('');
    expect(errorText).not.toContain('errors.');
  });

  it('o erro desaparece (e o seu id) cando se corrixe', async () => {
    const { fixture, el, input, described } = await render();
    const host = fixture.componentInstance;
    host.error.set({ key: 'errors.validation.required', params: {} });
    await fixture.whenStable();
    expect(el.querySelector('#f-name-error')).not.toBeNull();
    host.error.set(null);
    await fixture.whenStable();
    expect(el.querySelector('#f-name-error')).toBeNull();
    expect(input.hasAttribute('aria-invalid')).toBe(false);
    expect(described().every((n) => n !== null)).toBe(true);
  });
});

@Component({
  selector: 'furar-field-tip-host',
  imports: [FieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-field fieldId="f-code" [label]="'Código DIR3'" [tip]="'Coma L01150548'">
      <input id="f-code" [attr.aria-describedby]="describedBy('f-code', true, null)" />
    </furar-field>
    <furar-field fieldId="f-dpo" [label]="'Correo do DPD'" [tip]="'Quen vela'" [tipLabel]="'Que é o DPD?'">
      <input id="f-dpo" />
    </furar-field>
  `,
})
class TipHostComponent {
  protected readonly describedBy = describedBy;
}

describe('FieldComponent con tip', () => {
  it('a axuda vai nun «?» ao carón do rótulo, non debaixo; o control descríbese igual', async () => {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(TipHostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const [code, dpo] = [...el.querySelectorAll<HTMLElement>('furar-field')];
    expect(code!.querySelector('.f-help')).toBeNull();
    const tip = code!.querySelector('.f-field__head furar-help-tip button')!;
    expect(tip.getAttribute('aria-label')).toBe('Axuda sobre «Código DIR3»');
    expect(tip.getAttribute('aria-describedby')).toBe('f-code-help');
    expect(el.querySelector('#f-code-help')?.textContent?.trim()).toBe('Coma L01150548');
    expect(el.querySelector('#f-code')?.getAttribute('aria-describedby')).toBe('f-code-help');
    expect(dpo!.querySelector('furar-help-tip button')?.getAttribute('aria-label')).toBe('Que é o DPD?');
  });
});

@Component({
  selector: 'furar-field-capsule-host',
  imports: [FieldComponent, FieldGroupComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-field fieldId="c-name" [label]="'Nome'" [help]="'Como na web'" [error]="error()">
      <input id="c-name" />
    </furar-field>
    <furar-field fieldId="c-kind" [label]="'Tipo'">
      <button id="c-kind" type="button" (click)="opened.set(opened() + 1)">{{ 'Pista' }}</button>
      <div role="dialog" aria-label="Calendario">
        <table role="grid">
          <tr>
            <td id="c-day">12</td>
          </tr>
        </table>
      </div>
    </furar-field>
    <furar-field-group>
      <furar-field fieldId="c-mail" [label]="'Correo'" [error]="error()"><input id="c-mail" /></furar-field>
    </furar-field-group>
  `,
})
class CapsuleHostComponent {
  readonly error = signal<ControlError | null>({ key: 'errors.validation.required', params: {} });
  readonly opened = signal(0);
}

describe('FieldComponent na cápsula', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(CapsuleHostComponent);
    document.body.append(fixture.nativeElement);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('picar na cápsula fóra do control leva o foco ao campo ou abre o despregable; na axuda ou no erro, non', async () => {
    const { fixture, el } = await render();
    const [name, kind] = [...el.querySelectorAll<HTMLElement>('furar-field')];
    name!.querySelector<HTMLElement>('.f-field__head')!.click();
    expect(document.activeElement?.id).toBe('c-name');
    (document.activeElement as HTMLElement).blur();
    name!.querySelector<HTMLElement>('.f-help')!.click();
    name!.querySelector<HTMLElement>('.f-field-error')!.click();
    expect(document.activeElement).toBe(document.body);
    kind!.querySelector<HTMLElement>('.f-field__head')!.click();
    expect(fixture.componentInstance.opened()).toBe(1);
    // Un día do calendario (unha celda, non un botón) é da capa: non volve abrir o despregable.
    kind!.querySelector<HTMLElement>('#c-day')!.click();
    expect(fixture.componentInstance.opened()).toBe(1);
  });

  it('dentro dun grupo pinta o seu erro debaixo del, sen o rótulo diante', async () => {
    const { el } = await render();
    const mail = el.querySelector<HTMLElement>('furar-field-group furar-field')!;
    const error = mail.querySelector('#c-mail-error');
    expect(error?.classList).toContain('f-field-error');
    expect(mail.lastElementChild).toBe(error);
    expect(error?.textContent).not.toContain('Correo:');
    expect(el.querySelector('furar-field-group > .f-field-error')).toBeNull();
  });
});
