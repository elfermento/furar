// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, Injector, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { filter, map } from 'rxjs';
import { PlatformAccessService } from '../auth/platform-access.service';
import { SessionService } from '../auth/session.service';
import { StaffAccessService } from '../auth/staff-access.service';
import { CurrentMunicipality } from '../municipality/current-municipality';
import type { ManageSection, ManageTab } from './manage-links';
import { currentSection, currentTab, manageKind, manageSections } from './manage-links';

/** Os textos dos contadores para o lector (`nav.count.<que>One`/`Other` e «e»). */
const COUNT = 'nav.count.';

/**
 * O menú da xestión no concello actual, un para a barra superior, a franxa de
 * pestanas e a barra inferior: os apartados, o actual, a pestana actual e os contadores.
 */
@Injectable({ providedIn: 'root' })
export class ManageNav {
  private readonly session = inject(SessionService);
  private readonly staff = inject(StaffAccessService);
  private readonly platform = inject(PlatformAccessService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly transloco = inject(TranslocoService);
  /** Para volver traducir os textos dos números ao cambiar de idioma. */
  private readonly lang = toSignal(this.transloco.langChanges$);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** Os apartados que ve a persoa (baleiro sen xestión). */
  readonly sections = computed<ManageSection[]>(() => {
    const slug = this.municipality.slug();
    const kind = manageKind(this.session.authenticated(), this.staff.role(), this.platform.platformAdmin());
    return slug !== null && kind !== null ? manageSections(slug, kind) : [];
  });
  readonly section = computed(() => currentSection(this.sections(), this.url()));
  readonly tab = computed(() => currentTab(this.sections(), this.url()));

  /** As pestanas da franxa: as do apartado actual, se son dúas ou máis e a páxina é unha delas. */
  readonly strip = computed<readonly ManageTab[]>(() => {
    const section = this.section();
    const tab = this.tab();
    return section && tab && section.tabs.length > 1 && section.tabs.includes(tab) ? section.tabs : [];
  });
  /** Hai franxa de pestanas: o título da páxina vai só para lectores de pantalla (coma en Concello). */
  readonly underTabs = computed(() => this.strip().length > 0);

  /** «Validar QR» (o botón central no móbil), se a persoa o ve. */
  readonly qr = computed(
    () =>
      this.sections()
        .flatMap((section) => section.tabs)
        .find((tab) => tab.qr) ?? null,
  );

  /** O número dunha pestana (`null` sen contador ou a cero). */
  count(tab: ManageTab): number | null {
    const value = tab.counter ? this.injector.get(tab.counter).count() : null;
    return value ? value : null;
  }

  /**
   * O que di o número dunha pestana, para o lector de pantalla («3 incidencias novas»); baleiro
   * sen número. O número á vista vai `aria-hidden`.
   */
  countText(tab: ManageTab): string {
    this.lang();
    const n = this.count(tab);
    if (!n || !tab.counterText) return n ? String(n) : '';
    return this.transloco.translate(COUNT + tab.counterText + (n === 1 ? 'One' : 'Other'), { count: n });
  }

  /**
   * O do número dun apartado: o de cada pestana que conta algo, xuntos («3 incidencias novas e 1
   * solicitude de tempada pendente»); baleiro sen número.
   */
  sectionCountText(section: ManageSection, tabs: readonly ManageTab[] = section.tabs): string {
    this.lang();
    const parts = tabs.map((tab) => this.countText(tab)).filter((text) => text !== '');
    if (parts.length < 2) return parts[0] ?? '';
    const and = this.transloco.translate(COUNT + 'and');
    return `${parts.slice(0, -1).join(', ')} ${and} ${parts.at(-1)}`;
  }

  /** A suma dos contadores das pestanas dun apartado (`null` se non hai ningún). */
  sectionCount(section: ManageSection, tabs: readonly ManageTab[] = section.tabs): number | null {
    const total = tabs.reduce((sum, tab) => sum + (this.count(tab) ?? 0), 0);
    return total ? total : null;
  }
}
