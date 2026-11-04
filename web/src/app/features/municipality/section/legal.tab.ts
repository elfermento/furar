// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef, OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { LegalPageKey } from '../../../core/municipality/legal-pages';
import {
  LEGAL_PAGES,
  LEGAL_PAGE_FIELDS,
  LEGAL_PAGE_MODES,
  LEGAL_PAGE_PATHS,
} from '../../../core/municipality/legal-pages';
import type { LeavableTab } from '../../../core/unsaved-changes/leave-tab.guard';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FormErrorsSummaryComponent } from '../../../shared/ui/form-errors-summary.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TabLinkDirective, TabNavComponent } from '../../../shared/ui/tabs/tab-nav.component';
import { LegalFieldsComponent } from '../legal-fields.component';
import type { LegalForm, LegalTextsForm } from '../legal-form';
import {
  LEGAL_REQUIRED,
  LEGAL_REQUIRED_ROWS,
  legalBody,
  legalForm,
  legalTextsBody,
  legalTextsForm,
  legalValues,
  missingLegal,
  PAGE_ROWS,
  pagesWithErrors,
} from '../legal-form';
import { LegalPagesActions } from '../legal-pages.actions';
import { LegalTextEditorComponent } from '../legal-text-editor.component';
import type { LegalTextsResponse, MunicipalityResponse } from '../municipality.store';
import { MunicipalityStore } from '../municipality.store';
import { errorsMessage, missingMessage } from './legal-tab.texts';
import { MunicipalitySection } from './section';
import { tabForm } from './tab-form';
import { TabSave } from './tab-save';

type LegalTabForm = FormGroup<{ legal: LegalForm; texts: LegalTextsForm }>;

/**
 * «Legal» da sección «Concello» (ADR-0017 punto 8; maqueta 49). Enriba, «Datos que usan os textos
 * legais» (denominación, NIF, domicilio, correo xeral e do DPD): non son obrigatorios, pero se
 * falta algún un aviso dío en vivo e a pestana leva un triángulo. Debaixo, unha subpestana por
 * páxina (Privacidade, Accesibilidade, Aviso legal), cada unha co seu URL
 * (`…/concello/legal/aviso-legal`), co modo (Páxina desta web, Texto propio, Páxina propia ou Sen
 * páxina) e o que ese modo pide; «Sen páxina» deixa gardar pero avisa da obriga legal, e o pé xa
 * non a liga. Un só «Gardar»: o concello cos datos legais e, se cambiaron, os textos propios.
 */
@Component({
  selector: 'furar-municipality-legal-tab',
  imports: [
    ButtonDirective,
    CalloutComponent,
    CardComponent,
    FormErrorsSummaryComponent,
    IconComponent,
    InputDirective,
    LegalFieldsComponent,
    LegalTextEditorComponent,
    ProblemAlertComponent,
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    StatusStateComponent,
    TabLinkDirective,
    TabNavComponent,
    TranslocoPipe,
  ],
  providers: [LegalPagesActions],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-section__col' },
  templateUrl: './legal.tab.html',
})
export class LegalTab implements OnInit, LeavableTab {
  private readonly store = inject(MunicipalityStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly section = inject(MunicipalitySection);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly translation = toSignal(this.transloco.selectTranslation());
  private readonly formElement = viewChild<ElementRef<HTMLElement>>('formElement');
  private readonly textEditor = viewChild(LegalTextEditorComponent);
  private readonly router = inject(Router);
  protected readonly actions = inject(LegalPagesActions);

  /** A páxina do URL (`privacidade`, `accesibilidade`, `aviso-legal`; withComponentInputBinding). */
  readonly page = input<string>('');

  protected readonly tab = tabForm<LegalTabForm>('legal');
  protected readonly saver = new TabSave();
  protected readonly pages = LEGAL_PAGES;
  protected readonly modes = LEGAL_PAGE_MODES;
  protected readonly fields = LEGAL_PAGE_FIELDS;
  protected readonly rows = PAGE_ROWS;
  protected readonly requiredRows = LEGAL_REQUIRED_ROWS;
  protected readonly placeholder = 'https://';
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;

  private readonly texts = signal<LegalTextsResponse | null>(null);
  /** Sobe con cada cambio do formulario: as marcas e o aviso sígueno (OnPush). */
  private readonly revision = signal(0);

  protected readonly current = computed<LegalPageKey>(
    () => LEGAL_PAGES.find((key) => LEGAL_PAGE_PATHS[key] === this.page()) ?? 'privacy',
  );

  readonly dirty = this.tab.dirty;
  readonly tabName = computed(() => {
    this.translation();
    return this.transloco.translate('section.tab.legal');
  });

  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  /** «Faltan datos que usan as páxinas legais por defecto: NIF e domicilio. …» */
  protected readonly missingText = computed(() => {
    this.revision();
    this.translation();
    const form = this.tab.form();
    return form ? missingMessage(missingLegal(form.controls.legal.getRawValue()), this.t, this.language.lang()) : '';
  });

  /** Páxinas con algún erro á vista (tocado ou despois de «Gardar»). */
  protected readonly errorPages = computed<LegalPageKey[]>(() => {
    this.revision();
    const form = this.tab.form();
    return form ? pagesWithErrors(form.controls.legal, form.controls.texts, this.saver.submitted()) : [];
  });

  /** «Hai campos por revisar en Privacidade e nos datos que usan os textos legais.» */
  protected readonly status = computed(() => {
    if (!this.saver.submitted()) return '';
    this.revision();
    this.translation();
    return errorsMessage(this.errorPages(), this.requiredWrong(), this.t, this.language.lang());
  });

  private requiredWrong(): boolean {
    const form = this.tab.form();
    return !!form && LEGAL_REQUIRED.some((key) => form.controls.legal.controls[key].invalid);
  }

  constructor() {
    effect((onCleanup) => {
      const form = this.tab.form();
      if (!form) return;
      const detach = this.actions.attach(form.controls.legal, form.controls.texts);
      const bump = () => {
        this.revision.update((n) => n + 1);
        // O aviso da pestana «Legal» segue o formulario mentres se edita.
        this.section.liveLegal.set(legalValues(form.controls.legal));
      };
      bump();
      const subscription = form.events.subscribe(bump);
      onCleanup(() => {
        detach();
        subscription.unsubscribe();
      });
    });
    inject(DestroyRef).onDestroy(() => this.section.liveLegal.set(null));
  }

  ngOnInit(): void {
    void this.load();
  }

  protected pageLink(page: LegalPageKey): string[] {
    return ['/', this.municipality.slug() ?? '', 'xestion', 'concello', 'legal', LEGAL_PAGE_PATHS[page]];
  }

  private async load(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    try {
      this.texts.set(await this.store.legalTexts(slug));
      const municipality = this.municipality.value();
      if (municipality) this.reset(municipality);
    } catch (error: unknown) {
      this.saver.problem.set(asProblem(error));
    }
  }

  save(): Promise<boolean> {
    const form = this.tab.form();
    const municipality = this.municipality.value();
    if (!form || !municipality) return Promise.resolve(false);
    const textsBefore = JSON.stringify(legalTextsForm(this.texts()).getRawValue());
    return this.saver.run(
      form,
      this.formElement(),
      async () => {
        let updated = await this.store.update(municipality, {
          name: municipality.name,
          languages: municipality.languages,
          defaultLanguage: municipality.defaultLanguage,
          theme: municipality.theme,
          customFields: municipality.customFields,
          legal: legalBody(form.controls.legal),
        });
        this.municipality.set(updated);
        if (JSON.stringify(form.controls.texts.getRawValue()) !== textsBefore) {
          const saved = await this.store.updateLegalTexts(
            updated.slug,
            updated.version,
            legalTextsBody(form.controls.texts),
          );
          this.texts.set(saved.body);
          updated = { ...updated, version: saved.version };
          this.municipality.set(updated);
        }
        this.reset(updated);
      },
      (problem) => {
        for (const error of problem.fieldErrors) {
          // Os erros dos textos legais chegan sen prefixo desde o seu PUT ou con el (legalTexts).
          const control =
            form.controls.legal.get(error.field.replace(/^legal\./, '')) ??
            form.controls.texts.get(error.field.replace(/^legalTexts\./, ''));
          control?.setErrors({ [error.code]: error.params });
          control?.markAsTouched();
        }
        return problem.fieldErrors.length;
      },
      () => this.revealFirstError(),
    );
  }

  discard(): void {
    const municipality = this.municipality.value();
    if (municipality) this.reset(municipality);
    this.saver.clear();
  }

  protected async reload(): Promise<void> {
    if (await this.saver.reloadMunicipality()) await this.load();
  }

  protected async resetText(page: LegalPageKey): Promise<void> {
    if (await this.actions.confirmReset(page)) this.textEditor()?.focus();
  }

  /**
   * Antes de enfocar o primeiro erro: se a páxina á vista non ten ningún e os datos de enriba
   * tampouco, vai á primeira páxina que si (o seu URL).
   */
  private revealFirstError(): void {
    const first = this.errorPages()[0];
    if (first && !this.requiredWrong() && !this.errorPages().includes(this.current())) {
      void this.router.navigate(this.pageLink(first));
    }
    this.textEditor()?.showFirstError();
  }

  private reset(municipality: MunicipalityResponse): void {
    this.tab.set(new FormGroup({ legal: legalForm(municipality.legal), texts: legalTextsForm(this.texts()) }));
  }
}
