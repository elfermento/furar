// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { ButtonDirective } from '../button/button.directive';
import { ActionScope, ActionScopeDirective } from './action-scope';

/**
 * Un formulario con «Gardar» (envío, async), «Cancelar» (a acción contraria) e, nun contexto
 * propio, outro botón que non depende deles.
 */
@Component({
  imports: [ButtonDirective, ActionScopeDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form (submit)="save($event)">
      <label for="name">{{ 'Nome' }}</label>
      <input id="name" />
      <button id="cancel" type="button" furarButton (click)="log.push('cancel')">{{ 'Cancelar' }}</button>
      <button id="save" type="submit" furarButton="primary" [busy]="saving()">
        {{ saving() ? 'Gardando' : 'Gardar' }}
      </button>
    </form>
    <a id="link" href="/outra" furarButton (click)="follow($event)">{{ 'Ir' }}</a>
    <button id="later" type="button" furarButton [unavailable]="waiting()" (click)="log.push('later')">
      {{ 'Reenviar' }}
    </button>
    <section furarActionScope>
      <button id="other" type="button" furarButton (click)="log.push('other')">{{ 'Outra' }}</button>
    </section>
  `,
})
class HostComponent {
  readonly saving = signal(false);
  readonly waiting = signal(false);
  readonly log: string[] = [];
  finish: () => void = () => undefined;

  save(event: Event): void {
    event.preventDefault();
    this.log.push('save');
    this.saving.set(true);
    void new Promise<void>((resolve) => (this.finish = resolve)).then(() => this.saving.set(false));
  }

  follow(event: Event): void {
    event.preventDefault();
    this.log.push('link');
  }
}

describe('Accións en curso (ActionScope e furarAction en furarButton)', () => {
  let fixture: ComponentFixture<HostComponent>;
  const el = () => fixture.nativeElement as HTMLElement;
  const button = (id: string) => el().querySelector<HTMLButtonElement>(`#${id}`)!;
  const host = () => fixture.componentInstance;
  const settle = () => fixture.whenStable();

  beforeEach(async () => {
    fixture = TestBed.createComponent(HostComponent);
    document.body.append(fixture.nativeElement as HTMLElement);
    await settle();
  });

  afterEach(() => (fixture.nativeElement as HTMLElement).remove());

  it('mentres garda: «Gardar» ocupado co foco e o nome, e o resto do contexto con aria-disabled (non disabled)', async () => {
    button('save').focus();
    button('save').click();
    await settle();
    expect(host().log).toEqual(['save']);
    expect(button('save').getAttribute('aria-busy')).toBe('true');
    expect(button('save').getAttribute('aria-disabled')).toBe('true');
    expect(button('save').disabled).toBe(false);
    expect(document.activeElement).toBe(button('save'));
    expect(button('cancel').getAttribute('aria-disabled')).toBe('true');
    expect(button('cancel').disabled).toBe(false);
    expect(button('link').getAttribute('aria-disabled')).toBe('true');
    // Outro contexto non queda bloqueado.
    expect(button('other').hasAttribute('aria-disabled')).toBe(false);
    await expectNoAxeViolations(el());

    host().finish();
    await vi.waitFor(async () => {
      await settle();
      expect(button('save').hasAttribute('aria-busy')).toBe(false);
    });
    expect(button('cancel').hasAttribute('aria-disabled')).toBe(false);
    expect(document.activeElement).toBe(button('save'));
  });

  it('dobre clic: o segundo non envía outra vez, nin antes nin despois de pintar o busy', async () => {
    button('save').click();
    button('save').click(); // aínda sen render: reservado polo primeiro clic
    expect(host().log).toEqual(['save']);
    await settle();
    button('save').click();
    // O Intro nun campo envía co botón por defecto: tamén queda sen efecto.
    el().querySelector('form')!.requestSubmit();
    await settle();
    expect(host().log).toEqual(['save']);
    host().finish();
  });

  it('acción contraria: «Cancelar» e a ligazón non chegan á feature mentres se garda', async () => {
    button('save').click();
    button('cancel').click(); // no mesmo instante
    await settle();
    button('cancel').click();
    const navigation = new MouseEvent('click', { bubbles: true, cancelable: true });
    button('link').dispatchEvent(navigation);
    expect(navigation.defaultPrevented).toBe(true);
    button('other').click();
    expect(host().log).toEqual(['save', 'other']);
    host().finish();
    await vi.waitFor(async () => {
      await settle();
      expect(button('cancel').hasAttribute('aria-disabled')).toBe(false);
    });
    button('cancel').click();
    expect(host().log).toEqual(['save', 'other', 'cancel']);
  });

  it('unavailable: aria-disabled e o clic bloqueado, sen perder o foco', async () => {
    host().waiting.set(true);
    await settle();
    button('later').focus();
    button('later').click();
    expect(host().log).toEqual([]);
    expect(button('later').getAttribute('aria-disabled')).toBe('true');
    expect(button('later').disabled).toBe(false);
    expect(document.activeElement).toBe(button('later'));
  });

  it('run: unha acción sen botón ocupa o contexto e non deixa empezar outra ata que remata', async () => {
    const scope = TestBed.inject(ActionScope);
    let finish!: () => void;
    const first = scope.run(() => new Promise<string>((resolve) => (finish = () => resolve('feito'))));
    expect(scope.busy()).toBe(true);
    expect(await scope.run(() => Promise.resolve('outra'))).toBeUndefined();
    finish();
    expect(await first).toBe('feito');
    expect(scope.busy()).toBe(false);
    await expect(scope.run(() => Promise.reject(new Error('mal')))).rejects.toThrow('mal');
    expect(scope.busy()).toBe(false);
  });
});
