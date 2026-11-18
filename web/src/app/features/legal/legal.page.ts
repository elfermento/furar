// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { LanguageService } from '../../core/i18n/language.service';
import type { LegalPageKey } from '../../core/municipality/legal-pages';
import { externalLegalUrl, legalPageMode } from '../../core/municipality/legal-pages';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { DeploymentSettings } from '../../core/deployment/deployment-settings';
import { LEGAL_ANCHORS, furarCookiesHtml, furarLegalHtml } from '../../core/municipality/furar-legal-html';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { ProseHtmlDirective } from '../../shared/ui/prose/prose-html.directive';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import type { LegalTextsResponse, PrivacyNoticeResponse } from './legal.store';
import { LegalStore } from './legal.store';

/**
 * As páxinas que pinta: as tres do concello e a privacidade das solicitudes de alta dun concello
 * («¿Es un concello?», `/alta-concello/privacidade`, ADR-0042, revisión do 2026-11-17), que é do
 * despregue: o responsable é quen opera o servizo, non un concello.
 */
export type LegalPageName = LegalPageKey | 'joinRequests';

/** Título de cada páxina (o `h1`; o mesmo texto que a pestana do navegador). */
const TITLES: Readonly<Record<LegalPageName, string>> = {
  privacy: 'privacy.title',
  accessibility: 'accessibility.title',
  legalNotice: 'legalNotice.title',
  joinRequests: 'privacy.title',
};

/** As liñas do tratamento das solicitudes de alta, nesta orde. */
const JOIN_REQUEST_LINES = [
  'joinRequest.privacyController',
  'joinRequest.privacyPurpose',
  'joinRequest.privacyBasis',
  'joinRequest.privacyStorage',
  'joinRequest.privacyRights',
] as const;

/**
 * Páxinas legais públicas do concello (ADR-0017 punto 8): Privacidade (RGPD art. 13),
 * Accesibilidade (RD 1112/2018) e Aviso legal (LSSI art. 10), segundo o modo de cada unha.
 *
 * - Páxina de Furar: o texto de Furar (src/i18n) cos datos legais do concello (en Privacidade
 *   tamén responsable, DPD, dereitos e encargado; revisión do 2026-11-03) e, en Privacidade, os
 *   prazos do rexistro de actividade do despregue.
 * - Texto propio: o HTML que gardou o concello, saneado no servidor, co estilo de Furar e no
 *   idioma de quen mira (se falta, no outro). Sen texto en ningún idioma, o de Furar.
 * - Páxina propia: as ligazóns de Furar xa levan á do concello; quen chegue aquí (un correo ou o
 *   rexistro) atopa a ligazón.
 * - Sen páxina: o pé non a liga; quen chegue cun enderezo antigo le que o concello non a publica.
 *
 * En Privacidade, a sección «Cookies» de Furar vai sempre ao final, con calquera modo (revisión do
 * 2026-11-09): describe o software, non o concello.
 *
 * Fóra dun concello, a privacidade das solicitudes de alta (`joinRequests`): quen opera o servizo,
 * finalidade, base, conservación e dereitos, en texto de Furar e sen modos.
 *
 * O HTML pinta con `[furarProseHtml]`, que só copia a lista branca: nada de `bypassSecurityTrust*`.
 * Un fragmento no enderezo (`#persoal`, o do correo da invitación) leva á súa sección cando o texto
 * xa está pintado: o contido chega despois da navegación e o router non o atopa.
 */
@Component({
  selector: 'furar-legal-page',
  imports: [
    IconComponent,
    PageHeaderComponent,
    ProseHtmlDirective,
    ProblemAlertComponent,
    StatusStateComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="f-prose mx-auto" [attr.lang]="contentLang()">
      <furar-page-header [heading]="titleKey() | transloco" />
      <furar-problem-alert [problem]="problem()" />
      @if (page() === 'joinRequests') {
        <section data-legal-join-requests>
          <h2>{{ 'joinRequest.privacyPageHeading' | transloco }}</h2>
          <ul>
            @for (line of joinRequestLines; track line) {
              <li>{{ line | transloco }}</li>
            }
          </ul>
        </section>
      } @else if (mode() === 'NONE') {
        <p data-legal-none>{{ 'legal.none' | transloco }}</p>
      } @else if (external(); as url) {
        <p>
          {{ 'legal.external' | transloco }}
          <a class="f-external-link" [href]="url" rel="noopener noreferrer"
            >{{ url }}<furar-icon name="open-in-new" /><span class="sr-only">{{
              'legal.externalHint' | transloco
            }}</span></a
          >
        </p>
      } @else if (html(); as body) {
        <div data-legal-body [furarProseHtml]="body" [anchors]="anchors"></div>
      } @else if (!problem()) {
        <furar-status-state mode="loading" />
      }
      @if (cookies(); as section) {
        <div
          data-legal-cookies
          [attr.lang]="contentLang() ? activeLang() : null"
          [furarProseHtml]="section"
          [anchors]="anchors"
        ></div>
      }
    </article>
  `,
})
export class LegalPage implements OnInit {
  private readonly store = inject(LegalStore);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  protected readonly municipality = inject(CurrentMunicipality);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly fragment = toSignal(inject(ActivatedRoute).fragment, { initialValue: null });
  private readonly deployment = inject(DeploymentSettings);
  protected readonly anchors = LEGAL_ANCHORS;
  protected readonly joinRequestLines = JOIN_REQUEST_LINES;

  /** Que páxina é (dato da ruta, `withComponentInputBinding`). */
  readonly page = input.required<LegalPageName>();

  protected readonly problem = signal<ApiProblem | null>(null);
  private readonly notice = signal<PrivacyNoticeResponse | null>(null);
  private readonly texts = signal<LegalTextsResponse | null>(null);
  /** Os textos do idioma activo, xa cargados: o HTML de Furar recalcúlase ao cambiar de idioma. */
  private readonly translation = toSignal(this.transloco.selectTranslation());

  private readonly legal = computed(() => this.municipality.value()?.legal);
  protected readonly titleKey = computed(() => TITLES[this.page()]);
  /** A páxina do concello (as das solicitudes de alta non teñen modos). */
  private readonly municipalityPage = computed(() => {
    const page = this.page();
    return page === 'joinRequests' ? null : page;
  });
  protected readonly mode = computed(() => {
    const page = this.municipalityPage();
    return page ? legalPageMode(this.legal(), page) : 'FURAR';
  });
  protected readonly external = computed(() => {
    const page = this.municipalityPage();
    return page ? externalLegalUrl(this.legal(), page) : null;
  });

  /** O texto propio no idioma de quen mira ou, se falta, no outro. */
  private readonly own = computed(() => {
    const page = this.municipalityPage();
    if (!page || this.mode() !== 'TEXT') return null;
    const text = this.texts()?.[page];
    const lang = this.language.lang();
    const other = lang === 'gl' ? 'es' : 'gl';
    if (text?.[lang]) return { html: text[lang], lang };
    if (text?.[other]) return { html: text[other], lang: other };
    return null;
  });

  protected readonly contentLang = computed(() => this.own()?.lang ?? null);
  protected readonly activeLang = this.language.lang;

  /**
   * «Cookies» en Privacidade: sae sempre, con calquera modo (tamén texto ou páxina propia e «Sen
   * páxina»), porque describe o software; a ela leva a ligazón «Cookies» do pé.
   */
  protected readonly cookies = computed(() => {
    if (this.page() !== 'privacy' || !this.translation()) return null;
    return furarCookiesHtml((key, params) => this.transloco.translate(key, params));
  });

  protected readonly html = computed(() => {
    const page = this.municipalityPage();
    if (!page) return null;
    const own = this.own();
    if (own) return own.html;
    if (this.mode() === 'TEXT' && !this.texts()) return null;
    if (!this.translation()) return null;
    if (page === 'privacy' && !this.notice()) return null;
    return furarLegalHtml(
      page,
      {
        name: this.municipality.name() ?? '',
        lang: this.transloco.getActiveLang(),
        legal: this.legal() ?? {},
        notice: this.notice(),
        mapEnabled: this.deployment.mapEnabled(),
        openData: this.municipality.value()?.openData ?? false,
      },
      (key, params) => this.transloco.translate(key, params),
    );
  });

  constructor() {
    // Unha vez por fragmento: ao cambiar de idioma o texto repíntase e non debe mover a páxina.
    let reached: string | null = null;
    afterRenderEffect(() => {
      const fragment = this.fragment();
      if ((!this.html() && !this.cookies()) || !fragment || fragment === reached) return;
      const id = CSS.escape(fragment);
      const target = this.host.querySelector<HTMLElement>(
        `[data-legal-body] [id="${id}"], [data-legal-cookies] [id="${id}"]`,
      );
      if (!target) return;
      reached = fragment;
      target.tabIndex = -1;
      target.focus({ preventScroll: true });
      target.scrollIntoView?.({ block: 'start' });
    });
  }

  ngOnInit(): void {
    const slug = this.municipality.slug();
    if (!this.municipalityPage() || this.external() || this.mode() === 'NONE' || !slug) return;
    if (this.mode() === 'TEXT') {
      this.store.legalTexts(slug).then(
        (texts) => this.texts.set(texts),
        (error: unknown) => this.problem.set(asProblem(error)),
      );
    }
    // Privacidade de Furar (tamén se o texto propio falta en todos os idiomas): os prazos do rexistro.
    if (this.page() === 'privacy') {
      this.store.privacyNotice().then(
        (notice) => this.notice.set(notice),
        (error: unknown) => this.problem.set(asProblem(error)),
      );
    }
  }
}
