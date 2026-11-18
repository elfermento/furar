// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiProblem } from '../../../core/errors/api-problem';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { EditSession } from '../edit-session/edit-session';
import { EditFieldComponent } from './edit-field.component';
import { EditSectionComponent } from './edit-section.component';

@Component({
  imports: [ReactiveFormsModule, EditSectionComponent, EditFieldComponent],
  providers: [EditSession],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-edit-section [formGroup]="form" [heading]="'Contacto'" [save]="save" (cancelled)="cancelled.set(true)">
      <furar-edit-field [label]="'Teléfono'" [value]="'981 000 000'" [control]="form.controls.phone">
        <label for="phone">{{ 'Teléfono' }}</label>
        <input id="phone" formControlName="phone" />
      </furar-edit-field>
    </furar-edit-section>
  `,
})
class HostComponent {
  readonly form = new FormGroup({
    phone: new FormControl('981 000 000', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly cancelled = signal(false);
  next: () => Promise<unknown> = () => Promise.resolve();
  readonly save = () => this.next();
}

describe('furar-edit-section no modo edición global', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    const el = fixture.nativeElement as HTMLElement;
    document.body.appendChild(el);
    await fixture.whenStable();
    const session = fixture.debugElement.injector.get(EditSession);
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    const input = () => el.querySelector<HTMLInputElement>('#phone')!;
    return { fixture, el, session, settle, input, host: fixture.componentInstance };
  }

  it('fóra da edición, texto sen botón; dentro, o campo sen pé propio', async () => {
    const { el, session, settle } = await render();
    expect(el.querySelector('.f-edit-field__static')?.textContent).toContain('981 000 000');
    expect(el.querySelector('.f-edit-field__trigger')?.hasAttribute('hidden')).toBe(true);
    session.start();
    await settle();
    expect(el.querySelector('.f-edit-field__static')).toBeNull();
    expect(el.querySelector('#phone')).not.toBeNull();
    expect(el.querySelector('.f-edit-section__footer')?.hasAttribute('hidden')).toBe(true);
    await expectNoAxeViolations(el);
  });

  it('a sesión valida, garda e cancela a sección', async () => {
    const { el, session, settle, input, host } = await render();
    session.start();
    await settle();
    input().value = '';
    input().dispatchEvent(new Event('input'));
    await settle();
    expect(await session.save()).toBe('invalid');
    await settle();
    expect(host.form.controls.phone.touched && host.form.controls.phone.invalid).toBe(true);

    input().value = '981 111 111';
    input().dispatchEvent(new Event('input'));
    host.next = () => Promise.reject(new ApiProblem(412, 'common.stale-version', {}, [], undefined, ''));
    expect(await session.save()).toBe('failed');
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Os datos cambiaron');

    host.next = () => Promise.resolve();
    expect(await session.save()).toBe('saved');
    expect(host.form.pristine).toBe(true);

    session.start();
    input().value = 'cambio';
    input().dispatchEvent(new Event('input'));
    await settle();
    session.cancel();
    await settle();
    expect(host.form.controls.phone.value).toBe('981 111 111');
    expect(host.cancelled()).toBe(true);
  });

  it('Intro nun campo garda a pantalla enteira', async () => {
    const { el, session, settle, input } = await render();
    const save = vi.spyOn(session, 'save');
    session.start();
    await settle();
    input().value = '981 222 222';
    input().dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();
    expect(save).toHaveBeenCalled();
  });
});
