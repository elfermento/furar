// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { computed, effect, inject, signal, untracked } from '@angular/core';

/** Unha columna que se pode ensanchar: o ancho de partida e o mínimo, en px. Sen ancho, colle o que sobra. */
export interface ResizableColumn {
  readonly key: string;
  /** `null`: a columna flexible (colle o que sobra, polo menos `flexMin`). */
  readonly width: number | null;
  readonly min?: number;
}

export interface ColumnWidthsOptions {
  readonly columns: readonly ResizableColumn[];
  /**
   * Onde se gardan os anchos (`localStorage`), por persoa e por táboa ({@link columnWidthsKey});
   * `null`, non se gardan (sen sesión).
   */
  readonly storageKey: () => string | null;
  /** Mínimo da columna flexible, en px. */
  readonly flexMin?: number;
  /** Máximo de calquera columna, en px. */
  readonly max?: number;
}

const DEFAULT_MIN = 64;
const DEFAULT_FLEX_MIN = 160;
const DEFAULT_MAX = 640;

/** A clave dos anchos dunha táboa para unha persoa (o `sub`): `furar.columns.<persoa>.<táboa>`. */
export function columnWidthsKey(user: string | null | undefined, table: string): string | null {
  return user ? `furar.columns.${user}.${table}` : null;
}

/**
 * Os anchos das columnas dunha táboa que se poden ensanchar (`table[furarColumnResize]` e
 * `furar-column-resize`). Garda só os que cambiou a persoa, en `localStorage` (por persoa e por
 * táboa; sen almacenamento, valen ata recargar) e recupéraos ao cargar. Chámase nun contexto de
 * inxección (un campo dun compoñente).
 */
export class ColumnWidths {
  private readonly storage = storageOf(inject(DOCUMENT));
  private readonly custom = signal<Readonly<Record<string, number>>>({});
  readonly columns: readonly ResizableColumn[];
  readonly flexMin: number;
  readonly max: number;

  /** A persoa cambiou algún ancho. */
  readonly changed = computed(() => Object.keys(this.custom()).length > 0);
  /** O ancho mínimo da táboa: a suma das columnas e o mínimo das flexibles. */
  readonly total = computed(() =>
    this.columns.reduce((sum, column) => sum + (this.width(column.key) ?? this.flexMin), 0),
  );

  constructor(private readonly options: ColumnWidthsOptions) {
    this.columns = options.columns;
    this.flexMin = options.flexMin ?? DEFAULT_FLEX_MIN;
    this.max = options.max ?? DEFAULT_MAX;
    effect(() => {
      const key = options.storageKey();
      untracked(() => this.custom.set(this.read(key)));
    });
  }

  /** O ancho dunha columna en px; `null`, a flexible. */
  width(key: string): number | null {
    const column = this.column(key);
    if (!column || column.width === null) return null;
    return this.custom()[key] ?? column.width;
  }

  min(key: string): number {
    return this.column(key)?.min ?? DEFAULT_MIN;
  }

  /** Cambia o ancho (entre o mínimo e o máximo); con `save`, gárdao. */
  set(key: string, width: number, save = true): void {
    const column = this.column(key);
    if (!column || column.width === null) return;
    const clamped = Math.round(Math.min(this.max, Math.max(this.min(key), width)));
    this.custom.update((all) => (clamped === column.width ? without(all, key) : { ...all, [key]: clamped }));
    if (save) this.save();
  }

  /** Volve ao ancho de partida dunha columna ou, sen columna, de todas. */
  reset(key?: string): void {
    this.custom.set(key ? without(this.custom(), key) : {});
    this.save();
  }

  /** Garda os anchos (tras arrastrar). */
  save(): void {
    const key = this.options.storageKey();
    if (!key || !this.storage) return;
    try {
      const custom = this.custom();
      if (Object.keys(custom).length) this.storage.setItem(key, JSON.stringify(custom));
      else this.storage.removeItem(key);
    } catch {
      // Almacenamento cheo ou bloqueado: os anchos valen ata recargar.
    }
  }

  private column(key: string): ResizableColumn | undefined {
    return this.columns.find((c) => c.key === key);
  }

  /** Os anchos gardados, só das columnas que existen e con números válidos. */
  private read(key: string | null): Record<string, number> {
    if (!key || !this.storage) return {};
    try {
      const saved: unknown = JSON.parse(this.storage.getItem(key) ?? '{}');
      if (!saved || typeof saved !== 'object') return {};
      const widths: Record<string, number> = {};
      for (const column of this.columns) {
        const value = (saved as Record<string, unknown>)[column.key];
        if (column.width !== null && typeof value === 'number' && Number.isFinite(value)) {
          widths[column.key] = Math.round(Math.min(this.max, Math.max(this.min(column.key), value)));
        }
      }
      return widths;
    } catch {
      return {};
    }
  }
}

function storageOf(document: Document): Storage | null {
  try {
    return document.defaultView?.localStorage ?? null;
  } catch {
    return null;
  }
}

function without(widths: Readonly<Record<string, number>>, key: string): Record<string, number> {
  return Object.fromEntries(Object.entries(widths).filter(([k]) => k !== key));
}
