// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import {
  activityEvent,
  activityRequest,
  cell,
  columnPanel,
  flushPage,
  openColumn,
  renderActivity,
  row,
  text,
} from '../../../../testing/activity';
import { backend, fakeSession, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { MEMBERS, member } from '../../../../testing/staff-members';

const params = (request: { request: { params: { keys(): string[]; getAll(k: string): string[] | null } } }) =>
  Object.fromEntries(request.request.params.keys().map((k) => [k, request.request.params.getAll(k)]));

/** Tres accións seguidas da xestora na mesma instalación: crear, cambiar a tempada e publicar. */
const SESSION_EVENTS = [
  activityEvent({
    id: 'g3',
    at: '2026-11-10T11:31:00Z',
    action: 'facility.publish',
    facilityId: 'f1',
    requestId: 'r3',
  }),
  activityEvent({
    id: 'g2',
    at: '2026-11-10T11:29:00Z',
    action: 'facility.season-update',
    facilityId: 'f1',
    label: 'Inverno',
    requestId: 'r2',
    changes: [{ field: 'season.endDate', before: '2027-06-30', after: '2027-06-15', hidden: false }],
  }),
  activityEvent({ id: 'g1', at: '2026-11-10T11:14:00Z', action: 'facility.create', facilityId: 'f1', requestId: 'r1' }),
];

describe('Rexistro de actividade', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('táboa densa: unha cousa por columna, filtros nas cabeceiras e resumo curto; axe', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    expect(text(el.querySelector('h1'))).toBe('Rexistro de actividade');
    const table = el.querySelector('table')!;
    expect(text(table.querySelector('caption'))).toBe('Accións rexistradas, da máis nova á máis vella');
    expect([...table.querySelectorAll('thead th')].map(text)).toEqual([
      'Data',
      'Persoa',
      'Rol',
      'Acción',
      'Onde',
      'Resultado',
    ]);
    // Cada cabeceira é un botón que abre un panel.
    expect(table.querySelector('thead [data-filter="action"] button')?.getAttribute('aria-haspopup')).toBe('dialog');
    // 17:05 UTC no outono = 18:05 en Madrid; 08:30 UTC no verán = 10:30.
    expect(row(el, 0).querySelector('time')?.getAttribute('datetime')).toBe('2026-10-27T17:05:00Z');
    // Só o día (a hora vai no detalle); a persoa polo correo; o rol nun chip do seu ton.
    expect(cell(el, 0, 'date')).toBe('27/10/2026');
    expect(cell(el, 1, 'date')).toBe('01/07/2026');
    expect(cell(el, 0, 'person')).toBe('ana@naron.example');
    expect(cell(el, 0, 'role')).toBe('Xestión');
    expect(row(el, 0).querySelector('[data-cell="role"] furar-tag')?.getAttribute('data-tone')).toBe('info');
    expect(cell(el, 0, 'action')).toBe('Cambiou o nome dunha instalación');
    expect(cell(el, 0, 'place')).toBe('Pavillón de O Alto');
    expect(row(el, 0).querySelector('[data-cell="outcome"] furar-tag')?.getAttribute('data-tone')).toBe('success');
    expect(cell(el, 0, 'outcome')).toBe('Feito');
    // A plataforma, co seu rol; o persoal, en «Onde».
    expect(cell(el, 1, 'person')).toBe('Administración da plataforma');
    expect(cell(el, 1, 'role')).toBe('Plataforma');
    expect(row(el, 1).querySelector('[data-cell="role"] furar-tag')?.getAttribute('data-tone')).toBe('primary');
    expect(cell(el, 1, 'action')).toBe('Invitou a unha persoa');
    expect(cell(el, 1, 'place')).toBe('Persoal');
    // Fallou: «Intentou …», en letra forte e co ton de perigo.
    expect(cell(el, 2, 'person')).toBe('Conta sen rol');
    expect(cell(el, 2, 'role')).toBe('—');
    expect(row(el, 2).querySelector('[data-cell="role"] furar-tag')).toBeNull();
    expect(cell(el, 2, 'action')).toBe('Intentou cancelar unha reserva');
    expect(row(el, 2).getAttribute('data-outcome')).toBe('FAILURE');
    expect(row(el, 2).querySelector('furar-tag')?.getAttribute('data-tone')).toBe('danger');
    // Código descoñecido: texto de reserva co código, nunca baleiro.
    expect(cell(el, 3, 'action')).toBe('Outra acción (facility.teleport)');
    expect(text(el.querySelector('[role="status"]'))).toBe('4 accións á vista.');
    // O prazo de conservación, discreto e debaixo da táboa.
    const note = el.querySelector('[data-blocked-note]')!;
    expect(table.compareDocumentPosition(note) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(note.classList).toContain('f-help');
    await expectNoAxeViolations(el);
  });

  it('en castelán: os mesmos datos cos seus textos', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    TestBed.inject(TranslocoService).setActiveLang('es');
    await settle();
    expect(text(el.querySelector('h1'))).toBe('Registro de actividad');
    expect(cell(el, 0, 'action')).toBe('Cambió el nombre de una instalación');
    expect(cell(el, 2, 'action')).toBe('Intentó cancelar una reserva');
    expect(cell(el, 3, 'action')).toBe('Otra acción (facility.teleport)');
    expect(cell(el, 2, 'outcome')).toBe('Falló');
    expect(el.querySelector<HTMLInputElement>('[data-search]')?.placeholder).toBe('Buscar en el registro');
  });

  it('URL → API: período, persoas, rol, acción, instalación, e resultado; chips e puntos', async () => {
    const { el, settle } = await renderActivity(
      '/naron/xestion/rexistro?desde=2026-10-01&ata=2026-10-28&persoa=m1&rol=STAFF&accion=booking.cancel' +
        '&onde=f1&resultado=fallou',
    );
    const request = await activityRequest();
    expect(params(request)).toEqual({
      from: ['2026-09-30T22:00:00.000Z'],
      to: ['2026-10-28T23:00:00.000Z'],
      memberId: ['m1'],
      role: ['STAFF'],
      action: ['booking.cancel'],
      facilityId: ['f1'],
      outcome: ['FAILURE'],
    });
    await flushPage(request, settle, []);
    expect([...el.querySelectorAll('[data-active-filters] .f-chip__label')].map(text)).toEqual([
      'Data: 01/10/2026 – 28/10/2026',
      'Persoa: ana@naron.example',
      'Rol: Recepción',
      'Acción: Cancelar unha reserva',
      'Onde: Pavillón de O Alto',
      'Resultado: Fallou',
    ]);
    // Punto na cabeceira e «, con filtro» no nome.
    const header = el.querySelector('thead [data-filter="role"] button')!;
    expect(header.querySelector('.f-field-popover__dot')).not.toBeNull();
    expect(header.getAttribute('aria-label')).toBe('Rol, con filtro');
    // Baleiro cos filtros postos: dío e convida a quitalos; as cabeceiras seguen.
    expect(text(el.querySelector('[data-activity-empty]'))).toContain('Non hai accións con estes filtros');
    expect(el.querySelector('thead [data-filter="date"]')).not.toBeNull();
  });

  it('filtro Rol: cada opción no mesmo chip do rol ca na táboa', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    await openColumn(el, 'role', settle);
    const tags = [...columnPanel('Rol')!.querySelectorAll('[data-option] furar-tag')];
    expect(tags.map((tag) => [text(tag), tag.getAttribute('data-tone'), tag.getAttribute('data-look')])).toEqual([
      ['Xestión', 'info', 'solid'],
      ['Recepción', 'warn', 'solid'],
      ['Plataforma', 'primary', 'solid'],
    ]);
  });

  it('filtro da cabeceira: opcións coas contas, busca, escoller suma ao URL e «Borrar filtros» quita todo; axe', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    await openColumn(el, 'action', settle);
    const panel = columnPanel('Acción')!;
    const option = (label: string) =>
      [...panel.querySelectorAll<HTMLButtonElement>('[data-option]')].find((o) => text(o)?.startsWith(label));
    // As que hai na páxina, primeiro e coa súa conta.
    expect(text(option('Cancelar unha reserva'))).toBe('Cancelar unha reserva 1');
    const search = panel.querySelector<HTMLInputElement>('[data-column-search]')!;
    search.value = 'cancelar unha res';
    search.dispatchEvent(new Event('input'));
    await settle();
    expect([...panel.querySelectorAll('[data-option]')].map((o) => o.getAttribute('data-option'))).toEqual([
      'booking.cancel',
    ]);
    await expectNoAxeViolations(panel);
    option('Cancelar unha reserva')!.click();
    const filtered = await activityRequest();
    expect(filtered.request.params.getAll('action')).toEqual(['booking.cancel']);
    expect(TestBed.inject(Router).url).toBe('/naron/xestion/rexistro?accion=booking.cancel');
    await flushPage(filtered, settle, [activityEvent({ action: 'booking.cancel', outcome: 'FAILURE' })]);
    expect(text(el.querySelector('[data-active-filters] .f-chip__label'))).toBe('Acción: Cancelar unha reserva');
    el.querySelector<HTMLButtonElement>('[data-clear-filters]')!.click();
    const all = await activityRequest();
    expect(all.request.params.keys()).toEqual([]);
    expect(TestBed.inject(Router).url).toBe('/naron/xestion/rexistro');
    await flushPage(all, settle);
  });

  it('data: un atallo («Onte») é o período dun día e pecha o panel', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle);
    await openColumn(el, 'date', settle);
    const panel = columnPanel('Data')!;
    expect(panel.querySelector('[data-option=""]')?.getAttribute('aria-pressed')).toBe('true');
    expect(panel.querySelector('[data-period-from]')).not.toBeNull();
    panel.querySelector<HTMLButtonElement>('[data-option="yesterday"]')!.click();
    const request = await activityRequest();
    const url = TestBed.inject(Router).url;
    expect(url).toMatch(/^\/naron\/xestion\/rexistro\?desde=\d{4}-\d{2}-\d{2}&ata=\d{4}-\d{2}-\d{2}$/);
    const [from, to] = [...url.matchAll(/\d{4}-\d{2}-\d{2}/g)].map((m) => m[0]);
    expect(from).toBe(to);
    await flushPage(request, settle, []);
    expect(columnPanel('Data')).toBeNull();
    expect(text(el.querySelector('[data-active-filters] .f-chip__label'))).toBe('Data: Onte');
  });

  it('a busca filtra as filas cargadas e vai ao URL, sen pedir á API', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { el, settle } = await renderActivity();
      await flushPage(await activityRequest(), settle);
      const search = el.querySelector<HTMLInputElement>('[data-search]')!;
      search.value = 'conta sen';
      search.dispatchEvent(new Event('input'));
      await vi.advanceTimersByTimeAsync(300);
      await settle();
      expect(TestBed.inject(Router).url).toBe('/naron/xestion/rexistro?buscar=conta%20sen');
      expect(el.querySelectorAll('tbody tr')).toHaveLength(1);
      expect(cell(el, 0, 'person')).toBe('Conta sen rol');
    } finally {
      vi.useRealTimers();
    }
  });

  it('detalle: premer a fila abre a capa coa persoa, o rol, a petición e o que cambiou; axe', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, [
      activityEvent({
        action: 'facility.season-update',
        facilityId: 'f1',
        label: 'Inverno',
        requestId: '9c41e07b',
        changes: [
          { field: 'season.endDate', before: '2027-06-30', after: '2027-06-15', hidden: false },
          { field: 'season.startDate', after: '2026-09-01', hidden: false },
          { field: 'season.day.MONDAY', before: '09:00–21:00', after: '08:00–22:00', hidden: false },
          { field: 'facility.phone', hidden: true },
          { field: 'facility.teleport', before: 'a', after: 'b', hidden: false },
        ],
      }),
    ]);
    expect(cell(el, 0, 'action')).toBe('Cambiou a tempada «Inverno»');
    row(el, 0).querySelector<HTMLElement>('[data-cell="date"]')!.click();
    await settle();
    const layer = document.querySelector<HTMLElement>('furar-dialog-container')!;
    expect(text(layer.querySelector('h2'))).toBe('Cambiou a tempada «Inverno»');
    const meta = Object.fromEntries(
      [...layer.querySelectorAll('dl > div')].map((d) => [text(d.querySelector('dt')), text(d.querySelector('dd'))]),
    );
    expect(meta).toEqual({
      Persoa: 'Ana Rey ana@naron.example',
      Rol: 'Xestión',
      Data: '27/10/2026',
      Hora: '18:05',
      Onde: 'Pavillón de O Alto',
      Resultado: 'Feito',
      Petición: '9c41e07b',
    });
    expect(layer.querySelector('dd a')?.getAttribute('href')).toBe('/naron/xestion/instalacions/f1');
    expect(layer.querySelector('furar-dialog')?.getAttribute('data-size')).toBe('lg');
    // O mesmo chip do rol ca na táboa: recheo do ton.
    const role = layer.querySelector('[data-detail-roles] furar-tag');
    expect(role?.getAttribute('data-look')).toBe('solid');
    expect(role?.getAttribute('data-tone')).toBe('info');
    expect([...layer.querySelectorAll('[data-diff], [data-hidden]')].map(text)).toEqual([
      'Ata 30/06/2027 pasa a 15/06/2027',
      'Desde — pasa a 01/09/2026',
      'Luns 09:00–21:00 pasa a 08:00–22:00',
      'Teléfono cambiou',
      'Outro dato a pasa a b',
    ]);
    // Antes, riscado; sen valor antes, «—» sen riscar; o dato persoal, sen valores.
    expect(layer.querySelectorAll('[data-diff] del')).toHaveLength(3);
    expect(text(layer.querySelectorAll('[data-diff]')[1].querySelector('.f-activity-detail__empty'))).toBe('—');
    expect(layer.querySelector('[data-hidden] del, [data-hidden] ins')).toBeNull();
    await expectNoAxeViolations(layer);
  });

  it('un evento de antes, sen cambios, só leva o resumo; o resumo é un botón (teclado)', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, [activityEvent()]);
    const button = row(el, 0).querySelector<HTMLButtonElement>('[data-open]')!;
    expect(button.getAttribute('aria-haspopup')).toBe('dialog');
    button.click();
    await settle();
    const layer = document.querySelector<HTMLElement>('furar-dialog-container')!;
    expect(text(layer.querySelector('[data-no-changes]'))).toBe('Sen cambios que mostrar.');
  });

  it('agrupado: accións seguidas da mesma persoa na mesma instalación, nunha fila e no detalle', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, [
      ...SESSION_EVENTS,
      // Máis de 30 minutos antes: fila á parte.
      activityEvent({ id: 'old', at: '2026-11-10T10:30:00Z', action: 'facility.details', facilityId: 'f1' }),
    ]);
    expect(el.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(cell(el, 0, 'action')).toBe('Creou e publicou «Pavillón de O Alto» · 3 cambios');
    expect(cell(el, 1, 'action')).toBe('Cambiou os datos');
    row(el, 0).querySelector<HTMLButtonElement>('[data-open]')!.click();
    await settle();
    const layer = document.querySelector<HTMLElement>('furar-dialog-container')!;
    expect(text(layer.querySelector('h3'))).toBe('3 cambios');
    expect([...layer.querySelectorAll('[data-detail-item]')].map(text)).toEqual([
      '12:31 Publicou unha instalación r3',
      '12:29 Cambiou a tempada «Inverno» r2 Ata 30/06/2027 pasa a 15/06/2027',
      '12:14 Creou unha instalación r1',
    ]);
    // A hora, a franxa do grupo.
    const meta = Object.fromEntries(
      [...layer.querySelectorAll('dl > div')].map((d) => [text(d.querySelector('dt')), text(d.querySelector('dd'))]),
    );
    expect(meta['Hora']).toBe('12:14–12:31');
    expect(layer.querySelector('[data-request]')).toBeNull();
  });

  it('«Cargar máis»: a páxina seguinte co cursor, o foco na primeira fila nova e o anuncio', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, [activityEvent()], 'cursor-2');
    const more = el.querySelector<HTMLButtonElement>('[data-more]')!;
    more.focus();
    more.click();
    const next = await activityRequest();
    expect(next.request.params.get('cursor')).toBe('cursor-2');
    await flushPage(next, settle, [
      activityEvent({ id: 'e5', action: 'staff.sign-in' }),
      activityEvent({ id: 'e6', action: 'staff.sign-in' }),
    ]);
    await vi.waitFor(() => expect(document.activeElement).toBe(row(el, 1).querySelector('[data-open]')));
    expect(el.querySelectorAll('tbody tr')).toHaveLength(3);
    expect(el.querySelector('[data-more]')).toBeNull();
    expect(text(el.querySelector('[role="status"]'))).toBe('Xa non hai máis accións neste período.');
  });

  it('baleiro sen filtros; erro con «Tentar de novo»; un período que non vale, co seu texto', async () => {
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, []);
    expect(text(el.querySelector('[data-activity-empty]'))).toContain('Aínda non hai nada rexistrado neste período');

    await TestBed.inject(Router).navigateByUrl('/naron/xestion/rexistro?resultado=fallou');
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

  it('a plataforma: só o período vai á API; persoa e concello sen filtro; o resto, nas filas', async () => {
    const platformEvent = (id: string, outcome: 'SUCCESS' | 'FAILURE') =>
      activityEvent({
        id,
        outcome,
        actor: { kind: 'PLATFORM', roles: ['PLATFORM_ADMIN'], blocked: false },
        action: 'municipality.import',
        target: { kind: 'MUNICIPALITY', id: 'x' },
      });
    const { el, settle } = await renderActivity(
      '/plataforma/rexistro?resultado=fallou',
      fakeSession({ platform: true }),
    );
    const request = await activityRequest(true);
    expect(request.request.params.keys()).toEqual([]);
    await flushPage(request, settle, [platformEvent('p1', 'SUCCESS'), platformEvent('p2', 'FAILURE')]);
    expect(text(el.querySelector('h1'))).toBe('Rexistro da plataforma');
    expect([...el.querySelectorAll('thead th')].map(text)).toEqual([
      'Data',
      'Persoa',
      'Rol',
      'Acción',
      'Concello',
      'Resultado',
    ]);
    expect(el.querySelector('thead [data-filter="person"]')).toBeNull();
    expect(el.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(cell(el, 0, 'place')).toBe('naron');
    expect(cell(el, 0, 'action')).toBe('Intentou importar os datos dun concello');
    await expectNoAxeViolations(el);
  });

  it('a reserva leva a «Reservas» co seu detalle aberto; a plataforma, no concello, sen ligazón nin exportar', async () => {
    const booking = activityEvent({ action: 'booking.cancel', target: { kind: 'BOOKING', id: 'b1' } });
    const { el, settle } = await renderActivity();
    await flushPage(await activityRequest(), settle, [booking]);
    expect(cell(el, 0, 'place')).toBe('Reservas');
    row(el, 0).querySelector<HTMLButtonElement>('[data-open]')!.click();
    await settle();
    expect(document.querySelector('furar-dialog-container dd a')?.getAttribute('href')).toBe(
      '/naron/xestion/reservas?reserva=b1',
    );
  });

  it('a plataforma le o rexistro do concello (ADR-0028), sen «Exportar bloqueados» nin ligazóns ás reservas', async () => {
    const { el, settle } = await renderActivity('/naron/xestion/rexistro', fakeSession({ platform: true }));
    await flushPage(await activityRequest(), settle, [
      activityEvent({ target: { kind: 'BOOKING', id: 'b1' }, action: 'booking.cancel' }),
    ]);
    expect(el.querySelector('[data-export-blocked]')).toBeNull();
    row(el, 0).querySelector<HTMLButtonElement>('[data-open]')!.click();
    await settle();
    expect(document.querySelector('furar-dialog-container dd a')).toBeNull();
  });

  it('persoa con identidade bloqueada: «Persoa desactivada»; anonimizada no filtro de persoa', async () => {
    const anonymized = member({
      id: 'm7',
      email: undefined,
      anonymized: true,
      name: undefined,
      role: 'STAFF',
      status: 'DEACTIVATED',
      deactivatedAt: '2026-10-20T09:00:00Z',
    });
    const { el, settle } = await renderActivity('/naron/xestion/rexistro', fakeSession(), [...MEMBERS, anonymized]);
    await flushPage(await activityRequest(), settle, [
      activityEvent({ actor: { kind: 'MEMBER', roles: ['STAFF'], blocked: true } }),
    ]);
    expect(cell(el, 0, 'person')).toBe('Persoa desactivada');
    expect(cell(el, 0, 'role')).toBe('Recepción');
    await openColumn(el, 'person', settle);
    expect(text(columnPanel('Persoa')!.querySelector('[data-option="m7"]'))).toBe('Persoa anonimizada (desactivada) 0');
  });
});
