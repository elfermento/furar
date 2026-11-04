// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DeploymentSettings } from '../../../core/deployment/deployment-settings';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { localized } from '../../../shared/custom-fields/localized';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { MapComponent } from '../../../shared/ui/map/map.component';
import { pointOf } from '../../../shared/ui/map/map-point';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { formatCents } from '../../../shared/ui/money/money';
import type { IconName } from '../../../shared/ui/icon/icons';
import { amenityIcon } from '../amenities/amenities';
import type { RuleKey } from '../uses/use-terms';
import { RULE_KEYS, ruleText } from '../uses/use-terms';
import { periodText } from '../uses/use-row.component';
import type { ClosureLine } from './closures';
import { closureLines } from './closures';
import { PublicFacilityCardComponent } from './public-facility-card.component';
import type { PublicFacility, PublicUse } from './public-facility.store';

/** Unha caixa de «Antes de reservar»: a norma (ou o pago) coa súa icona e o texto. */
export interface BeforeItem {
  readonly key: RuleKey | 'payment';
  readonly icon: IconName;
  readonly lines: readonly string[];
}

const RULE_ICONS: Record<RuleKey, IconName> = {
  advance: 'calendar-month',
  duration: 'hourglass',
  cap: 'event-repeat',
  cancel: 'event-busy',
};
import { PublicFacilityStore } from './public-facility.store';

/**
 * Ficha pública dunha instalación (maqueta aprobada `06-ficha-publica`), sen sesión: o nome
 * arriba; á esquerda a imaxe de portada, a descrición no idioma de quen mira, «Que se pode
 * reservar» (cada uso nunha fila co seu modo e duración e o prezo á dereita), «Antes de reservar»
 * (só as normas con valor e, se hai usos de pago, como se paga; debaixo, as normas da
 * instalación), os servizos e «Como chegar» co mapa (se o despregue ten mapa); á dereita, a tarxeta fixa con enderezo,
 * horario e contacto e, se se reserva por internet, «Reservar». Só se ve se a instalación está
 * publicada; se non, «non está dispoñible».
 */
@Component({
  selector: 'furar-public-facility-page',
  imports: [
    ButtonDirective,
    IconComponent,
    MapComponent,
    PublicFacilityCardComponent,
    RouterLink,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
  ],
  providers: [PublicFacilityStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './public-facility.page.html',
})
export class PublicFacilityPage implements OnInit {
  /** Parámetro `:id` da ruta. */
  readonly id = input.required<string>();

  protected readonly store = inject(PublicFacilityStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly notFound = computed(() => this.store.problem()?.status === 404);
  protected readonly cover = computed(() => this.store.facility()?.images[0] ?? null);
  protected readonly description = computed(() => localized(this.store.facility()?.description, this.language.lang()));
  protected readonly point = computed(() => {
    return pointOf(this.store.facility()?.address);
  });
  protected readonly mapEnabled = inject(DeploymentSettings).mapEnabled;
  protected readonly iconOf = amenityIcon;
  /** «Próximos peches» (maqueta 54): os sesenta días seguintes; sen ningún, non se pinta. */
  protected readonly closures = computed<ClosureLine[]>(() => {
    this.langChanges();
    const facility = this.store.facility();
    if (!facility) return [];
    return closureLines(
      facility.closures ?? [],
      this.language.lang(),
      this.transloco.translate('facility.public.closedDay'),
    );
  });
  protected readonly houseRules = computed(() => localized(this.store.facility()?.houseRules, this.language.lang()));

  /**
   * «Antes de reservar»: cada norma que ten valor nalgún uso. Se todos os usos din o mesmo, unha
   * liña; se non, unha por uso co seu nome diante. O pago, só se algún uso é de pago.
   */
  protected readonly before = computed<BeforeItem[]>(() => {
    this.langChanges();
    const facility = this.store.facility();
    if (!facility) return [];
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    const items: BeforeItem[] = [];
    for (const key of RULE_KEYS) {
      const texts = facility.uses.map((use) => ({
        name: use.name,
        text: ruleText(key, use.rules, use.slotMinutes, t, 'public'),
      }));
      const set = texts.filter((entry): entry is { name: string; text: string } => entry.text !== null);
      if (!set.length) continue;
      const same = set.length === texts.length && set.every((entry) => entry.text === set[0].text);
      items.push({
        key,
        icon: RULE_ICONS[key],
        lines: same ? [set[0].text] : set.map((entry) => `${entry.name}: ${entry.text}`),
      });
    }
    const note = localized(this.store.paymentNote(), this.language.lang());
    if (note && facility.uses.some((use) => use.price.kind === 'PAID')) {
      items.push({ key: 'payment', icon: 'euro', lines: [note] });
    }
    return items;
  });

  ngOnInit(): void {
    void this.load();
  }

  protected load(): Promise<void> {
    const slug = this.municipality.slug();
    return slug ? this.store.load(slug, this.id()) : Promise.resolve();
  }

  protected alt(image: PublicFacility['images'][number]): string {
    return localized(image.alt, this.language.lang());
  }

  /** O prezo da fila: «30,00 €» (por persoa nas prazas); «Gratis» vai en chip e sen prezo, nada. */
  protected price(use: PublicUse): string | null {
    return use.price.kind === 'PAID' && typeof use.price.amountCents === 'number'
      ? formatCents(use.price.amountCents)
      : null;
  }

  /**
   * O uso pola súa forma e duración, nunca polos deportes (son da instalación): «Enteira · 90
   * minutos», «Metade norte ou Metade sur · 60 minutos», «20 prazas · 90 minutos».
   */
  protected useDetail(use: PublicUse): string {
    this.langChanges();
    const t = (key: string, params: Record<string, unknown> = {}) =>
      this.transloco.translate(`facility.public.use.${key}`, params);
    const mode =
      use.rentalMode === 'PARTS'
        ? t('parts', { names: use.parts.map((part) => part.name).join(t('or')) })
        : use.rentalMode === 'SEATS'
          ? t('seats', { count: use.capacity })
          : t('whole');
    const dates = use.availability === 'DATES' ? use.periods.map(periodText).join(', ') : null;
    return [mode, t('minutes', { minutes: use.slotMinutes }), dates].filter(Boolean).join(' · ');
  }
}
