// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, booleanAttribute, computed, inject, input, model } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { FieldLineComponent } from '../../../shared/ui/field-line/field-line.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';

/** As páxinas que xa leron `?tipo=` (unha vez cada unha). */
const READ = new WeakSet<ActivatedRoute>();

/** Unha reserva dunha vez ou unha tempada dunha entidade. */
export type BookingKind = 'once' | 'season';

/**
 * «Tipo de reserva» en «Nova reserva»: unha fila solta co segmentado «Unha vez ·
 * Tempada» (se non cabe ao carón do texto, debaixo e á esquerda). Só a xestión (o permiso das entidades) e nunha reserva nova (`changing`, sen fila). A
 * escolla vai no URL (`?tipo=tempada`), así recargar deixa a xestora no mesmo sitio.
 */
@Component({
  selector: 'furar-booking-kind',
  imports: [FieldLineComponent, SegmentedDirective, SegmentedOptionDirective, SegmentedRadioDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (shown()) {
      <furar-field-line #line data-book-for-kind wrap="start" [label]="'booking.bookFor.kind.label' | transloco">
        <div
          furarSegmented
          mode="radio"
          [value]="kind()"
          (valueChange)="pick($event)"
          [attr.aria-labelledby]="line.labelId()"
        >
          <button type="button" furarSegmentedOption="once" data-kind="once">
            {{ 'booking.bookFor.kind.once' | transloco }}
          </button>
          <button type="button" furarSegmentedOption="season" data-kind="season">
            {{ 'booking.bookFor.kind.season' | transloco }}
          </button>
        </div>
      </furar-field-line>
    }
  `,
})
export class BookingKindComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly staff = inject(StaffAccessService);

  /** O escollido (dobre vínculo); ao crearse, o do URL. */
  readonly kind = model<BookingKind>('once');
  /** Cambiando unha reserva: sen fila. */
  readonly changing = input(false, { transform: booleanAttribute });

  protected readonly shown = computed(() => !this.changing() && this.staff.canManage());

  /**
   * O do URL, unha vez por páxina (a fila vai nos dous formularios e recréase ao cambiar) e despois
   * de ligar o dobre vínculo (no construtor, a páxina non o sabería).
   */
  ngOnInit(): void {
    if (READ.has(this.route)) return;
    READ.add(this.route);
    if (this.route.snapshot.queryParamMap.get('tipo') === 'tempada') this.kind.set('season');
  }

  protected pick(kind: BookingKind | null): void {
    if (!kind || kind === this.kind()) return;
    this.kind.set(kind);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tipo: kind === 'season' ? 'tempada' : null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
