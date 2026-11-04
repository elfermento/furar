// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ApiProblem } from '../../../core/errors/api-problem';
import { MEMBERS, NOW, member } from '../../../../testing/staff-members';
import {
  STATE_TONE,
  canReactivate,
  invitationExpired,
  lastAccessOf,
  memberActions,
  memberLabel,
  memberState,
  resendBlock,
  resendDeadlines,
  resendInline,
  retryWait,
  splitMembers,
} from './staff-members';

const now = NOW.getTime();
const invited = (expiresAt: string, delivery: 'PENDING' | 'SENT' | 'FAILED' = 'SENT') =>
  member({
    status: 'INVITED',
    name: undefined,
    invitation: { sentAt: '2026-10-28T09:00:00Z', expiresAt, delivery, resendsExhausted: false },
  });
const problem = (extensions: Record<string, unknown>) =>
  new ApiProblem(429, 'staff.invitation-rate-limited', {}, [], undefined, '', extensions);

describe('Persoal (D4-14): regras da lista', () => {
  it('tres estados: Pendente, Activa e Desactivada; caducada segue pendente', () => {
    expect(memberState(invited('2026-10-28T10:00:00Z'))).toBe('INVITED');
    expect(memberState(member())).toBe('ACTIVE');
    expect(memberState(member({ status: 'DEACTIVATED' }))).toBe('DEACTIVATED');
    expect(STATE_TONE).toEqual({ INVITED: 'warn', ACTIVE: 'success', DEACTIVATED: 'muted' });
  });

  it('caducada = pendente cunha invitación que xa venceu (no mesmo instante tamén) ou sen ningunha', () => {
    expect(invitationExpired(invited('2026-10-28T10:00:01Z'), now)).toBe(false);
    expect(invitationExpired(invited('2026-10-28T10:00:00Z'), now)).toBe(true);
    // A conservación borrou a invitación: caducada.
    expect(invitationExpired(member({ status: 'INVITED' }), now)).toBe(true);
    expect(invitationExpired(member({ status: 'INVITED', invitation: null as never }), now)).toBe(true);
    expect(invitationExpired(member(), now)).toBe(false);
  });

  it('último acceso: a data, «Nunca» ou, mentres está pendente, como vai a invitación', () => {
    expect(lastAccessOf(member(), now)).toEqual({ key: 'at', date: '27/10/2026, 18:05', danger: false });
    expect(lastAccessOf(member({ lastSignInAt: undefined }), now)).toEqual({ key: 'never', date: null, danger: false });
    expect(lastAccessOf(member({ status: 'DEACTIVATED' }), now).key).toBe('at');
    expect(lastAccessOf(member({ status: 'DEACTIVATED', lastSignInAt: undefined }), now).key).toBe('never');
    expect(lastAccessOf(invited('2026-11-04T00:00:00Z'), now)).toEqual({
      key: 'sent',
      date: '28/10/2026',
      danger: false,
    });
    expect(lastAccessOf(invited('2026-11-04T00:00:00Z', 'PENDING'), now).key).toBe('sending');
    expect(lastAccessOf(invited('2026-11-04T00:00:00Z', 'FAILED'), now)).toEqual({
      key: 'failed',
      date: null,
      danger: true,
    });
    expect(lastAccessOf(invited('2026-10-01T00:00:00Z'), now)).toEqual({ key: 'expired', date: null, danger: true });
  });

  it('accións (a xestión e a plataforma, igual): reenviar á pendente, rol e desactivar, e reactivar ás desactivadas', () => {
    const deactivated = member({ status: 'DEACTIVATED', deactivatedAt: '2026-10-20T09:00:00Z' });
    expect(memberActions(member())).toEqual(['role', 'deactivate']);
    expect(memberActions(member({ role: 'STAFF' }))).toEqual(['role', 'deactivate']);
    expect(memberActions(invited('2026-11-04T00:00:00Z'))).toEqual(['resend', 'role', 'deactivate']);
    expect(memberActions(deactivated)).toEqual(['reactivate']);
    expect(memberActions({ ...deactivated, role: 'STAFF' })).toEqual(['reactivate']);
  });

  it('reactivar: só a desactivada que aceptou e non está anonimizada (tamén a bloqueada)', () => {
    const deactivated = member({ status: 'DEACTIVATED' });
    expect(canReactivate(deactivated)).toBe(true);
    expect(canReactivate({ ...deactivated, blocked: true, email: undefined, name: undefined })).toBe(true);
    expect(canReactivate({ ...deactivated, anonymized: true })).toBe(false);
    // Invitada que nunca aceptou: invítase de novo.
    expect(canReactivate({ ...deactivated, acceptedAt: undefined })).toBe(false);
    expect(memberActions({ ...deactivated, acceptedAt: undefined })).toEqual([]);
    expect(canReactivate(member())).toBe(false);
  });

  it('«Reenviar» á vista se o correo non saíu ou caducou', () => {
    expect(resendInline(invited('2026-11-04T00:00:00Z', 'FAILED'), now)).toBe(true);
    expect(resendInline(invited('2026-10-01T00:00:00Z'), now)).toBe(true);
    expect(resendInline(invited('2026-11-04T00:00:00Z'), now)).toBe(false);
    expect(resendInline(invited('2026-11-04T00:00:00Z', 'PENDING'), now)).toBe(false);
    expect(resendInline(member(), now)).toBe(false);
  });

  it('«Reenviar» bloqueado ata resendAvailableAt (exclusivo) ou para sempre se se gastaron os envíos', () => {
    const at = (resendAvailableAt?: string, resendsExhausted = false) =>
      member({
        status: 'INVITED',
        invitation: {
          sentAt: '2026-10-28T09:00:00Z',
          expiresAt: '2026-11-04T00:00:00Z',
          delivery: 'SENT',
          resendAvailableAt,
          resendsExhausted,
        },
      });
    const until = Date.parse('2026-10-28T10:01:45Z');
    expect(resendBlock(at('2026-10-28T10:01:45Z'), now)).toEqual({ kind: 'wait', until });
    expect(resendBlock(at('2026-10-28T10:00:00Z'), now)).toBeNull();
    expect(resendBlock(at(), now)).toBeNull();
    expect(resendBlock(at(undefined, true), now)).toEqual({ kind: 'spent' });
    // Activa ou desactivada: non hai invitación que reenviar.
    expect(resendBlock({ ...at('2026-10-28T10:01:45Z'), status: 'ACTIVE' }, now)).toBeNull();
    expect(resendDeadlines([at('2026-10-28T10:01:45Z'), at(), member()])).toEqual([until]);
  });

  it('desactivadas á parte; o correo (ou o nome, se non hai correo)', () => {
    const { current, deactivated } = splitMembers(MEMBERS);
    expect(current.map((m) => m.id)).toEqual(['m1', 'm2', 'm3', 'm4']);
    expect(deactivated.map((m) => m.id)).toEqual(['m5']);
    expect(memberLabel(member())).toBe('ana@naron.example');
    expect(memberLabel(member({ email: undefined }))).toBe('Ana Rey');
    expect(memberLabel(member({ status: 'DEACTIVATED', anonymized: true, name: undefined, email: undefined }))).toBe(
      '',
    );
  });

  it('espera do 429: minutos cara arriba ata 89; despois, horas; sen Retry-After, ningunha', () => {
    expect(retryWait(problem({ retryAfter: 1 }))).toEqual({ key: 'waitMinutes', count: 1 });
    expect(retryWait(problem({ retryAfter: 540 }))).toEqual({ key: 'waitMinutes', count: 9 });
    expect(retryWait(problem({ retryAfter: 89 * 60 }))).toEqual({ key: 'waitMinutes', count: 89 });
    expect(retryWait(problem({ retryAfter: 90 * 60 }))).toEqual({ key: 'waitHours', count: 2 });
    expect(retryWait(problem({}))).toBeNull();
    expect(retryWait(problem({ retryAfter: 0 }))).toBeNull();
  });
});
