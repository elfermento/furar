// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ThemeColors } from '../core/theme/contrast';
import { ThemeService } from '../core/theme/theme.service';
import { DEFAULT_THEME } from '../core/theme/theme-tokens';
import { ButtonDirective } from '../shared/ui/button/button.directive';
import { CatalogBookingComponent } from './catalog-booking.component';
import { CatalogChartsComponent } from './catalog-charts.component';
import { CatalogChoicesComponent } from './catalog-choices.component';
import { CatalogIndicatorsComponent } from './catalog-indicators.component';
import { CatalogDatesComponent } from './catalog-dates.component';
import { CatalogEditComponent } from './catalog-edit.component';
import { CatalogMapComponent } from './catalog-map.component';
import { CatalogEditingComponent } from './catalog-editing.component';
import { CatalogMediaComponent } from './catalog-media.component';
import { CatalogOverlaysComponent } from './catalog-overlays.component';
import { CatalogSearchComponent } from './catalog-search.component';
import { CatalogSelectComponent } from './catalog-select.component';
import { CalloutComponent } from '../shared/ui/callout/callout.component';
import { CardComponent } from '../shared/ui/card/card.component';
import { FieldsetDirective } from '../shared/ui/card/fieldset.directive';
import { FieldComponent } from '../shared/ui/field.component';
import { IconComponent } from '../shared/ui/icon/icon.component';
import { InputDirective } from '../shared/ui/input/input.directive';
import { PageHeaderComponent } from '../shared/ui/page-header/page-header.component';
import { RowListDirective } from '../shared/ui/row/row-list.directive';
import { RowComponent } from '../shared/ui/row/row.component';
import { SegmentedDirective } from '../shared/ui/segmented/segmented.directive';
import { StatusStateComponent } from '../shared/ui/status-state/status-state.component';
import { TagComponent } from '../shared/ui/tag/tag.component';
import { TimePickerComponent } from '../shared/ui/time-picker/time-picker.component';
import { ToastService } from '../shared/ui/toast/toast.service';
import { RequiredNoteComponent } from '../shared/ui/required/required.component';

type SampleTheme = 'default' | 'limit' | 'dark';

/** Temas de proba (docs/deseno.md §4.4): o por defecto, o máis xusto que admite a API e un escuro. */
const THEMES: Record<SampleTheme, ThemeColors> = {
  default: DEFAULT_THEME,
  limit: { primary: '#949494', onPrimary: '#000000', accent: '#767676', background: '#ffffff', text: '#767676' },
  dark: { primary: '#4493f8', onPrimary: '#0a0a0a', accent: '#f0b46a', background: '#0d1117', text: '#f0f6fc' },
};

/**
 * Catálogo dos compoñentes de shared/ui (só en desenvolvemento: a ruta non existe no build de
 * produción, app.routes.ts). Serve para revisar estados, temas límite e preferencias do sistema.
 */
@Component({
  selector: 'furar-catalog-page',
  imports: [
    RequiredNoteComponent,
    TranslocoPipe,
    ReactiveFormsModule,
    ButtonDirective,
    CatalogBookingComponent,
    CatalogChoicesComponent,
    CatalogDatesComponent,
    CatalogEditComponent,
    CalloutComponent,
    CardComponent,
    FieldsetDirective,
    FieldComponent,
    IconComponent,
    InputDirective,
    PageHeaderComponent,
    RowListDirective,
    RowComponent,
    SegmentedDirective,
    StatusStateComponent,
    TagComponent,
    TimePickerComponent,
    CatalogOverlaysComponent,
    CatalogSearchComponent,
    CatalogSelectComponent,
    CatalogMapComponent,
    CatalogEditingComponent,
    CatalogMediaComponent,
    CatalogChartsComponent,
    CatalogIndicatorsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './catalog.page.html',
})
export class CatalogPage {
  private readonly theme = inject(ThemeService);
  protected readonly themes = Object.keys(THEMES) as SampleTheme[];
  protected readonly current = signal<SampleTheme>('default');
  /** Os tons dos avisos fixos; o de «feito» vai en toast (docs/deseno.md §8, «Toast de feito»). */
  protected readonly tones = ['info', 'warn', 'danger'] as const;
  protected readonly toasts = inject(ToastService);
  protected readonly tagTones = ['neutral', 'primary', 'success', 'warn', 'danger', 'info', 'muted'] as const;
  protected readonly tagLooks = ['solid', 'outline'] as const;
  protected readonly requiredError = { key: 'errors.validation.required', params: {} };
  /** Un tramo de exemplo en minutos (8:00–14:00) e un selector desactivado. */
  protected readonly hours = new FormGroup({
    from: new FormControl(480, { nonNullable: true }),
    to: new FormControl(840, { nonNullable: true }),
    closed: new FormControl<number | null>({ value: null, disabled: true }),
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.theme.reset());
  }

  protected use(name: SampleTheme): void {
    this.current.set(name);
    void this.theme.apply(THEMES[name]);
  }
}
