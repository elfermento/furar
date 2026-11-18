// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, signal } from '@angular/core';

/** Prazas por reserva, como moito (o mesmo límite ca na API). */
export const MAX_SEATS = 11;

/** Unha persoa que se pode marcar en «Para quen». */
export interface ParticipantOption {
  readonly id: string;
  readonly label: string;
  /** Á dereita, en gris: o nome de quen reserva ou o ano de nacemento. */
  readonly detail?: string;
  /** Sempre marcada: no mostrador, a persoa a nome de quen vai a reserva. */
  readonly fixed?: boolean;
}

/** Un acompañante escrito no mostrador só para unha reserva. */
export interface CompanionDraft {
  readonly name: string;
  readonly year: string;
}

/** O id da opción «Eu» (ou a persoa titular no mostrador). */
export const SELF = 'self';

/** «Marta», «Marta e Uxía», «Marta, Uxía e Brais». */
export function joinNames(names: readonly string[], and: string): string {
  if (names.length < 2) return names[0] ?? '';
  return `${names.slice(0, -1).join(', ')}${and}${names.at(-1)}`;
}

/** O primeiro nome: «Uxía Vilar» → «Uxía». */
export function firstName(name: string): string {
  return name.trim().split(/\s+/)[0] ?? '';
}

/** Unha persoa dunha reserva, tal como a manda a API («Para quen»). */
export interface BookedParticipant {
  readonly kind: 'SELF' | 'DEPENDENT' | 'COMPANION';
  readonly name: string;
  readonly birthYear?: number;
  readonly dependentId?: string;
}

/** Os nomes de «Para quen»; co ano entre parénteses onde a API o manda (a xestión). */
export function participantNames(participants: readonly BookedParticipant[] | undefined): string[] {
  return (participants ?? []).map((p) => (p.birthYear ? `${p.name} (${p.birthYear})` : p.name));
}

/**
 * As persoas que hai que nomear: todas, salvo cando a reserva é só para quen a ten (o seu nome xa
 * está na reserva e repetilo non di nada).
 */
export function namedParticipants(participants: readonly BookedParticipant[] | undefined): string[] {
  const list = participants ?? [];
  return list.length === 1 && list[0].kind === 'SELF' ? [] : participantNames(list);
}

/** Un ano de catro cifras entre o actual menos `maxAge` e o actual. */
export function validBirthYear(text: string, thisYear: number, maxAge: number): boolean {
  if (!/^\d{4}$/.test(text.trim())) return false;
  const year = Number(text);
  return year >= thisYear - maxAge && year <= thisYear;
}

/**
 * Para quen é a reserva que se está facendo nun uso por prazas (ADR-0031): quen a ten, persoas a
 * cargo da súa conta e, só no mostrador, acompañantes. Cada persoa ocupa unha praza. Un por páxina
 * (reservar e reservar no nome doutra persoa).
 */
@Injectable()
export class ParticipantsSelection {
  /** Se quen ten a reserva ocupa praza. */
  readonly self = signal(true);
  readonly dependentIds = signal<readonly string[]>([]);
  readonly companions = signal<readonly CompanionDraft[]>([]);

  /** As prazas escollidas. */
  readonly seats = computed(() => (this.self() ? 1 : 0) + this.dependentIds().length + this.companions().length);
  /** As opcións marcadas da lista (`SELF` e os ids das fichas). */
  readonly selected = computed(() => [...(this.self() ? [SELF] : []), ...this.dependentIds()]);

  toggle(id: string): void {
    if (id === SELF) {
      this.self.update((on) => !on);
      return;
    }
    this.dependentIds.update((ids) => (ids.includes(id) ? ids.filter((each) => each !== id) : [...ids, id]));
  }

  /** Marca unha ficha (a que se acaba de gardar). */
  add(id: string): void {
    this.dependentIds.update((ids) => (ids.includes(id) ? ids : [...ids, id]));
  }

  /** Deixa só as fichas que seguen existindo. */
  keep(existing: readonly string[]): void {
    this.dependentIds.update((ids) => {
      const kept = ids.filter((id) => existing.includes(id));
      return kept.length === ids.length ? ids : kept;
    });
  }

  set(self: boolean, dependentIds: readonly string[], companions: readonly CompanionDraft[] = []): void {
    this.self.set(self);
    this.dependentIds.set(dependentIds);
    this.companions.set(companions);
  }

  addCompanion(): void {
    this.companions.update((list) => [...list, { name: '', year: '' }]);
  }

  editCompanion(index: number, change: Partial<CompanionDraft>): void {
    this.companions.update((list) => list.map((each, i) => (i === index ? { ...each, ...change } : each)));
  }

  removeCompanion(index: number): void {
    this.companions.update((list) => list.filter((_, i) => i !== index));
  }
}
