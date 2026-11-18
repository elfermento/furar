// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import es from '../../../../i18n/es.json';
import gl from '../../../../i18n/gl.json';
import type { ActivityEventResponse } from './activity';
import { KNOWN_ACTIONS } from './activity';
import type { Translate } from './activity-rows';
import { changeView, groupEvents, groupSummary, placeText, summaryText, valueText } from './activity-rows';

/** O texto dunha clave con puntos nun JSON aniñado. */
const lookup = (json: unknown, key: string): unknown =>
  key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], json);

/** Un tradutor coma o de Transloco: a clave se non existe, cos {{parámetros}} substituídos. */
const translator =
  (json: unknown): Translate =>
  (key, params = {}) => {
    const value = lookup(json, key);
    if (typeof value !== 'string') return key;
    return value.replace(/\{\{\s*(\w+)\s*\}\}/g, (_, name: string) => String(params[name] ?? ''));
  };
const t = translator(gl);

function event(overrides: Partial<ActivityEventResponse> = {}): ActivityEventResponse {
  return {
    id: 'e1',
    at: '2026-11-10T11:31:00Z',
    action: 'facility.publish',
    outcome: 'SUCCESS',
    actor: { kind: 'MEMBER', name: 'Ana Rey', email: 'ana@naron.example', roles: ['MANAGER'], blocked: false },
    facilityId: 'f1',
    changes: [],
    ...overrides,
  };
}

describe('rexistro: resumo curto de cada acción', () => {
  it('todos os códigos teñen resumo en pasado en gl e es', () => {
    for (const code of KNOWN_ACTIONS) {
      for (const json of [gl, es])
        expect(lookup(json, `activity.done.${code}`), code).toEqual(expect.stringMatching(/\S/));
    }
  });

  it('co nome do obxecto se o leva; fallou, «Intentou …»; descoñecido, o texto de reserva', () => {
    expect(summaryText(event({ label: 'Pista 1' }), t)).toBe('Publicou «Pista 1»');
    expect(summaryText(event(), t)).toBe('Publicou unha instalación');
    expect(summaryText(event({ action: 'facility.season-update', label: 'Inverno' }), t)).toBe(
      'Cambiou a tempada «Inverno»',
    );
    // O nome da instalación xa vai en «Onde»: os datos non o repiten.
    expect(summaryText(event({ action: 'facility.details', label: 'Pista 1' }), t)).toBe('Cambiou os datos');
    expect(summaryText(event({ action: 'booking.cancel', outcome: 'FAILURE' }), t)).toBe(
      'Intentou cancelar unha reserva',
    );
    expect(summaryText(event({ action: 'facility.teleport' }), t)).toBe('Outra acción (facility.teleport)');
    expect(summaryText(event({ action: 'booking.cancel', outcome: 'FAILURE' }), translator(es))).toBe(
      'Intentó cancelar una reserva',
    );
  });

  it('onde: a instalación (a da lista ou a gardada), o persoal, o concello; entrar, ningún', () => {
    const facilities = new Map([['f1', 'Pavillón']]);
    expect(placeText(event(), 'municipality', facilities, t)).toBe('Pavillón');
    expect(placeText(event({ facilityId: 'f9', label: 'Pista vella' }), 'municipality', facilities, t)).toBe(
      'Pista vella',
    );
    expect(placeText(event({ action: 'staff.invite', facilityId: undefined }), 'municipality', facilities, t)).toBe(
      'Persoal',
    );
    expect(placeText(event({ action: 'staff.sign-in', facilityId: undefined }), 'municipality', facilities, t)).toBe(
      null,
    );
    expect(placeText(event({ municipality: 'naron' }), 'platform', facilities, t)).toBe('naron');
  });
});

describe('rexistro: agrupado', () => {
  const at = (minute: number) => new Date(Date.UTC(2026, 10, 10, 11, minute)).toISOString();

  it('xunta as seguidas da mesma persoa na mesma instalación con menos de 30 minutos entre elas', () => {
    const groups = groupEvents([
      event({ id: 'a', at: at(50) }),
      event({ id: 'b', at: at(30), action: 'facility.use-update' }),
      event({ id: 'c', at: at(5), action: 'facility.create' }),
      // Outra persoa: fila á parte.
      event({ id: 'd', at: at(4), actor: { kind: 'MEMBER', name: 'Iago', roles: ['STAFF'], blocked: false } }),
      // Outra instalación.
      event({ id: 'e', at: at(3), facilityId: 'f2' }),
      // Non é da instalación (unha reserva nela).
      event({ id: 'f', at: at(2), action: 'booking.cancel', facilityId: 'f2' }),
    ]);
    expect(groups.map((g) => g.map((e) => e.id))).toEqual([['a', 'b', 'c'], ['d'], ['e'], ['f']]);
    expect(groupSummary(groups[0], 'Pista 1', t)).toBe('Creou e publicou «Pista 1»');
    expect(groupSummary(groups[0].slice(1), 'Pista 1', t)).toBe('Creou «Pista 1»');
    expect(groupSummary(groups[0].slice(0, 2), 'Pista 1', t)).toBe('Cambiou «Pista 1»');
  });

  it('máis de 30 minutos entre dúas seguidas parte o grupo', () => {
    const groups = groupEvents([event({ id: 'a', at: at(50) }), event({ id: 'b', at: at(19) })]);
    expect(groups).toHaveLength(2);
  });
});

describe('rexistro: o que cambiou (ADR-0020, revisión do 2026-11-10)', () => {
  it('campo e valores en texto: datas, códigos, si/non; sen valor antes, «creado»; oculto, sen valores', () => {
    const shown = { created: false, hidden: false, more: false };
    expect(
      changeView({ field: 'season.endDate', before: '2027-06-30', after: '2027-06-15', hidden: false }, t),
    ).toEqual({ field: 'Ata', before: '30/06/2027', after: '15/06/2027', ...shown });
    expect(changeView({ field: 'facility.status', before: 'ACTIVE', after: 'WITHDRAWN', hidden: false }, t)).toEqual({
      field: 'Estado',
      before: 'Activa',
      after: 'De baixa',
      ...shown,
    });
    expect(changeView({ field: 'season.endDate', after: '2027-06-30', hidden: false }, t)).toEqual({
      field: 'Ata',
      before: '—',
      after: '30/06/2027',
      ...shown,
      created: true,
    });
    expect(changeView({ field: 'facility.phone', hidden: true }, t)).toEqual({
      field: 'Teléfono',
      before: '',
      after: '',
      ...shown,
      hidden: true,
    });
    expect(changeView({ field: 'more', after: '3', hidden: false }, t)).toMatchObject({
      field: 'E 3 cambios máis',
      hidden: false,
      more: true,
    });
    expect(changeView({ field: 'x.y', after: 'z', hidden: false }, t).field).toBe('Outro dato');
  });

  it('valores: o do campo antes ca o xeral; listas de códigos; datas dentro do texto', () => {
    expect(valueText('booking.payment', 'PAID', t)).toBe('Cobrada');
    expect(valueText('use.priceKind', 'PAID', t)).toBe('De pago');
    expect(valueText('facility.covered', 'true', t)).toBe('Si');
    expect(valueText('use.weekdays', 'MONDAY,FRIDAY', t)).toBe('Luns, Venres');
    expect(valueText('use.periods', '2026-09-01 – 2027-06-30', t)).toBe('01/09/2026 – 30/06/2027');
    expect(valueText('booking.slot', '2026-11-13 18:00–19:00', t)).toBe('13/11/2026 18:00–19:00');
    expect(valueText('use.price', null, t)).toBe('—');
    expect(valueText('use.price', '12,00 €', t)).toBe('12,00 €');
  });
});
