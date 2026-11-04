// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ConnectedPosition } from '@angular/cdk/overlay';
import { signal } from '@angular/core';
import type { Observable } from 'rxjs';
import { Subject, catchError, debounce, from, map, of, switchMap, timer } from 'rxjs';
import type { IconName } from '../icon/icons';

/**
 * Unha suxestión: o valor que se garda, o texto que se ve e un detalle opcional debaixo. Opcionais
 * para listas máis ricas: `group` (título do grupo, xa traducido; vale cando o levan todas),
 * `image` (miniatura decorativa, URL da mesma orixe) ou `icon` (icona decorativa nun cadro).
 */
export interface ComboboxOption<T = unknown> {
  readonly value: T;
  readonly label: string;
  readonly detail?: string;
  readonly group?: string;
  readonly image?: string;
  readonly icon?: IconName;
}

/** Fonte asíncrona das suxestións para o texto escrito (xa sen espazos nos extremos). */
export type ComboboxSource<T> = (
  query: string,
) => Observable<readonly ComboboxOption<T>[]> | Promise<readonly ComboboxOption<T>[]>;

/** Fase da busca: `idle` sen busca, `loading` agardando, `ready` con resposta, `error` se fallou. */
export type ComboboxPhase = 'idle' | 'loading' | 'ready' | 'error';

/** Debaixo do campo (ou da orixe que se lle dea) e, se non cabe, enriba; co mesmo ancho ca el. */
export function comboboxPositions(gap: number): ConnectedPosition[] {
  return [
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: gap },
    { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -gap },
  ];
}

type Result<T> =
  | { readonly ok: true; readonly options: readonly ComboboxOption<T>[] }
  | { readonly ok: false; readonly error: unknown };

/** O que precisa a busca de quen a usa; lese en cada busca (os `input()` poden cambiar). */
export interface ComboboxSearchConfig<T> {
  readonly source: () => ComboboxSource<T>;
  readonly delay: () => number;
  readonly failed: (error: unknown) => void;
}

/**
 * Estado da busca de furar-combobox, sen DOM: agarda `delay` ms sen texto novo, pide á fonte e
 * cancela a petición anterior (switchMap), así unha resposta vella nunca pisa a nova.
 * `reset` esquece a busca en curso: o que chegue tarde xa non se mostra.
 */
export class ComboboxSearch<T> {
  readonly phase = signal<ComboboxPhase>('idle');
  readonly options = signal<readonly ComboboxOption<T>[]>([]);
  /** Texto da busca en curso ou da última resposta. */
  readonly searched = signal('');
  private readonly queries = new Subject<string>();
  private readonly subscription;

  constructor(config: ComboboxSearchConfig<T>) {
    this.subscription = this.queries
      .pipe(
        debounce((query) => (query === '' ? of(0) : timer(config.delay()))),
        switchMap((query) =>
          query === ''
            ? of(null)
            : from(config.source()(query)).pipe(
                map((options): Result<T> => ({ ok: true, options })),
                catchError((error: unknown) => of<Result<T>>({ ok: false, error })),
              ),
        ),
      )
      .subscribe((result) => {
        if (result === null) {
          return;
        }
        this.options.set(result.ok ? result.options : []);
        this.phase.set(result.ok ? 'ready' : 'error');
        if (!result.ok) {
          config.failed(result.error);
        }
      });
  }

  /** Busca `query` (xa recortado): pasa a «buscando» agora e pide á fonte tras a espera. */
  start(query: string): void {
    this.searched.set(query);
    this.phase.set('loading');
    this.options.set([]);
    this.queries.next(query);
  }

  reset(): void {
    this.queries.next('');
    this.phase.set('idle');
    this.options.set([]);
    this.searched.set('');
  }

  destroy(): void {
    this.subscription.unsubscribe();
  }
}

/** Unha fila da lista: unha suxestión ou a opción final «Crear». */
export type ComboboxItem<T> =
  | { readonly kind: 'option'; readonly id: string; readonly option: ComboboxOption<T> }
  | { readonly kind: 'create'; readonly id: string };

/** «Crear» só se se pide, hai texto e ningunha suxestión se chama xa así (sen maiúsculas). */
export function offersCreate<T>(allow: boolean, query: string, options: readonly ComboboxOption<T>[]): boolean {
  const q = query.toLocaleLowerCase();
  return allow && q.length > 0 && !options.some((o) => o.label.trim().toLocaleLowerCase() === q);
}

/** Filas da lista con ids estables para `aria-activedescendant`. */
export function comboboxItems<T>(
  listId: string,
  options: readonly ComboboxOption<T>[],
  create: boolean,
): ComboboxItem<T>[] {
  const items: ComboboxItem<T>[] = options.map((option, i) => ({ kind: 'option', id: `${listId}-${i}`, option }));
  return create ? [...items, { kind: 'create', id: `${listId}-create` }] : items;
}

/** Índice activo tras unha frecha, dando a volta nos extremos (sen activo: a primeira ou a última). */
export function nextActive(down: boolean, current: number, count: number): number {
  if (down) {
    return (current + 1) % count;
  }
  return current <= 0 ? count - 1 : current - 1;
}

type Translate = (key: string, params?: Record<string, unknown>) => string;

/** O que di o `role="status"`: buscando, número de suxestións (e «Crear», se hai), sen resultados ou o erro. */
export function comboboxAnnouncement(
  t: Translate,
  state: { phase: ComboboxPhase; count: number; errorText: string; createText: string | null },
): string {
  switch (state.phase) {
    case 'loading':
      return t('ui.combobox.searching');
    case 'error':
      return state.errorText || t('ui.combobox.error');
    case 'ready': {
      const { count, createText } = state;
      const base =
        count === 0
          ? t('ui.combobox.empty')
          : t(count === 1 ? 'ui.combobox.countOne' : 'ui.combobox.countOther', { count });
      return createText ? `${base} ${t('ui.combobox.createHint', { option: createText })}` : base;
    }
    default:
      return '';
  }
}

/** Un grupo da lista: título (null = sen título, p. ex. «Crear») e as súas filas co índice global. */
export interface ComboboxGroup<T> {
  readonly id: string;
  readonly label: string | null;
  readonly items: readonly { readonly item: ComboboxItem<T>; readonly index: number }[];
}

/**
 * Agrupa as filas consecutivas co mesmo `group`, na orde na que chegan. `null` se algunha suxestión
 * non leva grupo (lista simple). «Crear» vai nun grupo propio ao final.
 */
export function comboboxGroups<T>(listId: string, items: readonly ComboboxItem<T>[]): ComboboxGroup<T>[] | null {
  const options = items.filter((i) => i.kind === 'option');
  if (!options.length || options.some((i) => !i.option.group)) {
    return null;
  }
  const groups: { id: string; label: string | null; items: { item: ComboboxItem<T>; index: number }[] }[] = [];
  items.forEach((item, index) => {
    const label = item.kind === 'option' ? (item.option.group ?? null) : null;
    const last = groups.at(-1);
    if (last && last.label === label) {
      last.items.push({ item, index });
    } else {
      groups.push({ id: `${listId}-g${groups.length}`, label, items: [{ item, index }] });
    }
  });
  return groups;
}

/** Texto para comparar: sen acentos nin maiúsculas («Pádel» → «padel»). */
export function foldText(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLocaleLowerCase();
}

/** Anaco dun texto, marcado se coincide coa busca. */
export interface TextPart {
  readonly text: string;
  readonly match: boolean;
}

/**
 * Parte `label` arredor da primeira coincidencia de `query`, sen distinguir acentos nin
 * maiúsculas («pad» en «Pista de pádel» marca «pád»). Sen coincidencia, un só anaco.
 */
export function matchParts(label: string, query: string): TextPart[] {
  const q = foldText(query.trim());
  let folded = '';
  const origin: number[] = [];
  let index = 0;
  for (const char of label) {
    for (const f of foldText(char)) {
      folded += f;
      origin.push(index);
    }
    index += char.length;
  }
  const at = q ? folded.indexOf(q) : -1;
  if (at < 0) {
    return [{ text: label, match: false }];
  }
  const start = origin[at];
  const end = at + q.length < origin.length ? origin[at + q.length] : label.length;
  return [
    { text: label.slice(0, start), match: false },
    { text: label.slice(start, end), match: true },
    { text: label.slice(end), match: false },
  ].filter((part) => part.text);
}
