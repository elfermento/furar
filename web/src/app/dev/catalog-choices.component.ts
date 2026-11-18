// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../shared/ui/button/button.directive';
import { CardComponent } from '../shared/ui/card/card.component';
import { IconComponent } from '../shared/ui/icon/icon.component';
import { SegmentedOptionDirective, SegmentedRadioDirective } from '../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../shared/ui/segmented/segmented.directive';
import { TabDirective, TabsComponent } from '../shared/ui/tabs/tabs.component';
import { TagComponent } from '../shared/ui/tag/tag.component';

type RentalMode = 'WHOLE' | 'PARTS' | 'SEATS';

/** Sección «Segmentado e pestanas» do catálogo (só desenvolvemento). */
@Component({
  selector: 'furar-catalog-choices',
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    ButtonDirective,
    CardComponent,
    IconComponent,
    SegmentedDirective,
    SegmentedRadioDirective,
    SegmentedOptionDirective,
    TabsComponent,
    TabDirective,
    TagComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './catalog-choices.component.html',
})
export class CatalogChoicesComponent {
  protected readonly modes: RentalMode[] = ['WHOLE', 'PARTS', 'SEATS'];
  protected readonly durations = [30, 60, 90, 120];
  protected readonly use = new FormGroup({
    mode: new FormControl<RentalMode>('WHOLE', { nonNullable: true }),
    duration: new FormControl<number | null>(null),
    locked: new FormControl<RentalMode>({ value: 'PARTS', disabled: true }, { nonNullable: true }),
  });
  /** Tempadas de exemplo; «Inverno» é a de agora. */
  protected readonly seasons = ['winter', 'summer', 'christmas'] as const;
  protected readonly season = signal<string | null>('winter');
}
