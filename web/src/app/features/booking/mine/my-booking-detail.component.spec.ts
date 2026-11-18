// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { booking } from '../reserve/reserve.fixture';
import type { MyBookingDetailData, MyBookingDetailResult } from './my-booking-detail.component';
import { MyBookingDetailComponent } from './my-booking-detail.component';

describe('furar-my-booking-detail: accións doutras funcións', () => {
  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  async function open(data: MyBookingDetailData) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), provideRouter([])] });
    const ref = TestBed.inject(DialogService).open<
      MyBookingDetailResult,
      MyBookingDetailData,
      MyBookingDetailComponent
    >(MyBookingDetailComponent, { data });
    await vi.waitFor(() => expect(document.querySelector('[data-my-booking-detail]')).not.toBeNull());
    return { ref, layer: document.querySelector<HTMLElement>('furar-dialog-container')! };
  }

  it('van antes da destrutiva e devolven a súa clave; axe', async () => {
    const { ref, layer } = await open({
      booking: booking(),
      slug: 'naron',
      phone: null,
      extraActions: [{ id: 'match', icon: 'event-available', label: 'Buscar xente para xogar' }],
    });
    const items = [...layer.querySelectorAll('furar-action-item')];
    expect(items.map((item) => item.textContent?.trim())).toEqual(['Cambiar', 'Buscar xente para xogar', 'Cancelar']);
    await expectNoAxeViolations(layer);
    const result = firstValueFrom(ref.closed);
    layer.querySelector<HTMLButtonElement>('[data-detail-action="match"]')!.click();
    expect(await result).toEqual({ kind: 'extra', id: 'match' });
  });

  it('só con accións doutras funcións (fóra de prazo), a lista sae igual', async () => {
    const { layer } = await open({
      booking: booking({ canCancel: false }),
      slug: 'naron',
      phone: null,
      extraActions: [{ id: 'match', icon: 'event-available', label: 'Ver a partida' }],
    });
    expect([...layer.querySelectorAll('furar-action-item')].map((item) => item.textContent?.trim())).toEqual([
      'Ver a partida',
    ]);
    // Sen teléfono, só o aviso.
    expect(layer.querySelector('[data-booking-late]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Xa pasou o prazo para cancelar.',
    );
  });
});
