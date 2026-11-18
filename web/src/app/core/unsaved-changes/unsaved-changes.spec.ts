// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { provideTestI18n } from '../../../testing/i18n';
import { unsavedChangesGuard } from './unsaved-changes.guard';
import { UnsavedChangesService } from './unsaved-changes.service';

const stable = () => TestBed.inject(ApplicationRef).whenStable();
const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');

function runGuard(): Promise<boolean> {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;
  const result = TestBed.runInInjectionContext(() => unsavedChangesGuard(null, route, state, state));
  return Promise.resolve(result as boolean | Promise<boolean>);
}

function unload(): Event {
  const event = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(event);
  return event;
}

describe('UnsavedChangesService e unsavedChangesGuard', () => {
  let service: UnsavedChangesService;
  const dirty = signal(false);

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    service = TestBed.inject(UnsavedChangesService);
    dirty.set(false);
  });
  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  it('sen seccións sucias, a garda deixa saír sen preguntar e beforeunload non avisa', async () => {
    const untrack = service.track(dirty);
    TestBed.tick();
    expect(service.hasUnsaved()).toBe(false);
    expect(await runGuard()).toBe(true);
    expect(dialog()).toBeNull();
    expect(unload().defaultPrevented).toBe(false);
    untrack();
  });

  it('beforeunload só avisa mentres hai unha sección sucia', () => {
    const untrack = service.track(dirty);
    dirty.set(true);
    TestBed.tick();
    expect(unload().defaultPrevented).toBe(true);

    dirty.set(false);
    TestBed.tick();
    expect(unload().defaultPrevented).toBe(false);

    dirty.set(true);
    untrack();
    TestBed.tick();
    expect(service.hasUnsaved()).toBe(false);
    expect(unload().defaultPrevented).toBe(false);
  });

  it('con cambios, a garda pregunta co diálogo propio: «Seguir editando» queda e «Saír sen gardar» sae', async () => {
    const untrack = service.track(dirty);
    dirty.set(true);

    const stay = runGuard();
    await stable();
    expect(dialog()?.getAttribute('role')).toBe('alertdialog');
    expect(dialog()?.textContent).toContain('Saír sen gardar?');
    const cancel = dialog()!.querySelector<HTMLButtonElement>('[data-confirm-cancel]')!;
    expect(document.activeElement).toBe(cancel);
    expect(cancel.textContent?.trim()).toBe('Seguir editando');
    cancel.click();
    expect(await stay).toBe(false);

    const leave = runGuard();
    await stable();
    dialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    expect(await leave).toBe(true);
    untrack();
  });
});
