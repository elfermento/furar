// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiProblem } from '../../../core/errors/api-problem';
import { UnsavedChangesService } from '../../../core/unsaved-changes/unsaved-changes.service';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { EditFieldComponent } from './edit-field.component';
import { EditSectionComponent, SAVED_NOTICE_MS } from './edit-section.component';

@Component({
  imports: [ReactiveFormsModule, EditSectionComponent, EditFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-edit-section
      id="contact"
      [formGroup]="form"
      [heading]="'Contacto'"
      [save]="save"
      (saved)="events.push('saved')"
      (cancelled)="events.push('cancelled')"
      (failed)="events.push('failed')"
    >
      <furar-edit-field id="phone-field" [label]="'Teléfono'" [value]="phone()" [control]="form.controls.phone">
        <label for="phone">{{ 'Teléfono' }}</label>
        <input id="phone" formControlName="phone" />
      </furar-edit-field>
      <div formArrayName="parts">
        <label for="part0">{{ 'Parte 1' }}</label>
        <input id="part0" [formControlName]="0" />
      </div>
    </furar-edit-section>
  `,
})
class HostComponent {
  readonly form = new FormGroup({
    phone: new FormControl('981 000 000', { nonNullable: true, validators: [Validators.required] }),
    parts: new FormArray([new FormControl('Pista A', { nonNullable: true })]),
  });
  readonly phone = signal('981 000 000');
  readonly events: string[] = [];
  next: () => Promise<unknown> = () => Promise.resolve();
  readonly save = () => this.next();
}

describe('furar-edit-section e furar-edit-field', () => {
  let fixture: ComponentFixture<HostComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    fixture = TestBed.createComponent(HostComponent);
    el = fixture.nativeElement as HTMLElement;
    document.body.appendChild(el);
    await fixture.whenStable();
  });

  afterEach(() => {
    el.remove();
    vi.useRealTimers();
  });

  const host = () => fixture.componentInstance;
  const q = <T extends HTMLElement>(selector: string) => el.querySelector<T>(selector)!;
  const trigger = () => q<HTMLButtonElement>('#phone-field .f-edit-field__trigger');
  const editor = () => q<HTMLElement>('#phone-field .f-edit-field__editor');
  const footer = () => q<HTMLElement>('.f-edit-section__footer');
  const heading = () => q<HTMLElement>('h2');
  const status = () => q<HTMLElement>('[role="status"]');
  const stable = () => fixture.whenStable();

  async function type(selector: string, value: string): Promise<void> {
    const input = q<HTMLInputElement>(selector);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await stable();
  }

  async function submit(): Promise<void> {
    q<HTMLFormElement>('form').dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
    await stable();
  }

  async function escape(target: HTMLElement): Promise<KeyboardEvent> {
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
    target.dispatchEvent(event);
    await stable();
    return event;
  }

  it('é un <section> co título como nome e, sen cambios, sen accións; pasa axe', async () => {
    const section = q<HTMLElement>('section');
    expect(document.getElementById(section.getAttribute('aria-labelledby')!)).toBe(heading());
    expect(heading().getAttribute('tabindex')).toBe('-1');
    expect(section.querySelector('form')).not.toBeNull();
    expect(footer().hidden).toBe(true);
    expect(trigger().textContent).toContain('Teléfono');
    expect(trigger().textContent).toContain('981 000 000');
    expect(trigger().textContent).toContain('editar');
    expect(editor().hidden).toBe(true);
    await expectNoAxeViolations(el);
  });

  it('o valor en repouso é un botón: ao activalo o foco vai ao campo e Esc devólveo ao botón', async () => {
    trigger().click();
    await stable();
    expect(trigger().hidden).toBe(true);
    expect(editor().hidden).toBe(false);
    expect(document.activeElement).toBe(q('#phone'));

    const event = await escape(q('#phone'));
    expect(event.defaultPrevented).toBe(true);
    expect(editor().hidden).toBe(true);
    expect(document.activeElement).toBe(trigger());
  });

  it('Esc non pecha se o control xa o usou (preventDefault) nin se o campo ten un erro á vista', async () => {
    trigger().click();
    await stable();
    q('#phone').addEventListener('keydown', (event) => event.preventDefault(), { once: true });
    await escape(q('#phone'));
    expect(editor().hidden).toBe(false);

    await type('#phone', '');
    host().form.controls.phone.markAsTouched();
    await stable();
    await escape(q('#phone'));
    expect(editor().hidden).toBe(false);
  });

  it('con cambios amosa «Cambios sen gardar» e conta para a garda; Cancelar restaura e leva o foco ao título', async () => {
    const unsaved = TestBed.inject(UnsavedChangesService);
    trigger().click();
    await type('#phone', '600 111 222');
    expect(footer().hidden).toBe(false);
    expect(footer().textContent).toContain('Cambios sen gardar');
    expect(el.querySelector('furar-edit-section')!.hasAttribute('data-dirty')).toBe(true);
    expect(unsaved.hasUnsaved()).toBe(true);
    await expectNoAxeViolations(el);

    q<HTMLButtonElement>('[data-section-cancel]').click();
    await stable();
    expect(host().form.getRawValue()).toEqual({ phone: '981 000 000', parts: ['Pista A'] });
    expect(host().form.pristine).toBe(true);
    expect(footer().hidden).toBe(true);
    expect(editor().hidden).toBe(true);
    expect(document.activeElement).toBe(heading());
    expect(host().events).toEqual(['cancelled']);
    expect(unsaved.hasUnsaved()).toBe(false);
  });

  it('Cancelar volve ao último estado limpo, tamén se a feature recargou os valores', async () => {
    host().form.reset({ phone: '981 222 333', parts: ['Pista B'] });
    await type('#part0', 'Pista C');
    q<HTMLButtonElement>('[data-section-cancel]').click();
    await stable();
    expect(host().form.getRawValue()).toEqual({ phone: '981 222 333', parts: ['Pista B'] });
  });

  it('gardar con erros non chama a save, abre o campo e leva o foco ao primeiro erro', async () => {
    const save = vi.fn(() => Promise.resolve());
    host().next = save;
    await type('#part0', 'Pista C');
    await type('#phone', '');
    await submit();
    expect(save).not.toHaveBeenCalled();
    expect(editor().hidden).toBe(false);
    expect(document.activeElement).toBe(q('#phone'));
  });

  it('gardar ben: ocupado mentres garda, despois limpo, campos pechados, «Gardado» un anaco e foco no título', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    let finish: () => void = () => undefined;
    host().next = () => new Promise<void>((resolve) => (finish = resolve));
    trigger().click();
    await type('#phone', '600 111 222');
    await submit();
    const saveButton = q<HTMLButtonElement>('[data-section-save]');
    expect(saveButton.getAttribute('aria-busy')).toBe('true');
    expect(saveButton.textContent).toContain('Gardando');
    expect(q<HTMLButtonElement>('[data-section-cancel]').getAttribute('aria-disabled')).toBe('true');

    finish();
    // Con setTimeout falso, o planificador sen zonas só pinta ao avanzar o reloxo.
    await vi.advanceTimersByTimeAsync(0);
    await stable();
    expect(host().form.dirty).toBe(false);
    expect(host().form.touched).toBe(false);
    expect(footer().hidden).toBe(true);
    expect(editor().hidden).toBe(true);
    expect(status().textContent).toContain('Gardado');
    expect(document.activeElement).toBe(heading());
    expect(host().events).toEqual(['saved']);

    await vi.advanceTimersByTimeAsync(SAVED_NOTICE_MS);
    await stable();
    expect(status().textContent?.trim()).toBe('');
  });

  it('sen cambios, Intro no campo non garda', async () => {
    const save = vi.fn(() => Promise.resolve());
    host().next = save;
    await submit();
    expect(save).not.toHaveBeenCalled();
  });

  it('os errors[] da API van ao seu campo (tamén por índice) e o foco ao primeiro, sen perder o escrito', async () => {
    host().next = () =>
      Promise.reject(
        new ApiProblem(
          422,
          'validation.failed',
          {},
          [{ field: 'parts[0]', code: 'validation.size', params: {} }],
          undefined,
          '',
        ),
      );
    await type('#part0', 'Pista C');
    await submit();
    const part = host().form.controls.parts.at(0);
    expect(part.errors).toEqual({ 'validation.size': {} });
    expect(part.value).toBe('Pista C');
    expect(document.activeElement).toBe(q('#part0'));
    expect(q('[role="alert"]').textContent?.trim()).toBe('');
    expect(host().events).toEqual(['failed']);
    expect(footer().hidden).toBe(false);
  });

  it('un erro sen campo vai ao aviso da sección (role="alert") e o formulario segue sucio', async () => {
    host().next = () => Promise.reject(new ApiProblem(412, 'common.stale-version', {}, [], 'abc', ''));
    await type('#part0', 'Pista C');
    await submit();
    expect(q('[role="alert"]').textContent).toContain('abc');
    expect(host().form.dirty).toBe(true);
    expect(host().form.controls.parts.at(0).value).toBe('Pista C');
    await expectNoAxeViolations(el);

    host().next = () => Promise.reject(new Error('boom'));
    await submit();
    expect(q('[role="alert"]').textContent?.length).toBeGreaterThan(0);
  });
});
