// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { bookingMatch, myMatch, publicMatch } from '../../../../testing/open-matches';
import { booking } from '../reserve/reserve.fixture';
import { canOpenMatch, managedMatchText, matchCancelNotice, matchDetailActions } from './booking-match';
import { matchAction } from './open-match';
import { openMatchBody, openMatchForm } from './open-match-form';

const t = (key: string, params: Record<string, unknown> = {}) =>
  [key, ...Object.entries(params).map(([k, v]) => `${k}=${String(v)}`)].join(' ');

/** Unha reserva confirmada de pista enteira, para dentro de 5 días e que aínda se pode cancelar. */
const NOW = Date.parse('2099-11-15T10:00:00Z');
const future = (overrides = {}) =>
  booking({
    start: '2099-11-20T19:00:00Z',
    end: '2099-11-20T20:30:00Z',
    canCancel: true,
    done: false,
    rentalMode: 'WHOLE',
    ...overrides,
  });
const ON = { enabled: true, cancelHours: 12 };

describe('Buscar xente para xogar desde a reserva', () => {
  it('pódese buscar: función acesa, confirmada, por vir, cancelable de balde, sen prazas e antes do prazo', () => {
    expect(canOpenMatch(future(), ON, NOW)).toBe(true);
    expect(canOpenMatch(future(), { enabled: false, cancelHours: 12 }, NOW)).toBe(false);
    expect(canOpenMatch(future(), undefined, NOW)).toBe(false);
    expect(canOpenMatch(future({ rentalMode: 'SEATS' }), ON, NOW)).toBe(false);
    expect(canOpenMatch(future({ canCancel: false }), ON, NOW)).toBe(false);
    expect(canOpenMatch(future({ openMatch: bookingMatch() }), ON, NOW)).toBe(false);
    expect(canOpenMatch(future({ openMatch: bookingMatch({ status: 'CANCELLED' }) }), ON, NOW)).toBe(false);
    expect(canOpenMatch(future({ openMatch: bookingMatch({ status: 'CLOSED' }) }), ON, NOW)).toBe(true);
    // O prazo do concello (inicio menos 12 horas) xa pasou.
    expect(canOpenMatch(future(), ON, Date.parse('2099-11-20T08:00:00Z'))).toBe(false);
  });

  it('as accións do detalle: buscar; buscando, ver, editar e deixar de buscar; pechada, ver e reabrir', () => {
    const ids = (b: ReturnType<typeof future>) => matchDetailActions(b, ON, (key) => key, NOW).map((a) => a.id);
    expect(ids(future())).toEqual(['match-open']);
    expect(ids(future({ openMatch: bookingMatch() }))).toEqual(['match-see', 'match-edit', 'match-stop']);
    expect(ids(future({ openMatch: bookingMatch({ status: 'CLOSED' }) }))).toEqual(['match-see', 'match-open']);
    expect(ids(future({ openMatch: bookingMatch({ status: 'CANCELLED' }) }))).toEqual([]);
    expect(matchDetailActions(future(), ON, (key) => `«${key}»`, NOW)[0]).toEqual({
      id: 'match-open',
      icon: 'group-add',
      label: '«openMatch.action»',
    });
  });

  it('o detalle do persoal: os números, as pendentes e o estado se non busca', () => {
    expect(managedMatchText(bookingMatch(), t)).toBe(
      'openMatch.ofTotal total=4 maximum=10 · openMatch.manage.minimum count=6 · openMatch.manage.maximum count=10',
    );
    expect(managedMatchText(bookingMatch({ pending: 2, status: 'CLOSED' }), t)).toContain(
      'openMatch.manage.pending count=2 · openMatch.manage.status.CLOSED',
    );
    expect(managedMatchText(bookingMatch({ status: 'CANCELLED' }), t)).toContain('openMatch.manage.status.CANCELLED');
  });

  it('cancelar unha reserva con partida avisa de cantas persoas apuntadas hai', () => {
    expect(matchCancelNotice(bookingMatch({ confirmed: 3 }), t)?.text).toBe('openMatch.cancelNotice count=3');
    expect(matchCancelNotice(bookingMatch({ confirmed: 1 }), t)?.text).toBe('openMatch.cancelNoticeOne count=1');
    expect(matchCancelNotice(bookingMatch({ confirmed: 0 }), t)?.text).toBe('openMatch.cancelNoticeNone count=0');
    expect(matchCancelNotice(bookingMatch({ status: 'CANCELLED' }), t)).toBeNull();
    expect(matchCancelNotice(undefined, t)).toBeNull();
  });
});

describe('a acción da páxina da partida', () => {
  it('quen organiza, dentro, pendente, rexeitada, cancelada, pechada, completa, pedir ou unirse', () => {
    expect(matchAction(myMatch({ me: { role: 'ORGANIZER' } }))).toBe('organizer');
    expect(matchAction(myMatch({ me: { role: 'CONFIRMED', playerId: 'p1' } }))).toBe('joined');
    expect(matchAction(myMatch({ me: { role: 'PENDING', playerId: 'p1' } }))).toBe('pending');
    expect(matchAction(myMatch({ me: { role: 'REJECTED', playerId: 'p1' } }))).toBe('rejected');
    expect(matchAction(publicMatch({ status: 'CANCELLED' }))).toBe('cancelled');
    expect(matchAction(publicMatch({ status: 'CLOSED' }))).toBe('closed');
    expect(matchAction(publicMatch({ total: 10 }))).toBe('full');
    expect(matchAction(publicMatch({ approval: true }))).toBe('ask');
    expect(matchAction(myMatch())).toBe('join');
  });
});

describe('o formulario da partida', () => {
  const set = (form: ReturnType<typeof openMatchForm>, values: Record<string, string>) => {
    form.patchValue(values);
    form.updateValueAndValidity();
  };

  it('ao abrir: «Xa somos» coas prazas da reserva, o resto baleiro e obrigatorio', () => {
    const form = openMatchForm(null, 2);
    expect(form.getRawValue()).toEqual({
      level: 'NONE',
      title: '',
      description: '',
      knownCount: '2',
      minimum: '',
      maximum: '',
      approval: false,
    });
    expect(form.controls.minimum.hasError('required')).toBe(true);
    expect(openMatchForm(null, 0).controls.knownCount.value).toBe('1');
  });

  it('as contas entre campos: o máximo non menor ca o mínimo e «Xa somos» menor ca o máximo', () => {
    const form = openMatchForm(null, 1);
    set(form, { minimum: '10', maximum: '8' });
    expect(form.controls.maximum.errors).toEqual({ 'openMatch.maximum-below-minimum': { min: 10 } });
    set(form, { maximum: '12' });
    expect(form.controls.maximum.errors).toBeNull();
    set(form, { knownCount: '12' });
    expect(form.controls.knownCount.errors).toEqual({ 'openMatch.known-not-below-maximum': { max: 12 } });
    set(form, { knownCount: '3' });
    expect(form.valid).toBe(true);
    // Fóra de rango: o erro de sempre, non o das contas.
    set(form, { minimum: '1', maximum: '41' });
    expect(form.controls.minimum.hasError('min')).toBe(true);
    expect(form.controls.maximum.hasError('min')).toBe(true);
    expect(form.controls.maximum.hasError('openMatch.maximum-below-minimum')).toBe(false);
  });

  it('o corpo: números, e o nivel, o título e a descrición só se se escriben', () => {
    const form = openMatchForm(null, 1);
    set(form, { knownCount: '3', minimum: '6', maximum: '10' });
    expect(openMatchBody(form)).toEqual({ knownCount: 3, minimum: 6, maximum: 10, approval: false });
    form.patchValue({ level: 'ADVANCED', title: '  Pachanga ', description: ' Sen présa ', approval: true });
    expect(openMatchBody(form)).toEqual({
      knownCount: 3,
      minimum: 6,
      maximum: 10,
      approval: true,
      level: 'ADVANCED',
      title: 'Pachanga',
      description: 'Sen présa',
    });
  });

  it('ao editar, o que xa hai', () => {
    const form = openMatchForm(myMatch({ title: undefined, description: undefined, approval: true }), 1);
    expect(form.getRawValue()).toEqual({
      level: 'INTERMEDIATE',
      title: '',
      description: '',
      knownCount: '3',
      minimum: '6',
      maximum: '10',
      approval: true,
    });
  });
});
