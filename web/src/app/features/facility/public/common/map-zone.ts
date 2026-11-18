// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';
import { switchMap } from 'rxjs';
import { searchTextChips } from '../../../../shared/facility-search/facility-search';
import type { ChipItem } from '../../../../shared/ui/chip-list/chip-list.component';
import type { MapBounds } from '../../../../shared/ui/map/map-point';
import { inBounds, pointOf } from '../../../../shared/ui/map/map-point';
import { countKey } from './catalog-view';

/**
 * «Zona do mapa» da portada común: mentres o mapa está aberto e a persoa o move ou o achega, o
 * listado (grella ou carruseis) só leva as instalacións con punto dentro do que se ve, e as
 * partidas e os eventos, só os desas instalacións. Filtra o que xa está cargado, sen pedir nada.
 * Abrir o mapa non a pon (só movelo); queda ao volver á lista ata quitala co seu chip ou cos filtros.
 * Tamén dá os textos do listado que dependen dela (o número e os chips). Créase nun campo da páxina.
 */
export class MapZone {
  private readonly transloco = inject(TranslocoService);
  /** Os textos xa cargados do idioma activo (os textos cambian cando chegan). */
  private readonly translation = toSignal(
    this.transloco.langChanges$.pipe(switchMap((lang) => this.transloco.selectTranslation(lang))),
  );
  /** O rectángulo á vista despois do último movemento da persoa (`null`, sen zona). */
  readonly bounds = signal<MapBounds | null>(null);

  /** O número do listado: «N instalacións» ou, coa zona, «N instalacións nesta zona». */
  countText(count: number): string {
    this.translation();
    return this.transloco.translate(countKey(count, !!this.bounds()), { count });
  }

  /** Os chips do listado: o texto buscado («torre») e, coa zona, «Zona do mapa». */
  chips(text: string): ChipItem[] {
    this.translation();
    const zone = this.bounds() ? [{ key: 'zone', label: this.transloco.translate('facility.catalog.zone') }] : [];
    return [...searchTextChips(text), ...zone];
  }

  /** As que caen dentro (todas, sen zona). */
  keep<T extends { readonly address?: Parameters<typeof pointOf>[0] }>(facilities: readonly T[]): T[] {
    const bounds = this.bounds();
    if (!bounds) return [...facilities];
    return facilities.filter((f) => {
      const point = pointOf(f.address);
      return point !== null && inBounds(point, bounds);
    });
  }

  clear(): void {
    this.bounds.set(null);
  }
}
