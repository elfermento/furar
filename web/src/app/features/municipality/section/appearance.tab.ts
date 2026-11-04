// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef, OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { LeavableTab } from '../../../core/unsaved-changes/leave-tab.guard';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { fieldError } from '../../../shared/ui/field-errors';
import { FormErrorsSummaryComponent } from '../../../shared/ui/form-errors-summary.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { growFrom, shrinkInto, slideFrom } from '../../../shared/ui/motion/motion';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { themeForm } from '../municipality-form';
import type { MunicipalityResponse } from '../municipality.store';
import { MunicipalityStore } from '../municipality.store';
import type { ThemeForm } from '../theme-editor.component';
import { ThemeEditorComponent } from '../theme-editor.component';
import { tabForm } from './tab-form';
import { TabSave } from './tab-save';
import { ThemePreviewComponent } from './theme-preview.component';

/**
 * «Aparencia» da sección «Concello» (maqueta 49): as cores en mosaicos coas frases de contraste e
 * «Vista previa» debaixo. A vista previa abre ao lado coma o detalle das reservas do concello
 * (2/3 e 1/3 desde 64rem, fixa ao baixar), pero medra desde o botón e, ao pechar, encolle de volta
 * a el (FLIP, `growFrom`/`shrinkInto`; coa mesma curva e duración). Sen movemento se a persoa o
 * pide. Segue as cores en vivo. Garda o tema co `PUT` do concello.
 */
@Component({
  selector: 'furar-municipality-appearance-tab',
  imports: [
    ButtonDirective,
    FormErrorsSummaryComponent,
    IconComponent,
    ProblemAlertComponent,
    ReactiveFormsModule,
    StatusStateComponent,
    ThemeEditorComponent,
    ThemePreviewComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-section__col',
    '[attr.data-wide]': 'open() ? "" : null',
    '(keydown.escape)': 'onEscape($event)',
  },
  templateUrl: './appearance.tab.html',
})
export class AppearanceTab implements OnInit, LeavableTab {
  private readonly store = inject(MunicipalityStore);
  private readonly transloco = inject(TranslocoService);
  private readonly translation = toSignal(this.transloco.selectTranslation());
  private readonly injector = inject(Injector);
  private readonly view = inject(DOCUMENT).defaultView;
  private readonly formElement = viewChild<ElementRef<HTMLElement>>('formElement');
  private readonly toggleButton = viewChild<ElementRef<HTMLElement>>('toggle');
  private readonly colours = viewChild<ElementRef<HTMLElement>>('colours');
  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private readonly closer = viewChild<ElementRef<HTMLElement>>('closer');
  protected readonly current = inject(CurrentMunicipality);

  protected readonly tab = tabForm<ThemeForm>('appearance');
  protected readonly saver = new TabSave();
  protected readonly open = signal(false);
  protected readonly fieldError = fieldError;

  readonly dirty = this.tab.dirty;
  readonly tabName = computed(() => {
    this.translation();
    return this.transloco.translate('section.tab.appearance');
  });

  ngOnInit(): void {
    const municipality = this.current.value();
    if (municipality) this.reset(municipality);
  }

  /** Abre a vista previa ao lado: medra desde o botón; as cores corren cara á esquerda. */
  protected show(): void {
    const origin = this.toggleButton()?.nativeElement.getBoundingClientRect();
    const before = this.colours()?.nativeElement.getBoundingClientRect();
    this.open.set(true);
    afterNextRender(
      () => {
        const panel = this.panel()?.nativeElement;
        const colours = this.colours()?.nativeElement;
        if (panel && origin) growFrom(this.view, panel, origin);
        if (colours && before) slideFrom(this.view, colours, before);
        this.closer()?.nativeElement.focus({ preventScroll: true });
      },
      { injector: this.injector },
    );
  }

  /** Esc dentro da vista previa a pecha (o foco volve ao botón). */
  protected onEscape(event: Event): void {
    if (this.open() && this.panel()?.nativeElement.contains(event.target as Node)) this.close();
  }

  /** Pecha: encolle ata o botón e o foco volve a el. */
  protected close(): void {
    shrinkInto(
      this.view,
      this.panel()?.nativeElement,
      () => this.toggleButton()?.nativeElement.getBoundingClientRect() ?? null,
      () => {
        const before = this.colours()?.nativeElement.getBoundingClientRect();
        this.open.set(false);
        afterNextRender(
          () => {
            const colours = this.colours()?.nativeElement;
            if (colours && before) slideFrom(this.view, colours, before);
            this.toggleButton()?.nativeElement.focus({ preventScroll: true });
          },
          { injector: this.injector },
        );
      },
    );
  }

  save(): Promise<boolean> {
    const form = this.tab.form();
    const municipality = this.current.value();
    if (!form || !municipality) return Promise.resolve(false);
    return this.saver.run(
      form,
      this.formElement(),
      async () => {
        const updated = await this.store.update(municipality, {
          name: municipality.name,
          languages: municipality.languages,
          defaultLanguage: municipality.defaultLanguage,
          theme: form.getRawValue(),
          customFields: municipality.customFields,
        });
        this.current.set(updated);
        this.reset(updated);
      },
      (problem) => {
        if (problem.code === 'municipality.theme-low-contrast') {
          form.setErrors({ [problem.code]: problem.params });
          return 1;
        }
        return 0;
      },
    );
  }

  discard(): void {
    const municipality = this.current.value();
    if (municipality) this.reset(municipality);
    this.saver.clear();
  }

  protected async reload(): Promise<void> {
    if (await this.saver.reloadMunicipality()) {
      const fresh = this.current.value();
      if (fresh) this.reset(fresh);
    }
  }

  private reset(municipality: MunicipalityResponse): void {
    this.tab.set(themeForm(municipality.theme));
  }
}
