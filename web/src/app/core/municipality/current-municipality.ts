// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from '../../api/api';
import { getMunicipality } from '../../api/fn/municipalities/get-municipality';
import type { MunicipalityResponse } from '../../api/models/municipality-response';
import { ApiProblem } from '../errors/api-problem';
import { ThemeService, loadThemeTokens } from '../theme/theme.service';

/**
 * Concello do URL (/:municipality/...). Cárgase no resolver antes de pintar, co seu tema,
 * para que non haxa parpadeo (ADR-0014).
 */
@Injectable({ providedIn: 'root' })
export class CurrentMunicipality {
  private readonly api = inject(Api);
  private readonly theme = inject(ThemeService);
  private readonly current = signal<MunicipalityResponse | null>(null);

  readonly value = this.current.asReadonly();
  readonly slug = computed(() => this.current()?.slug ?? null);
  readonly name = computed(() => this.current()?.name ?? null);

  /** Devolve o concello ou null se non existe; outros erros propáganse. */
  async load(slug: string): Promise<MunicipalityResponse | null> {
    const loaded = this.current();
    if (loaded?.slug === slug) {
      return loaded;
    }
    // O chunk das cores derivadas, en paralelo coa API (só a primeira vez).
    void loadThemeTokens();
    try {
      const municipality = await this.api.invoke(getMunicipality, { slug });
      this.current.set(municipality);
      await this.theme.apply(municipality.theme);
      return municipality;
    } catch (error: unknown) {
      if (error instanceof ApiProblem && error.status === 404) {
        this.clear();
        return null;
      }
      throw error;
    }
  }

  /** Despois de gardar cambios (nome, tema...): o chunk do tema xa está desde `load`. */
  set(municipality: MunicipalityResponse): void {
    this.current.set(municipality);
    void this.theme.apply(municipality.theme);
  }

  clear(): void {
    this.current.set(null);
    this.theme.reset();
  }
}
