// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import { STAFF_PAGES } from '../../../core/layout/staff-pages';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { LEGAL_PAGES, legalPageMode } from '../../../core/municipality/legal-pages';
import type { LegalDetailsResponse } from '../municipality.store';
import { missingLegal } from '../legal-form';

export type SectionTabKey = 'general' | 'appearance' | 'service' | 'legal' | 'staff' | 'customFields' | 'activity';

export interface SectionTab {
  readonly key: SectionTabKey;
  /** Segmento do URL (`/:concello/xestion/concello/<path>`). */
  readonly path: string;
}

/**
 * As pestanas da sección «Concello» (maqueta 49), na orde da barra. Todas son do xestor e da
 * plataforma (ADR-0028, revisión do 2026-11-03); as do persoal saen cando existen as súas páxinas.
 */
export const SECTION_TABS: readonly SectionTab[] = [
  { key: 'general', path: 'xeral' },
  { key: 'appearance', path: 'aparencia' },
  { key: 'service', path: 'servizo' },
  { key: 'legal', path: 'legal' },
  ...(STAFF_PAGES.members ? [{ key: 'staff', path: 'persoal' } as const] : []),
  { key: 'customFields', path: 'campos' },
  ...(STAFF_PAGES.activity ? [{ key: 'activity', path: 'rexistro' } as const] : []),
];

/**
 * Estado da sección «Concello» que comparten a barra de pestanas e cada pestana: que pestana ten
 * cambios sen gardar (o punto; como saír dunha con cambios pregunta, só pode haber unha) e o aviso
 * de «Legal» (o triángulo): faltan datos que usan os textos legais ou algunha páxina está «Sen
 * páxina». Mentres se edita «Legal», o aviso segue o formulario; se non, os datos gardados.
 */
@Injectable()
export class MunicipalitySection {
  private readonly current = inject(CurrentMunicipality);

  readonly dirty = signal<SectionTabKey | null>(null);
  /** Os datos legais do formulario de «Legal» mentres está aberta; `null`, os gardados. */
  readonly liveLegal = signal<Partial<LegalDetailsResponse> | null>(null);

  private readonly legal = computed(() => this.liveLegal() ?? this.current.value()?.legal ?? {});
  readonly missing = computed(() => missingLegal(this.legal()));
  readonly withoutPage = computed(() =>
    LEGAL_PAGES.filter((page) => legalPageMode(this.legal() as LegalDetailsResponse, page) === 'NONE'),
  );
  /** Que avisa a pestana «Legal»: faltan datos, falta unha páxina ou nada. */
  readonly legalWarning = computed<'missing' | 'page' | null>(() =>
    this.missing().length ? 'missing' : this.withoutPage().length ? 'page' : null,
  );
}
