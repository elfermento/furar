// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import es from '../../../../i18n/es.json';
import gl from '../../../../i18n/gl.json';
import type { ActivityEventResponse } from './activity';
import { blockedDays, firstVisibleDay, periodLabel, retentionPeriods, validReference } from './activity-retention';
import {
  KNOWN_ACTIONS,
  NO_FILTERS,
  actionKey,
  actorView,
  apiParams,
  filtersFromParams,
  filtersToParams,
  madridMidnight,
  targetView,
} from './activity';

/** O texto dunha clave con puntos nun JSON aniñado. */
const lookup = (json: unknown, key: string): unknown =>
  key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], json);

function event(overrides: Partial<ActivityEventResponse> = {}): ActivityEventResponse {
  return {
    id: 'e1',
    at: '2026-10-27T17:05:00Z',
    action: 'facility.rename',
    outcome: 'SUCCESS',
    actor: { kind: 'MEMBER', name: 'Ana Rey', email: 'ana@naron.example', roles: ['MANAGER'], blocked: false },
    ...overrides,
  };
}

describe('rexistro de actividade: filtros no URL', () => {
  it('URL → filtros → URL, sen os valores por defecto', () => {
    const params = { desde: '2026-10-01', ata: '2026-10-28', area: 'staff', persoa: 'm1', fallidos: '1' };
    const filters = filtersFromParams(params, 'municipality');
    expect(filters).toEqual({ from: '2026-10-01', to: '2026-10-28', area: 'staff', memberId: 'm1', failedOnly: true });
    expect(filtersToParams(filters)).toEqual(params);
    expect(filtersToParams(NO_FILTERS)).toEqual({ desde: null, ata: null, area: null, persoa: null, fallidos: null });
  });

  it('o que non vale cae no valor por defecto: datas ao revés, de máis de 365 días, área descoñecida', () => {
    expect(filtersFromParams({ desde: '2026-10-28', ata: '2026-10-01' }, 'municipality')).toEqual(NO_FILTERS);
    expect(filtersFromParams({ desde: '2025-10-28', ata: '2026-10-28' }, 'municipality').from).toBeNull();
    expect(filtersFromParams({ desde: '2025-10-29', ata: '2026-10-28' }, 'municipality').from).toBe('2025-10-29');
    expect(filtersFromParams({ desde: '2026-13-01', ata: '2026-13-02' }, 'municipality').from).toBeNull();
    expect(filtersFromParams({ area: 'teleport', fallidos: 'si' }, 'municipality')).toEqual(NO_FILTERS);
  });

  it('a plataforma só filtra polo período', () => {
    expect(
      filtersFromParams({ desde: '2026-10-01', ata: '2026-10-02', area: 'staff', fallidos: '1' }, 'platform'),
    ).toEqual({ ...NO_FILTERS, from: '2026-10-01', to: '2026-10-02' });
  });
});

describe('rexistro de actividade: datas en Europe/Madrid', () => {
  it('medianoite de Madrid como instante: UTC+1 no inverno, UTC+2 no verán e nos días do cambio', () => {
    expect(madridMidnight('2026-01-15')).toBe('2026-01-14T23:00:00.000Z');
    expect(madridMidnight('2026-07-01')).toBe('2026-06-30T22:00:00.000Z');
    // 29 de marzo e 25 de outubro de 2026: a hora cambia ás 2 e ás 3, despois da medianoite.
    expect(madridMidnight('2026-03-29')).toBe('2026-03-28T23:00:00.000Z');
    expect(madridMidnight('2026-10-25')).toBe('2026-10-24T22:00:00.000Z');
  });

  it('a API recibe o fin excluínte (a medianoite do día seguinte) e o resto dos filtros', () => {
    const filters = filtersFromParams(
      { desde: '2026-10-01', ata: '2026-10-28', area: 'booking', persoa: 'm1', fallidos: '1' },
      'municipality',
    );
    expect(apiParams(filters)).toEqual({
      from: '2026-09-30T22:00:00.000Z',
      to: '2026-10-28T23:00:00.000Z',
      area: 'booking',
      memberId: 'm1',
      outcome: 'FAILURE',
    });
    // Sen período, o da API (o último ano ata agora).
    expect(apiParams(NO_FILTERS)).toEqual({});
  });
});

describe('rexistro de actividade: textos', () => {
  it('todos os códigos de hoxe teñen texto en gl e es', () => {
    expect(KNOWN_ACTIONS).toHaveLength(53);
    for (const code of KNOWN_ACTIONS) {
      const key = actionKey(code);
      expect(key, code).toBe(`activity.actions.${code}`);
      for (const json of [gl, es]) expect(lookup(json, key ?? ''), code).toEqual(expect.stringMatching(/\S/));
    }
  });

  it('desactivar e reactivar; os «staff.revoke» xa gravados len como «Desactivar»', () => {
    expect(lookup(gl, actionKey('staff.deactivate') ?? '')).toBe('Desactivar a unha persoa');
    expect(lookup(gl, actionKey('staff.reactivate') ?? '')).toBe('Reactivar a unha persoa');
    expect(lookup(gl, actionKey('staff.revoke') ?? '')).toBe('Desactivar a unha persoa');
    expect(lookup(es, actionKey('staff.revoke') ?? '')).toBe('Desactivar a una persona');
  });

  it('un código descoñecido non ten clave: vai o texto de reserva, nunca baleiro', () => {
    expect(actionKey('facility.teleport')).toBeNull();
    expect(actionKey('')).toBeNull();
    expect(lookup(gl, 'activity.unknownAction')).toBe('Outra acción ({{code}})');
    expect(lookup(es, 'activity.unknownAction')).toBe('Otra acción ({{code}})');
  });

  it('persoa e rol: nome e correo; plataforma; conta sen rol; persoa anonimizada', () => {
    expect(actorView(event().actor)).toEqual({
      name: 'Ana Rey',
      nameKey: 'activity.actor.anonymized',
      email: 'ana@naron.example',
      roleKeys: ['staff.members.role.MANAGER'],
      platform: false,
    });
    expect(actorView({ kind: 'PLATFORM', roles: ['PLATFORM_ADMIN'], blocked: false })).toMatchObject({
      name: null,
      nameKey: 'activity.actor.platform',
      platform: true,
    });
    expect(actorView({ kind: 'OTHER', roles: [], blocked: false })).toMatchObject({
      name: null,
      nameKey: 'activity.actor.other',
    });
    expect(actorView({ kind: 'SYSTEM', roles: [], blocked: false })).toMatchObject({
      name: null,
      nameKey: 'activity.actor.system',
      roleKeys: [],
      platform: false,
    });
    expect(actorView({ kind: 'MEMBER', roles: ['STAFF'], blocked: false })).toMatchObject({
      name: null,
      nameKey: 'activity.actor.anonymized',
      roleKeys: ['staff.members.role.STAFF'],
    });
    // Sen nome, o correo fai de nome (e non se repite debaixo).
    expect(actorView({ kind: 'MEMBER', email: 'iago@naron.example', roles: [], blocked: false })).toMatchObject({
      name: 'iago@naron.example',
      email: null,
    });
  });

  it('sobre que: a instalación co seu nome e ligazón só no concello; o resto, o tipo', () => {
    const facilities = new Map([['f1', 'Pavillón de O Alto']]);
    const facility = event({ target: { kind: 'FACILITY', id: 'f1' } });
    expect(targetView(facility, 'municipality', 'naron', facilities)).toEqual({
      kindKey: 'activity.target.FACILITY',
      text: 'Pavillón de O Alto',
      link: ['/', 'naron', 'xestion', 'instalacions', 'f1'],
    });
    expect(targetView(facility, 'platform', null, facilities).link).toBeNull();
    // Eliminada (ou fóra da lista): sen nome nin ligazón.
    expect(targetView(event({ target: { kind: 'FACILITY', id: 'f9' } }), 'municipality', 'naron', facilities)).toEqual({
      kindKey: 'activity.target.FACILITY',
      text: null,
      link: null,
    });
    expect(
      targetView(
        event({ target: { kind: 'STAFF_MEMBER', id: 'm2', memberEmail: 'iago@naron.example' } }),
        'municipality',
        'naron',
        facilities,
      ).text,
    ).toBe('iago@naron.example');
    expect(targetView(event({ target: undefined }), 'municipality', 'naron', facilities).kindKey).toBe(
      'activity.target.none',
    );
  });

  it('a reserva leva a «Reservas» co seu detalle, só no concello e se a acción saíu ben', () => {
    const facilities = new Map<string, string>();
    const booking = event({ action: 'booking.cancel', target: { kind: 'BOOKING', id: 'b1' } });
    expect(targetView(booking, 'municipality', 'naron', facilities)).toEqual({
      kindKey: 'activity.target.BOOKING',
      text: null,
      link: ['/', 'naron', 'xestion', 'reservas'],
      query: { reserva: 'b1' },
    });
    // A plataforma non ve reservas (D4-03).
    expect(targetView(booking, 'platform', null, facilities)).toEqual({
      kindKey: 'activity.target.BOOKING',
      text: null,
      link: null,
    });
    // Unha acción que fallou pode levar o id dunha reserva que non existe: sen ligazón.
    expect(targetView({ ...booking, outcome: 'FAILURE' }, 'municipality', 'naron', facilities).link).toBeNull();
  });
});

describe('rexistro de actividade: 320 px', () => {
  const css = readFileSync(resolve(process.cwd(), 'src/styles/components/activity.css'), 'utf8');

  it('por debaixo de 40rem as filas apílanse e cada dato leva o seu rótulo', () => {
    const narrow = css.slice(css.indexOf('@media (max-width: 40rem)'));
    expect(narrow.length).toBeGreaterThan(0);
    expect(narrow).toMatch(/\.f-activity__table tbody tr \{\s*display: grid;/);
    expect(narrow).toContain('content: attr(data-label)');
    // A cabeceira só se oculta á vista (os lectores seguen tendo os roles da táboa).
    expect(narrow).toContain('clip-path: inset(50%)');
    expect(css).not.toMatch(/overflow-x:\s*(auto|scroll)/);
  });
});

describe('conservación e bloqueo (ADR-0020, revisión do 2026-10-29)', () => {
  it('o tramo bloqueado: desde hai 3 anos ata hai 1, e os prazos da API mandan', () => {
    expect(blockedDays(null, '2029-03-01')).toEqual({ first: '2026-03-01', last: '2028-03-01' });
    expect(blockedDays({ visible: 'P6M', retention: 'P2Y' }, '2029-03-01')).toEqual({
      first: '2027-03-01',
      last: '2028-09-01',
    });
    expect(firstVisibleDay({ visible: 'P1Y', retention: 'P3Y' }, '2028-02-29')).toBe('2027-02-28');
    // Un prazo que non se entende cae no de por defecto.
    expect(retentionPeriods({ visible: 'un ano', retention: '' }).retention).toEqual({ years: 3, months: 0, days: 0 });
    expect(periodLabel({ years: 1, months: 0, days: 0 })).toEqual({ key: 'common.period.yearsOne', count: 1 });
  });

  it('a referencia: obrigatoria, como moito 80 caracteres e sen caracteres de control', () => {
    expect(validReference(' DP 123/2028 ')).toBe(true);
    expect(validReference('   ')).toBe(false);
    expect(validReference('x'.repeat(81))).toBe(false);
    expect(validReference('x'.repeat(80))).toBe(true);
    expect(validReference('EXP\n1')).toBe(false);
  });

  it('a persoa coa identidade bloqueada: «Persoa desactivada», cos seus roles e sen correo', () => {
    expect(actorView({ kind: 'MEMBER', roles: ['STAFF'], blocked: true })).toEqual({
      name: null,
      nameKey: 'activity.actor.blocked',
      email: null,
      roleKeys: ['staff.members.role.STAFF'],
      platform: false,
    });
  });
});
