// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { STAFF_PAGES } from '../../core/layout/staff-pages';
import { countInvalid, describedBy, fieldError, focusFirstInvalid } from '../../shared/ui/field-errors';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { CardComponent } from '../../shared/ui/card/card.component';
import { FieldComponent } from '../../shared/ui/field.component';
import { InputDirective } from '../../shared/ui/input/input.directive';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { FormErrorsSummaryComponent } from '../../shared/ui/form-errors-summary.component';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { SLUG_PATTERN, languagesForm, languagesOf, nameControl } from './municipality-form';
import { LanguagesFieldsetComponent } from './languages-fieldset.component';
import { MunicipalityStore } from './municipality.store';
import { suggestSlug } from './slug-suggestion';
import { placeOf } from '../../shared/i18n/place-name';
import { LanguageService } from '../../core/i18n/language.service';
import { RequiredNoteComponent } from '../../shared/ui/required/required.component';

/**
 * Alta dun concello (plataforma). Primeiro o nome; o identificador (slug), que é o URL e non se
 * pode cambiar, suxírese do nome («A Coruña» → `a-coruna`) mentres a persoa non o escriba a man; se
 * o baleira, volve seguir o nome.
 */
@Component({
  selector: 'furar-municipality-create-page',
  imports: [
    RequiredNoteComponent,
    ReactiveFormsModule,
    TranslocoPipe,
    FieldComponent,
    LanguagesFieldsetComponent,
    FormErrorsSummaryComponent,
    ProblemAlertComponent,
    PageHeaderComponent,
    CardComponent,
    InputDirective,
    ButtonDirective,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './municipality-create.page.html',
})
export class MunicipalityCreatePage {
  private readonly injector = inject(Injector);
  private readonly store = inject(MunicipalityStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly formElement = viewChild.required<ElementRef<HTMLFormElement>>('createForm');

  protected readonly form = new FormGroup({
    slug: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(2),
        Validators.maxLength(40),
        Validators.pattern(SLUG_PATTERN),
      ],
    }),
    name: nameControl(),
    languages: languagesForm(['gl', 'es'], 'gl'),
  });
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly submitted = signal(false);
  protected readonly errorCount = signal(0);
  protected readonly saving = signal(false);
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;
  private readonly language = inject(LanguageService);
  /** A persoa escribiu o identificador: deixa de seguir o nome. */
  private slugByHand = false;

  constructor() {
    this.form.controls.name.valueChanges.pipe(takeUntilDestroyed()).subscribe((name) => {
      if (!this.slugByHand) this.form.controls.slug.setValue(suggestSlug(name));
    });
  }

  protected slugEdited(): void {
    this.slugByHand = this.form.controls.slug.value !== '';
  }

  protected async create(): Promise<void> {
    this.submitted.set(true);
    this.problem.set(null);
    this.form.markAllAsTouched();
    const invalid = countInvalid(this.form);
    this.errorCount.set(invalid);
    if (invalid > 0) {
      afterNextRender(() => focusFirstInvalid(this.formElement().nativeElement), { injector: this.injector });
      return;
    }
    this.saving.set(true);
    try {
      const { slug, name } = this.form.getRawValue();
      const created = await this.store.create({
        slug,
        name: name.trim(),
        languages: languagesOf(this.form.controls.languages),
        defaultLanguage: this.form.controls.languages.controls.defaultLanguage.value,
      });
      this.toast.show('platform.created', { ofName: placeOf(created.name, this.language.lang()) });
      // A plataforma non xestiona o concello (ADR-0007): vai a «Persoal» para invitar ao primeiro
      // xestor; mentres esa páxina non existe, á portada pública.
      await this.router.navigate(
        STAFF_PAGES.members ? ['/', created.slug, 'xestion', 'concello', 'persoal'] : ['/', created.slug],
      );
    } catch (error: unknown) {
      const problem = asProblem(error);
      this.problem.set(problem);
      if (problem.code.startsWith('municipality.slug-')) {
        this.form.controls.slug.setErrors({ [problem.code]: problem.params });
      }
      for (const fieldErrorItem of problem.fieldErrors) {
        this.form.get(fieldErrorItem.field)?.setErrors({ [fieldErrorItem.code]: fieldErrorItem.params });
      }
      afterNextRender(() => focusFirstInvalid(this.formElement().nativeElement), { injector: this.injector });
    } finally {
      this.saving.set(false);
    }
  }
}
