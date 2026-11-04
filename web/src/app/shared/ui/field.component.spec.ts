// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { provideTestI18n } from '../../../testing/i18n';
import type { ControlError } from '../custom-fields/custom-field-controls';
import { FieldComponent } from './field.component';
import { describedBy, fieldError } from './field-errors';

/** Mesmo patrón que as pantallas (municipality-create.page.html): o control proxectado leva os aria-*. */
@Component({
  selector: 'furar-field-host',
  imports: [FieldComponent, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-field fieldId="f-name" [label]="label" [help]="help" [required]="true" [error]="error()">
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
      child.matches('label') ? 'label' : child.contains(input) ? 'control' : child.className,
    );
    expect(order).toEqual(['label', 'f-help', 'control']);
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
