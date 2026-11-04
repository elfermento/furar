// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { formatInstant } from '../../../shared/ui/calendar/madrid-time';
import type { RadioCardOption } from '../../../shared/ui/radio-cards/radio-cards.component';
import type { TagTone } from '../../../shared/ui/tag/tag.component';
import type { StaffMemberResponse } from './staff-members.store';

export type { StaffMemberResponse };
export type StaffRole = StaffMemberResponse['role'];
export type MailLanguage = StaffMemberResponse['language'];

/** Estado que se ve: o da API (Pendente, Activa, Desactivada). Unha invitación caducada segue Pendente. */
export type MemberState = StaffMemberResponse['status'];

/** Quen está na páxina: a xestión do concello ou a administración da plataforma (fan o mesmo). */
export type StaffMode = 'manager' | 'platform';

/** Accións dunha fila, na orde do menú. */
export type MemberAction = 'resend' | 'role' | 'deactivate' | 'reactivate';

export const ROLES: readonly StaffRole[] = ['MANAGER', 'STAFF'];
/**
 * O que pode facer cada rol, para as tarxetas ao invitar e ao cambiar o rol (claves
 * `staff.members.roleCaps.<ROL>.<clave>`). Sae dos permisos da API (`BookingPermission`, `FacilityPermission`…):
 * as dúas ven as reservas e marcan os cobros; cancelar, instalacións, indicadores, persoal e
 * datos do concello son só da xestora.
 */
export const ROLE_CAPABILITIES: Readonly<Record<StaffRole, readonly (readonly [string, boolean])[]>> = {
  MANAGER: [
    ['bookings', true],
    ['facilities', true],
    ['indicators', true],
    ['staff', true],
  ],
  STAFF: [
    ['bookings', true],
    ['cancel', false],
    ['facilities', false],
    ['staff', false],
  ],
};

/** As tarxetas dos roles (furar-radio-cards) co texto no idioma `lang`. */
export function roleCards(transloco: TranslocoService, lang: string): RadioCardOption<StaffRole>[] {
  return ROLES.map((role) => ({
    value: role,
    label: transloco.translate(`staff.members.role.${role}`, {}, lang),
    items: ROLE_CAPABILITIES[role].map(([key, included]) => ({
      text: transloco.translate(`staff.members.roleCaps.${role}.${key}`, {}, lang),
      included,
    })),
  }));
}

export const MAIL_LANGUAGES: readonly MailLanguage[] = ['gl', 'es'];

/** Paleta de estados (docs/deseno.md P1-9): ámbar o pendente, verde o que vale, gris a desactivada. */
export const STATE_TONE: Readonly<Record<MemberState, TagTone>> = {
  INVITED: 'warn',
  ACTIVE: 'success',
  DEACTIVATED: 'muted',
};

export function memberState(member: StaffMemberResponse): MemberState {
  return member.status;
}

/**
 * A invitación xa non vale: `INVITED` cunha invitación vencida (`expiresAt <= agora`) ou sen ningunha
 * (`invitation: null`: a conservación borrouna e a pertenza pecharase na mesma pasada).
 */
export function invitationExpired(member: StaffMemberResponse, now: number): boolean {
  if (member.status !== 'INVITED') return false;
  if (!member.invitation) return true;
  const expires = Date.parse(member.invitation.expiresAt);
  return !Number.isNaN(expires) && expires <= now;
}

/**
 * O que se pode facer cunha persoa (a xestión e a plataforma, o mesmo; ADR-0027, revisión do
 * 2026-11-02): reenviar, só a quen aínda non aceptou; cambiar o rol e desactivar, a quen non está
 * desactivada; reactivar, a desactivada que aceptou algunha vez e non está anonimizada (a invitada
 * invítase de novo).
 */
export function memberActions(member: StaffMemberResponse): MemberAction[] {
  if (member.status === 'DEACTIVATED') return canReactivate(member) ? ['reactivate'] : [];
  const actions: MemberAction[] = [];
  if (member.status === 'INVITED') actions.push('resend');
  actions.push('role', 'deactivate');
  return actions;
}

/** Reactivar: desactivada, aceptou algunha vez e aínda non está anonimizada. */
export function canReactivate(member: StaffMemberResponse): boolean {
  return member.status === 'DEACTIVATED' && !!member.acceptedAt && !member.anonymized;
}

/** «Reenviar» á vista na fila: o correo non saíu ou a invitación caducou. */
export function resendInline(member: StaffMemberResponse, now: number): boolean {
  if (member.status !== 'INVITED') return false;
  return member.invitation?.delivery === 'FAILED' || invitationExpired(member, now);
}

/**
 * Que dicir na columna «Último acceso» (clave en `staff.members.access.*`): a data do último acceso
 * (ou «Nunca»); mentres está pendente, a invitación: «Enviando…», «Invitación enviada o …» ou, en
 * vermello, «Non se puido enviar» e «Invitación caducada».
 */
export interface LastAccess {
  readonly key: 'at' | 'never' | 'sending' | 'sent' | 'failed' | 'expired';
  readonly date: string | null;
  readonly danger: boolean;
}

export function lastAccessOf(member: StaffMemberResponse, now: number): LastAccess {
  if (member.status !== 'INVITED') {
    const date = formatInstant(member.lastSignInAt);
    return { key: date ? 'at' : 'never', date, danger: false };
  }
  const invitation = member.invitation;
  if (invitation?.delivery === 'PENDING') return { key: 'sending', date: null, danger: false };
  if (invitation?.delivery === 'FAILED') return { key: 'failed', date: null, danger: true };
  if (invitationExpired(member, now)) return { key: 'expired', date: null, danger: true };
  return { key: 'sent', date: formatInstant(invitation?.sentAt, false), danger: false };
}

/**
 * Por que aínda non se pode reenviar (a API dío na invitación; o 429 queda de rede): `wait` ata
 * `until` (ms) despois do último envío ou `spent` se xa se gastaron os envíos. `null` se se pode.
 */
export type ResendBlock = { readonly kind: 'wait'; readonly until: number } | { readonly kind: 'spent' };

export function resendBlock(member: StaffMemberResponse, now: number): ResendBlock | null {
  const invitation = member.status === 'INVITED' ? member.invitation : undefined;
  if (!invitation) return null;
  if (invitation.resendsExhausted) return { kind: 'spent' };
  const until = invitation.resendAvailableAt ? Date.parse(invitation.resendAvailableAt) : Number.NaN;
  return until > now ? { kind: 'wait', until } : null;
}

/** Os prazos de reenvío aínda abertos (ms), para o reloxo da conta atrás. */
export function resendDeadlines(members: readonly StaffMemberResponse[]): number[] {
  return members
    .map((m) =>
      m.status === 'INVITED' && m.invitation?.resendAvailableAt ? Date.parse(m.invitation.resendAvailableAt) : NaN,
    )
    .filter((at) => !Number.isNaN(at));
}

/** Primeiro o persoal en uso (activas e convidadas, na orde da API); as desactivadas, á parte. */
export function splitMembers(members: readonly StaffMemberResponse[]): {
  current: StaffMemberResponse[];
  deactivated: StaffMemberResponse[];
} {
  return {
    current: members.filter((m) => m.status !== 'DEACTIVATED'),
    deactivated: members.filter((m) => m.status === 'DEACTIVATED'),
  };
}

/**
 * Como chamar a unha persoa nos textos: o seu correo, que é o usuario e o que mostra a lista (ou o
 * nome, se non hai correo). Unha anonimizada non ten ningún dos dous (a lista di «Persoa
 * anonimizada» e non ten accións).
 */
export function memberLabel(member: StaffMemberResponse): string {
  return member.email?.trim() || member.name?.trim() || '';
}

/** Espera dun 429 (`Retry-After`, en segundos): minutos cara arriba ou horas; `null` se non hai. */
export function retryWait(problem: ApiProblem): { key: 'waitMinutes' | 'waitHours'; count: number } | null {
  const seconds = problem.extensions['retryAfter'];
  if (typeof seconds !== 'number' || !(seconds > 0)) return null;
  const minutes = Math.ceil(seconds / 60);
  return minutes < 90 ? { key: 'waitMinutes', count: minutes } : { key: 'waitHours', count: Math.ceil(minutes / 60) };
}

export const RATE_LIMITED = 'staff.invitation-rate-limited';
