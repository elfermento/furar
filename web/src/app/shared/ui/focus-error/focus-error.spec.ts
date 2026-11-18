// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { provideTestI18n } from '../../../../testing/i18n';
import { ApiProblem } from '../../../core/errors/api-problem';
import { DialogComponent } from '../dialog/dialog.component';
import { ProblemAlertComponent } from '../problem-alert.component';
import { FocusErrorDirective } from './focus-error.directive';
import { firstFieldError, focusErrorOrAlert, focusFirstError } from './focus-error';

function container(html: string): HTMLElement {
  const host = document.createElement('div');
  host.innerHTML = html;
  document.body.append(host);
  return host;
}

describe('focusFirstError / focusErrorOrAlert', () => {
  let scrolled: Element[];

  beforeEach(() => {
    scrolled = [];
    Element.prototype.scrollIntoView = function (this: Element) {
      scrolled.push(this);
    };
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('o primeiro co erro á vista (aria-invalid) na orde de lectura; se non hai, o primeiro control ng-invalid tocado', () => {
    const host = container(`
      <fieldset class="ng-invalid ng-touched"><button id="scope" type="button">Concello</button></fieldset>
      <furar-select id="kind" class="ng-invalid ng-touched"><button id="kind-button" type="button">Tipo</button></furar-select>
      <input id="name" aria-invalid="true" />
      <input id="phone" aria-invalid="true" />
      <input id="untouched" class="ng-invalid ng-untouched" />
    `);
    expect(firstFieldError(host)?.id).toBe('name');
    expect(focusFirstError(host)).toBe(true);
    expect(document.activeElement?.id).toBe('name');
    for (const input of host.querySelectorAll('[aria-invalid]')) input.removeAttribute('aria-invalid');
    // Sen erro á vista: o primeiro control inválido tocado (o «fieldset» tamén, aínda que só agrupa).
    expect(firstFieldError(host)?.id).toBe('scope');
  });

  it('deixa á vista o campo (a súa cápsula) e a súa mensaxe de erro, e abre o pregable que o tapa', () => {
    const host = container(`
      <details>
        <summary>Máis</summary>
        <div class="f-field">
          <input id="email" aria-invalid="true" aria-describedby="email-help email-error" />
          <span id="email-help" class="f-help">Para os avisos</span>
        </div>
        <span id="email-error" class="f-field-error">Correo non válido</span>
      </details>
    `);
    expect(focusFirstError(host)).toBe(true);
    expect(document.activeElement?.id).toBe('email');
    expect(host.querySelector('details')!.open).toBe(true);
    // Un só desprazamento (dous suaves seguidos cancélanse): o menor contedor co campo e a mensaxe.
    expect(scrolled).toEqual([host.querySelector('details')]);
  });

  it('se o contedor do campo e da mensaxe non cabe na pantalla, deixa á vista o campo', () => {
    const host = container(`
      <div id="tall">
        <div class="f-field"><input id="name" aria-invalid="true" aria-describedby="name-error" /></div>
        <span id="name-error" class="f-field-error">Obrigatorio</span>
      </div>
    `);
    const tall = host.querySelector<HTMLElement>('#tall')!;
    tall.getBoundingClientRect = () => ({ height: window.innerHeight * 2 }) as DOMRect;
    expect(focusFirstError(host)).toBe(true);
    expect(document.activeElement?.id).toBe('name');
    expect(scrolled).toEqual([host.querySelector('.f-field')]);
  });

  it('salta os escondidos; sen campos con erro, o foco vai ao aviso de erro con texto', () => {
    const host = container(`
      <div hidden><input aria-invalid="true" /></div>
      <furar-problem-alert class="f-alert-slot" data-empty><div role="alert"></div></furar-problem-alert>
      <furar-problem-alert class="f-alert-slot" id="server"><div role="alert">Non se puido gardar</div></furar-problem-alert>
    `);
    expect(focusFirstError(host)).toBe(false);
    expect(focusErrorOrAlert(host)).toBe(true);
    const alert = host.querySelector<HTMLElement>('#server [role="alert"]')!;
    expect(document.activeElement).toBe(alert);
    expect(alert.tabIndex).toBe(-1);
  });

  it('sen erro nin aviso, non move o foco', () => {
    const host = container('<input id="ok" />');
    expect(focusErrorOrAlert(host)).toBe(false);
    expect(document.activeElement).toBe(document.body);
  });
});

@Component({
  imports: [FocusErrorDirective, ProblemAlertComponent, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div furarFocusError>
      <furar-problem-alert [problem]="problem()" />
      <form id="form" novalidate [formGroup]="form" (submit)="submit($event)">
        <input id="first" formControlName="first" />
        <input
          id="name"
          formControlName="name"
          [attr.aria-invalid]="nameInvalid() ? 'true' : null"
          aria-describedby="name-error"
        />
        @if (nameInvalid()) {
          <span class="f-field-error" id="name-error">Obrigatorio</span>
        }
      </form>
      <button id="save" type="submit" form="form">Gardar</button>
    </div>
  `,
})
class HostComponent {
  readonly form = new FormGroup({
    first: new FormControl(''),
    name: new FormControl('', { validators: [Validators.required] }),
  });
  readonly nameInvalid = signal(false);
  readonly problem = signal<ApiProblem | null>(null);

  submit(event: Event): void {
    event.preventDefault();
    this.form.markAllAsTouched();
    this.nameInvalid.set(this.form.controls.name.invalid);
  }
}

describe('FocusErrorDirective', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = () => undefined;
  });

  async function render() {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(HostComponent);
    document.body.append(fixture.nativeElement);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const submit = async () => {
      el.querySelector<HTMLFormElement>('#form')!.requestSubmit();
      await fixture.whenStable();
      await new Promise((done) => setTimeout(done));
    };
    return { fixture, el, submit };
  }

  it('ao enviar con erros, o foco vai ao primeiro campo con erro', async () => {
    const { el, submit } = await render();
    el.querySelector<HTMLInputElement>('#first')!.focus();
    await submit();
    expect(document.activeElement?.id).toBe('name');
  });

  it('se o servidor responde despois cun erro que non é dun campo, o foco vai ao aviso', async () => {
    const { fixture, el, submit } = await render();
    fixture.componentInstance.form.controls.name.setValue('Ana');
    await submit();
    expect(document.activeElement?.id).not.toBe('name');
    fixture.componentInstance.problem.set(new ApiProblem(500, 'common.unexpected', {}, [], undefined, 'abc'));
    await fixture.whenStable();
    await new Promise((done) => setTimeout(done));
    expect(document.activeElement).toBe(el.querySelector('furar-problem-alert [role="alert"]'));
  });

  it('se a persoa xa escribiu despois de enviar, un erro que chega non lle move o foco', async () => {
    const { fixture, el, submit } = await render();
    fixture.componentInstance.form.controls.name.setValue('Ana');
    await submit();
    const first = el.querySelector<HTMLInputElement>('#first')!;
    first.focus();
    first.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.componentInstance.nameInvalid.set(true);
    await fixture.whenStable();
    await new Promise((done) => setTimeout(done));
    expect(document.activeElement).toBe(first);
  });
});

@Component({
  imports: [DialogComponent, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog heading="Nova excepción">
      <form id="dialog-form" novalidate (submit)="$event.preventDefault(); invalid.set(true)">
        <input id="reason" [attr.aria-invalid]="invalid() ? 'true' : null" />
      </form>
      <button dialogActions type="submit" form="dialog-form">Aceptar</button>
    </furar-dialog>
  `,
})
class DialogHostComponent {
  readonly invalid = signal(false);
}

describe('furar-dialog: foco ao erro', () => {
  it('ao fallar o envío dun formulario da capa, o foco vai ao primeiro campo con erro', async () => {
    Element.prototype.scrollIntoView = () => undefined;
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(DialogHostComponent);
    document.body.append(fixture.nativeElement);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLFormElement>('#dialog-form')!.requestSubmit();
    await fixture.whenStable();
    await vi.waitFor(() => expect(document.activeElement?.id).toBe('reason'));
  });
});
