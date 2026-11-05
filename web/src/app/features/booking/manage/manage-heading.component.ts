// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';

/**
 * Cabeceira de «Reservas»: o título e «Nova reserva» (maqueta 58, opción A), á vista tamén no
 * móbil (o botón baixa se non cabe). Vena a xestión e Recepción: as dúas reservan no nome de alguén.
 */
@Component({
  selector: 'furar-manage-heading',
  imports: [ButtonDirective, IconComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-manage__head' },
  template: `
    <h1>{{ 'booking.manage.title' | transloco }}</h1>
    @if (staff.canViewBookings()) {
      <a furarButton="primary" data-manage-new [routerLink]="['/', slug(), 'xestion', 'reservas', 'nova']">
        <furar-icon name="plus" />
        {{ 'booking.manage.new' | transloco }}
      </a>
    }
  `,
})
export class ManageHeadingComponent {
  readonly slug = input.required<string>();
  protected readonly staff = inject(StaffAccessService);
}
