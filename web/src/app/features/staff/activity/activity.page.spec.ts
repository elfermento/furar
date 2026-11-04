// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import {
  EXPORT_BLOCKED_URL,
  activityEvent,
  activityRequest,
  cell,
  flushPage,
  renderActivity,
  row,
  text,
} from '../../../../testing/activity';
import { backend, expectRequest, fakeSession, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { pickOption, selectText } from '../../../../testing/select';
import { MEMBERS, member } from '../../../../testing/staff-members';

describe('Rexistro de actividade (D4-16)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('táboa: caption, cabeceiras, data en Madrid, persoa e rol, acción, sobre que e resultado; axe', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    expect(text(el.querySelector('h1'))).toBe('Rexistro de actividade');
    const table = el.querySelector('table')!;
    expect(text(table.querySelector('caption'))).toBe('Accións rexistradas, da máis nova á máis vella');
    expect([...table.querySelectorAll('thead th')].map((th) => [th.getAttribute('scope'), text(th)])).toEqual([
      ['col', 'Data'],
      ['col', 'Persoa'],
      ['col', 'Acción'],
      ['col', 'Sobre que'],
      ['col', 'Resultado'],
    ]);
    // 17:05 UTC no outono = 18:05 en Madrid; 08:30 UTC no verán = 10:30.
    expect(row(el, 0).querySelector('th[scope="row"] time')?.getAttribute('datetime')).toBe('2026-10-27T17:05:00Z');
    expect(text(row(el, 0).querySelector('th[scope="row"]'))).toBe('27/10/2026, 18:05');
    expect(text(row(el, 1).querySelector('th[scope="row"]'))).toBe('01/07/2026, 10:30');

    expect(cell(el, 0, 'person')).toBe('Ana Rey ana@naron.example Xestión');
    expect(cell(el, 0, 'action')).toBe('Cambiar o nome dunha instalación');
    expect(row(el, 0).querySelector('[data-cell="target"] a')?.getAttribute('href')).toBe(
      '/naron/xestion/instalacions/f1',
    );
    expect(cell(el, 0, 'target')).toBe('Instalación Pavillón de O Alto');
    expect(row(el, 0).querySelector('[data-cell="outcome"] furar-tag')?.getAttribute('data-tone')).toBe('success');
    expect(cell(el, 0, 'outcome')).toBe('Feito');

    // A plataforma, marcada na vista do concello.
    expect(cell(el, 1, 'person')).toBe('Administración da plataforma Plataforma');
    expect(row(el, 1).querySelector('[data-platform]')).not.toBeNull();
    expect(cell(el, 1, 'target')).toBe('Persoal iago@naron.example');
    expect(cell(el, 2, 'person')).toBe('Conta sen rol');
    expect(cell(el, 2, 'target')).toBe('Reserva');
    // A reserva dunha acción que fallou, sen ligazón.
    expect(row(el, 2).querySelector('[data-cell="target"] a')).toBeNull();
    expect(cell(el, 2, 'outcome')).toBe('Fallou');
    expect(row(el, 2).querySelector('furar-tag')?.getAttribute('data-tone')).toBe('danger');
    // Código descoñecido: texto de reserva co código, nunca baleiro.
    expect(cell(el, 3, 'action')).toBe('Outra acción (facility.teleport)');
    expect(cell(el, 3, 'target')).toBe('—');
    // Cada dato leva o seu rótulo para as filas apiladas (320 px).
    expect([...row(el, 0).querySelectorAll('[data-label]')].map((c) => c.getAttribute('data-label'))).toEqual([
      'Data',
      'Persoa',
      'Acción',
      'Sobre que',
      'Resultado',
    ]);
    expect(text(el.querySelector('[role="status"]'))).toBe('4 accións á vista.');
    await expectNoAxeViolations(el);
  });

  it('en castelán: os mesmos datos cos seus textos; axe', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    TestBed.inject(TranslocoService).setActiveLang('es');
    await settle();
    expect(text(el.querySelector('h1'))).toBe('Registro de actividad');
    expect(cell(el, 0, 'action')).toBe('Cambiar el nombre de una instalación');
    expect(cell(el, 1, 'person')).toBe('Administración de la plataforma Plataforma');
    expect(cell(el, 3, 'action')).toBe('Otra acción (facility.teleport)');
    expect(cell(el, 2, 'outcome')).toBe('Falló');
    expect(text(row(el, 0).querySelector('th[scope="row"]'))).toBe('27/10/2026, 18:05');
    await expectNoAxeViolations(el);
  });

  it('filtro de persoa: a anonimizada como «Persoa anonimizada», sen o correo técnico', async () => {
    const anonymized = member({
      id: 'm7',
      email: undefined,
      anonymized: true,
      name: undefined,
      role: 'STAFF',
      status: 'DEACTIVATED',
      deactivatedAt: '2026-10-20T09:00:00Z',
    });
    const { el, settle } = await renderActivity('/naron/xestion/rexistro?persoa=m7', fakeSession(), [
      ...MEMBERS,
      anonymized,
    ]);
    await flushPage(await activityRequest(), settle, []);
    expect(selectText(el.querySelector('[data-person]')!)).toBe('Persoa anonimizada (desactivada)');
  });

  it('URL → filtros: o período (medianoites de Madrid, fin excluínte), a área, a persoa e só fallidos', async () => {
    const { el, settle } = await renderActivity(
      '/naron/xestion/rexistro?desde=2026-10-01&ata=2026-10-28&area=staff&persoa=m1&fallidos=1',
    );
    const request = await activityRequest();
    expect(Object.fromEntries(request.request.params.keys().map((k) => [k, request.request.params.get(k)]))).toEqual({
      from: '2026-09-30T22:00:00.000Z',
      to: '2026-10-28T23:00:00.000Z',
      area: 'staff',
      memberId: 'm1',
      outcome: 'FAILURE',
    });
    await flushPage(request, settle, []);
    expect(text(el.querySelector('[data-period] .f-field-popover__value'))).toBe('01/10/2026 – 28/10/2026');
    expect(selectText(el.querySelector('[data-area]')!)).toBe('Persoal');
    expect(selectText(el.querySelector('[data-person]')!)).toBe('Ana Rey');
    expect(el.querySelector('[data-failed]')?.getAttribute('aria-pressed')).toBe('true');
    // Baleiro cos filtros postos: dío e convida a quitalos.
    expect(text(el.querySelector('[data-activity-empty]'))).toContain('Non hai accións con estes filtros');
  });

  it('filtros → URL: «Só fallidos» e «Quitar os filtros» cambian o URL e volven pedir', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    expect(el.querySelector('[data-reset]')).toBeNull();
    el.querySelector<HTMLButtonElement>('[data-failed]')!.click();
    const failed = await activityRequest();
    expect(failed.request.params.get('outcome')).toBe('FAILURE');
    expect(TestBed.inject(Router).url).toBe('/naron/xestion/rexistro?fallidos=1');
    await flushPage(failed, settle, [activityEvent({ outcome: 'FAILURE' })]);
    el.querySelector<HTMLButtonElement>('[data-reset]')!.click();
    const all = await activityRequest();
    expect(all.request.params.keys()).toEqual([]);
    expect(TestBed.inject(Router).url).toBe('/naron/xestion/rexistro');
    await flushPage(all, settle);
  });

  it('«Cargar máis»: a páxina seguinte co cursor, o foco na primeira fila nova e o anuncio', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, [activityEvent()], 'cursor-2');
    const more = el.querySelector<HTMLButtonElement>('[data-more]')!;
    more.focus();
    more.click();
    const next = await activityRequest();
    expect(next.request.params.get('cursor')).toBe('cursor-2');
    await flushPage(next, settle, [activityEvent({ id: 'e5' }), activityEvent({ id: 'e6' })]);
    await vi.waitFor(() => expect(document.activeElement).toBe(row(el, 1)));
    expect(row(el, 1).getAttribute('tabindex')).toBe('-1');
    expect(el.querySelectorAll('tbody tr')).toHaveLength(3);
    // Sen máis páxinas, o botón vaise e dío.
    expect(el.querySelector('[data-more]')).toBeNull();
    expect(text(el.querySelector('[role="status"]'))).toBe('Xa non hai máis accións neste período.');
  });

  it('baleiro sen filtros; erro con «Tentar de novo»; un período que non vale, co seu texto', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, []);
    expect(text(el.querySelector('[data-activity-empty]'))).toContain('Aínda non hai nada rexistrado neste período');
    expect(el.querySelector('table')).toBeNull();

    await TestBed.inject(Router).navigateByUrl('/naron/xestion/rexistro?area=booking');
    (await activityRequest()).flush('x', { status: 500, statusText: 'x' });
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[data-load-error]'))).toContain('Non se puido cargar o rexistro');
    });
    el.querySelector<HTMLButtonElement>('[data-load-error] button')!.click();
    await flushPage(await activityRequest(), settle);
    expect(el.querySelectorAll('tbody tr')).toHaveLength(4);

    await TestBed.inject(Router).navigateByUrl('/naron/xestion/rexistro?desde=2026-01-01&ata=2026-01-31');
    flushProblem(await activityRequest(), 400, 'audit.range-invalid', { params: { maxDays: 366 } });
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[role="alert"]'))).toContain(
        'O período ten que comezar antes de rematar e durar como moito 366 días.',
      );
    });
    expect(el.querySelector('[data-load-error]')).toBeNull();
  });

  it('a plataforma: o seu rexistro, só o período e a columna do concello; axe', async () => {
    const { el, settle } = await renderActivity('/plataforma/rexistro', fakeSession({ platform: true }));
    const request = await activityRequest(true);
    expect(request.request.params.keys()).toEqual([]);
    await flushPage(request, settle, [
      activityEvent({
        actor: { kind: 'PLATFORM', roles: ['PLATFORM_ADMIN'], blocked: false },
        action: 'municipality.import',
        target: { kind: 'MUNICIPALITY', id: 'x' },
      }),
    ]);
    expect(text(el.querySelector('h1'))).toBe('Rexistro da plataforma');
    expect(el.querySelector('[data-area]')).toBeNull();
    expect(el.querySelector('[data-person]')).toBeNull();
    expect(el.querySelector('[data-failed]')).toBeNull();
    expect([...el.querySelectorAll('thead th')].map(text)).toContain('Concello');
    expect(cell(el, 0, 'municipality')).toBe('naron');
    expect(cell(el, 0, 'target')).toBe('Concello naron');
    // Na súa propia vista non se marca «Plataforma»: todo é dela.
    expect(row(el, 0).querySelector('[data-platform]')).toBeNull();
    expect(row(el, 0).querySelector('[data-cell="target"] a')).toBeNull();
    await expectNoAxeViolations(el);
  });

  it('a reserva leva a «Reservas» co seu detalle aberto; na plataforma, sen ligazón', async () => {
    const booking = activityEvent({ action: 'booking.cancel', target: { kind: 'BOOKING', id: 'b1' } });
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, [booking]);
    const link = row(el, 0).querySelector('[data-cell="target"] a[data-target-link]');
    expect(link?.getAttribute('href')).toBe('/naron/xestion/reservas?reserva=b1');
    expect(text(link)).toBe('Reserva');
    expect(cell(el, 0, 'target')).toBe('Reserva');
    await expectNoAxeViolations(el);
  });

  it('a plataforma non ve reservas: o seu rexistro non as liga', async () => {
    const { el, settle } = await renderActivity('/plataforma/rexistro', fakeSession({ platform: true }));
    await flushPage(await activityRequest(true), settle, [
      activityEvent({
        actor: { kind: 'PLATFORM', roles: ['PLATFORM_ADMIN'], blocked: false },
        action: 'booking.cancel',
        target: { kind: 'BOOKING', id: 'b1' },
      }),
    ]);
    expect(cell(el, 0, 'target')).toBe('Reserva');
    expect(row(el, 0).querySelector('[data-cell="target"] a')).toBeNull();
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
    // Sen motivo nin referencia: dous erros, cada un no seu campo.
    layer.querySelector<HTMLButtonElement>('[data-export-send]')!.click();
    await settle();
    expect(text(layer.querySelector('furar-form-errors-summary'))).toContain('2 erro');
    const reference = layer.querySelector<HTMLInputElement>('#export-blocked-reference')!;
    expect(reference.getAttribute('aria-invalid')).toBe('true');
    expect(reference.getAttribute('aria-describedby')).toBe(
      'export-blocked-reference-help export-blocked-reference-error',
    );
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
    expect(body.from < body.to).toBe(true);
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
    // Despois recarga a lista, que xa leva a exportación.
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

  it('persoa con identidade bloqueada: «Persoa desactivada», sen nome nin correo', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, [
      activityEvent({ actor: { kind: 'MEMBER', roles: ['STAFF'], blocked: true } }),
    ]);
    expect(cell(el, 0, 'person')).toBe('Persoa desactivada Recepción');
  });

  it('a plataforma le o rexistro do concello (ADR-0028), sen «Exportar bloqueados» nin ligazóns ás reservas', async () => {
    const { el, settle } = await renderActivity('/naron/xestion/rexistro', fakeSession({ platform: true }));
    await flushPage(await activityRequest(), settle, [
      activityEvent({ target: { kind: 'BOOKING', id: 'b1' }, outcome: 'SUCCESS' }),
    ]);
    expect(el.querySelector('[data-export-blocked]')).toBeNull();
    expect(cell(el, 0, 'target')).toBe('Reserva');
    expect(row(el, 0).querySelector('[data-cell="target"] a')).toBeNull();
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
