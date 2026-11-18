// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import {
  EXPORT_BLOCKED_URL,
  activityEvent,
  activityRequest,
  flushPage,
  renderActivity,
  text,
} from '../../../../testing/activity';
import { backend, expectRequest } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { pickOption } from '../../../../testing/select';

describe('Rexistro de actividade: bloqueo e «Exportar bloqueados» (ADR-0020, revisión do 2026-10-29)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('bloqueo: a liña do ano visible e o diálogo «Exportar bloqueados» co motivo e a referencia obrigatorios; axe', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    expect(text(el.querySelector('[data-blocked-note]'))).toBe(
      'Os eventos de hai máis de 1 ano non se mostran: están bloqueados e só se exportan cun motivo.',
    );
    el.querySelector<HTMLButtonElement>('[data-export-blocked]')!.click();
    await settle();
    const layer = document.querySelector<HTMLElement>('furar-dialog-container')!;
    expect(text(layer.querySelector('h2'))).toBe('Exportar bloqueados');
    layer.querySelector<HTMLButtonElement>('[data-export-send]')!.click();
    await settle();
    expect(text(layer.querySelector('furar-form-errors-summary'))).toContain('2 erro');
    const reference = layer.querySelector<HTMLInputElement>('#export-blocked-reference')!;
    expect(reference.getAttribute('aria-invalid')).toBe('true');
    await expectNoAxeViolations(layer);
  });

  it('exportar bloqueados: POST co motivo e a referencia, CSV coa identidade e anuncio; recarga a lista', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    const names: string[] = [];
    const csvs: Blob[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      names.push(this.download);
    });
    URL.createObjectURL = vi.fn((blob: Blob) => {
      csvs.push(blob);
      return 'blob:x';
    });
    URL.revokeObjectURL = vi.fn();

    el.querySelector<HTMLButtonElement>('[data-export-blocked]')!.click();
    await settle();
    const layer = document.querySelector<HTMLElement>('furar-dialog-container')!;
    await pickOption(layer.querySelector('[data-export-reason]')!, 'Requirimento xudicial', settle);
    const reference = layer.querySelector<HTMLInputElement>('#export-blocked-reference')!;
    reference.value = ' DP 123/2028 ';
    reference.dispatchEvent(new Event('input'));
    await settle();
    layer.querySelector<HTMLButtonElement>('[data-export-send]')!.click();
    const post = await expectRequest((r) => r.url === EXPORT_BLOCKED_URL && r.method === 'POST');
    const body = post.request.body as { reason: string; reference: string; from: string; to: string };
    expect(body.reason).toBe('COURT_ORDER');
    expect(body.reference).toBe('DP 123/2028');
    post.flush({
      from: '2025-09-30T22:00:00Z',
      to: '2025-10-28T23:00:00Z',
      items: [
        activityEvent({
          id: 'old',
          at: '2025-10-10T08:00:00Z',
          actor: { kind: 'MEMBER', name: 'Iago Vello', email: 'iago@naron.example', roles: ['STAFF'], blocked: false },
        }),
      ],
    });
    await flushPage(await activityRequest(), settle);
    await vi.waitFor(async () => {
      await settle();
      expect(document.querySelector('furar-dialog-container')).toBeNull();
    });
    expect(names).toEqual(['rexistro-bloqueados-naron-2025-10-01-2025-10-29.csv']);
    const csv = await csvs[0].text();
    expect(csv).toContain('Data;Persoa;Correo;Rol;Acción;Sobre que;Resultado;Petición');
    expect(csv).toContain('10/10/2025, 10:00;Iago Vello;iago@naron.example;Recepción;Cambiar o nome dunha instalación');
    expect(text(el.querySelector('[data-announcement]'))).toBe('Exportáronse 1 eventos bloqueados.');
    click.mockRestore();
  });

  it('a plataforma non ten «Exportar bloqueados»', async () => {
    const { el, settle } = await renderActivity('/plataforma/rexistro');
    await flushPage(await activityRequest(true), settle);
    expect(el.querySelector('[data-export-blocked]')).toBeNull();
    expect(text(el.querySelector('[data-blocked-note]'))).toBe(
      'Os eventos de hai máis de 1 ano non se mostran: están bloqueados.',
    );
  });
});
