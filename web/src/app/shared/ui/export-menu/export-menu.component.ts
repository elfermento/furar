// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ConnectedPosition } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActionScope } from '../action/action-scope';
import type { ButtonSize } from '../button/button.directive';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';
import { MENU } from '../menu/menu.directives';

/** Unha exportación: o que fai PDF ou CSV (se devolve unha promesa, o botón queda ocupado ata que remata). */
export type ExportAction = () => unknown;

/** Xusto debaixo, aliñado á dereita do botón; se non cabe, á esquerda ou enriba. */
const POSITIONS: ConnectedPosition[] = [
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
];

/**
 * «Exportar» (D3-07; docs/deseno.md §8): botón que abre debaixo un menú pequeno con PDF e CSV,
 * cada un coa súa axuda curta. Patrón APG «menu button» sobre `[furarMenu]`: foco no primeiro,
 * frechas, Esc ou Tab pechan e devolven o foco, pecha ao escoller e ao picar fóra.
 *
 * Non exporta por si mesmo: corre `pdf` ou `csv`, que pasa quen o usa (`printWithScope`,
 * `downloadCsv`), como acción do contexto (`ActionScope`): mentres dura, «Exportar» queda ocupado
 * (`aria-busy`, co foco) e o resto de accións do contexto, bloqueadas.
 * As axudas din que sae en cada sitio («este gráfico», «o panel tal cal»); por defecto, as xerais.
 */
@Component({
  selector: 'furar-export-menu',
  imports: [TranslocoPipe, ButtonDirective, IconComponent, ...MENU],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-export-menu' },
  template: `
    <button
      type="button"
      furarButton
      class="f-export-menu__trigger"
      [size]="size()"
      [busy]="busy()"
      [furarMenuTrigger]="menu"
      [menuPosition]="positions"
      [attr.aria-label]="subject() ? ('ui.exportMenu.named' | transloco: { subject: subject() }) : null"
    >
      {{ 'ui.exportMenu.label' | transloco }}
      <furar-icon name="chevron-down" />
    </button>
    <ng-template #menu>
      <div furarMenu class="f-export-menu__panel" [attr.aria-label]="'ui.exportMenu.label' | transloco">
        <button type="button" furarMenuItem class="f-export-menu__item" data-format="pdf" (triggered)="run(pdf())">
          <span class="f-export-menu__format">{{ 'ui.exportMenu.pdf' | transloco }} </span>
          <span class="f-export-menu__hint">{{ pdfHint() || ('ui.exportMenu.pdfHint' | transloco) }}</span>
        </button>
        <button type="button" furarMenuItem class="f-export-menu__item" data-format="csv" (triggered)="run(csv())">
          <span class="f-export-menu__format">{{ 'ui.exportMenu.csv' | transloco }} </span>
          <span class="f-export-menu__hint">{{ csvHint() || ('ui.exportMenu.csvHint' | transloco) }}</span>
        </button>
      </div>
    </ng-template>
  `,
})
export class ExportMenuComponent {
  /** Que se exporta, para o nome do botón cando hai varios: «Exportar: Ocupación por instalación». */
  readonly subject = input('');
  readonly size = input<ButtonSize>('sm');
  /** Axuda do PDF, xa traducida. */
  readonly pdfHint = input('');
  /** Axuda do CSV, xa traducida. */
  readonly csvHint = input('');
  /** Que fai o PDF. */
  readonly pdf = input.required<ExportAction>();
  /** Que fai o CSV. */
  readonly csv = input.required<ExportAction>();

  protected readonly positions = POSITIONS;
  protected readonly busy = signal(false);
  private readonly scope = inject(ActionScope);

  protected async run(action: ExportAction): Promise<void> {
    await this.scope.run(async () => {
      this.busy.set(true);
      try {
        await action();
      } finally {
        this.busy.set(false);
      }
    }, this);
  }
}
