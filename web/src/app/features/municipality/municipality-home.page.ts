// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { CustomFieldDefinition } from './municipality.store';
import { PlatformAccessService } from '../../core/auth/platform-access.service';
import { canConfigure } from '../../core/auth/roles';
import { SessionService } from '../../core/auth/session.service';
import { StaffAccessService } from '../../core/auth/staff-access.service';
import { LanguageService } from '../../core/i18n/language.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { CustomFieldsViewComponent } from '../../shared/custom-fields/custom-fields-view.component';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { CarouselComponent, CarouselItemDirective } from '../../shared/ui/carousel/carousel.component';
import { CardComponent } from '../../shared/ui/card/card.component';
import { FacilitySearchComponent } from '../../shared/facility-search/facility-search.component';
import { localToday } from '../../shared/ui/calendar/local-date';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import { ExampleCardComponent } from './home/example-card.component';
import { exampleFacilities } from './home/examples';
import { FacilityCardComponent } from './home/facility-card.component';
import type { PublicFacilityResponse } from './home/home.store';
import { HomeNavigation } from './home/home-navigation';
import { HomeStore } from './home/home.store';
import { HowItWorksComponent } from './home/how-it-works.component';
import { MunicipalityStore } from './municipality.store';
import { OpeningChecklistComponent } from './section/opening-checklist.component';

/** Fotos do collaxe da portada: as portadas das primeiras instalacións que a teñen. */
export const COLLAGE_SIZE = 3;

/**
 * Portada do concello (maqueta aprobada 02-portada, decisión «A»): título co nome do concello,
 * buscador e collaxe de fotos; tarxetas das instalacións publicadas; «Así de fácil» e, se os hai,
 * os datos públicos do concello (campos personalizados). Cada tarxeta leva «N ocos hoxe» se a
 * instalación se reserva por internet e lle quedan ocos; buscar leva ao listado público.
 *
 * Exemplos (maqueta 46, ADR-0028): mentres o concello non ten ningunha instalación publicada, o
 * collaxe, as tarxetas e «Así de fácil» levan instalacións de exemplo coa etiqueta «Exemplo» e sen
 * ligazón. Con `showcase` (a raíz dunha instalación sen ningún concello) non hai concello: título
 * «Reserva a túa pista», o buscador desactivado e só os exemplos.
 *
 * Pendentes (maqueta 56): a quen configura o concello (Xestión e a plataforma), mentres lle falte
 * algún paso de «Para abrir as reservas», a lista de «Xeral» pregada enriba do título. Vai á parte
 * (`@defer`), así o público non carga nada da xestión.
 */
@Component({
  selector: 'furar-municipality-home-page',
  imports: [
    TranslocoPipe,
    ExampleCardComponent,
    CustomFieldsViewComponent,
    ButtonDirective,
    CardComponent,
    StatusStateComponent,
    TagComponent,
    FacilityCardComponent,
    FacilitySearchComponent,
    HowItWorksComponent,
    CarouselComponent,
    CarouselItemDirective,
    OpeningChecklistComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './municipality-home.page.html',
})
export class MunicipalityHomePage {
  private readonly store = inject(MunicipalityStore);
  private readonly home = inject(HomeStore);
  private readonly session = inject(SessionService);
  private readonly document = inject(DOCUMENT);
  private readonly transloco = inject(TranslocoService);
  protected readonly municipality = inject(CurrentMunicipality);
  protected readonly language = inject(LanguageService);
  protected readonly navigation = inject(HomeNavigation);
  private readonly staff = inject(StaffAccessService);
  private readonly platform = inject(PlatformAccessService);
  /** Quen configura o concello: ve os pasos pendentes. Recepción e a veciñanza, non. */
  protected readonly configures = computed(() => canConfigure(this.staff.role(), this.platform.platformAdmin()));
  protected readonly definitions = signal<CustomFieldDefinition[]>([]);
  protected readonly facilities = signal<PublicFacilityResponse[]>([]);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  /** Ocos libres hoxe por instalación (as que non se reservan por internet non están). */
  protected readonly freeToday = signal<ReadonlyMap<string, number>>(new Map());

  /** Raíz sen ningún concello: só os exemplos. */
  readonly showcase = input(false, { transform: booleanAttribute });

  protected readonly facilityKey = (facility: PublicFacilityResponse) => facility.id;
  /** Os exemplos van mentres non hai ningunha instalación publicada (ou non hai concello). */
  protected readonly showExamples = computed(
    () => this.showcase() || (this.state() === 'ready' && this.facilities().length === 0),
  );
  /** Os textos do idioma activo xa cargados: os exemplos tradúcense cando chegan. */
  private readonly translation = toSignal(
    this.transloco.langChanges$.pipe(switchMap((lang) => this.transloco.selectTranslation(lang))),
  );
  protected readonly examples = computed(() => {
    this.translation();
    return exampleFacilities((key) => this.transloco.translate(key));
  });
  /** As que pintan o collaxe e «Así de fácil»: as publicadas ou, sen elas, os exemplos. */
  protected readonly shown = computed(() => (this.showExamples() ? this.examples() : this.facilities()));
  protected readonly collage = computed(() =>
    this.shown()
      .map((f) => f.cover)
      .slice(0, COLLAGE_SIZE),
  );
  /** «localhost:8080/naron»: o enderezo da web para as capturas debuxadas (sen concello, só o servidor). */
  protected readonly address = computed(() => {
    const host = this.document.defaultView?.location.host ?? '';
    const slug = this.municipality.slug();
    return this.showcase() || !slug ? host : `${host}/${slug}`;
  });

  constructor() {
    // As definicións visibles dependen da sesión (PUBLIC / OWNER / STAFF).
    effect(() => {
      const slug = this.municipality.slug();
      this.session.authenticated();
      if (slug) {
        this.store.definitions(slug).then(
          (definitions) => this.definitions.set(definitions),
          () => this.definitions.set([]),
        );
      }
    });
    // A orde alfabética é a do idioma: ao cambialo, pídense outra vez.
    effect(() => {
      const slug = this.municipality.slug();
      this.language.lang();
      if (slug) {
        untracked(() => this.load(slug));
      }
    });
  }

  protected retry(): void {
    const slug = this.municipality.slug();
    if (slug) {
      this.load(slug);
    }
  }

  private load(slug: string): void {
    this.state.set(this.facilities().length ? 'ready' : 'loading');
    // Sen ocos (fallo incluído), as tarxetas vense igual, sen o chip.
    this.home.freeSlots(slug, localToday()).then(
      (free) => this.freeToday.set(free),
      () => this.freeToday.set(new Map()),
    );
    this.home.facilities(slug).then(
      (facilities) => {
        this.facilities.set(facilities);
        this.state.set('ready');
      },
      () => this.state.set('error'),
    );
  }
}
