// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { SessionService } from '../../../core/auth/session.service';
import { MyOrganizationsStore } from '../seasons/my-organizations.store';
import { readSelection } from './reserve';

/**
 * «Reservar como» na páxina de reservar (ADR-0039): a persoa responsable dunha entidade
 * reserva para ela ou para a entidade, coas mesmas condicións. A escolla vive no URL (`?como=`,
 * coma o resto) e só vale se segue sendo responsable desa entidade. Só na páxina da persoa (no
 * mostrador non se dá: o persoal ten conta de traballo).
 */
@Injectable()
export class ReserveAs {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly store = inject(MyOrganizationsStore);
  private readonly params = toSignal(this.route.queryParams, { initialValue: this.route.snapshot.queryParams });

  /** As entidades das que é responsable no concello (baleiro sen sesión). */
  readonly organizations = computed(() => (this.session.authenticated() ? this.store.items() : []));
  /** A entidade escollida, se segue sendo responsable dela; se non, `null` (a persoa). */
  readonly organizationId = computed(() => {
    const id = readSelection(this.params()).organizationId;
    return id && this.organizations().some((organization) => organization.id === id) ? id : null;
  });

  load(slug: string): void {
    if (this.session.authenticated()) void this.store.load(slug);
  }

  /** Unha entidade ou (`null`) a persoa. */
  select(organizationId: string | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { como: organizationId },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
