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
import { SOFTWARE_LEGAL_ANCHOR, STAFF_PRIVACY_ANCHOR, furarLegalHtml } from '../../core/municipality/furar-legal-html';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { ProseHtmlDirective } from '../../shared/ui/prose/prose-html.directive';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import type { LegalTextsResponse, PrivacyNoticeResponse } from './legal.store';
import { LegalStore } from './legal.store';

/** Título de cada páxina (o `h1`; o mesmo texto que a pestana do navegador). */
const TITLES: Readonly<Record<LegalPageKey, string>> = {
  privacy: 'privacy.title',
  accessibility: 'accessibility.title',
  legalNotice: 'legalNotice.title',
};

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
      @if (mode() === 'NONE') {
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
  protected readonly anchors = [STAFF_PRIVACY_ANCHOR, SOFTWARE_LEGAL_ANCHOR];

  /** Que páxina é (dato da ruta, `withComponentInputBinding`). */
  readonly page = input.required<LegalPageKey>();

  protected readonly problem = signal<ApiProblem | null>(null);
  private readonly notice = signal<PrivacyNoticeResponse | null>(null);
  private readonly texts = signal<LegalTextsResponse | null>(null);
  /** Os textos do idioma activo, xa cargados: o HTML de Furar recalcúlase ao cambiar de idioma. */
  private readonly translation = toSignal(this.transloco.selectTranslation());

  private readonly legal = computed(() => this.municipality.value()?.legal);
  protected readonly titleKey = computed(() => TITLES[this.page()]);
  protected readonly mode = computed(() => legalPageMode(this.legal(), this.page()));
  protected readonly external = computed(() => externalLegalUrl(this.legal(), this.page()));

  /** O texto propio no idioma de quen mira ou, se falta, no outro. */
  private readonly own = computed(() => {
    if (this.mode() !== 'TEXT') return null;
    const text = this.texts()?.[this.page()];
    const lang = this.language.lang();
    const other = lang === 'gl' ? 'es' : 'gl';
    if (text?.[lang]) return { html: text[lang], lang };
    if (text?.[other]) return { html: text[other], lang: other };
    return null;
  });

  protected readonly contentLang = computed(() => this.own()?.lang ?? null);

  protected readonly html = computed(() => {
    const own = this.own();
    if (own) return own.html;
    if (this.mode() === 'TEXT' && !this.texts()) return null;
    if (!this.translation()) return null;
    if (this.page() === 'privacy' && !this.notice()) return null;
    return furarLegalHtml(
      this.page(),
      {
        name: this.municipality.name() ?? '',
        lang: this.transloco.getActiveLang(),
        legal: this.legal() ?? {},
        notice: this.notice(),
      },
      (key, params) => this.transloco.translate(key, params),
    );
  });

  constructor() {
    // Unha vez por fragmento: ao cambiar de idioma o texto repíntase e non debe mover a páxina.
    let reached: string | null = null;
    afterRenderEffect(() => {
      const fragment = this.fragment();
      if (!this.html() || !fragment || fragment === reached) return;
      const target = this.host.querySelector<HTMLElement>(`[data-legal-body] [id="${CSS.escape(fragment)}"]`);
      if (!target) return;
      reached = fragment;
      target.tabIndex = -1;
      target.focus({ preventScroll: true });
      target.scrollIntoView?.({ block: 'start' });
    });
  }

  ngOnInit(): void {
    const slug = this.municipality.slug();
    if (this.external() || this.mode() === 'NONE' || !slug) return;
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
