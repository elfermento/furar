// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, flushMatching, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { SLUG_BASE, preview, seasonFacility, series, seriesRequest } from '../../../../testing/seasons';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { SeriesRequestPage } from './series-request.page';

const REQUEST = `${SLUG_BASE}/booking-series-requests/r1`;

describe('Revisar unha solicitude de tempada (ADR-0039, punto 11)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-12T10:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    flushMatching((r) => r.url.endsWith('/pending-count'), { count: 0 });
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const settle = async (fixture: { whenStable: () => Promise<unknown>; detectChanges: () => void }) => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

  async function render(request = seriesRequest()) {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const toast = vi.spyOn(TestBed.inject(ToastService), 'show');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(SeriesRequestPage);
    fixture.componentRef.setInput('id', 'r1');
    fixture.detectChanges();
    (await expectRequest(REQUEST)).flush(request);
    await settle(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement, toast, navigate };
  }

  /** A pendente: os campos xa enchidos, a ficha da instalación e a vista previa. */
  async function renderPending() {
    const rendered = await render();
    const { fixture } = rendered;
    (await expectRequest(`${SLUG_BASE}/public/facilities`)).flush([{ id: 'f1', name: 'Pavillón' }]);
    (await expectRequest(`${SLUG_BASE}/public/facilities/f1`)).flush(seasonFacility());
    await settle(fixture);
    const request = await expectRequest({ method: 'POST', url: `${SLUG_BASE}/booking-series/preview` });
    expect(request.request.body).toMatchObject({ organizationId: 'o2', weekdays: [5], startMinute: 1080 });
    request.flush(
      preview({ sessions: 27, conflicts: [{ ...preview().conflicts[0], kind: 'SESSION', name: 'Club Voleibol' }] }),
    );
    await settle(fixture);
    return rendered;
  }

  it('a entidade, a súa nota e quen a pediu; os campos enchidos, os choques e as dúas accións; axe', async () => {
    const { el } = await renderPending();
    const from = el.querySelector('[data-request-from]')!;
    expect(text(from)).toContain('Club Patinaxe');
    expect(text(from)).toContain('«Equipo infantil»');
    expect(text(from)).toContain('xiana@example.org · 12 de novembro de 2026');
    expect(text(el.querySelector('[data-conflict-cancel]'))).toBe('Cancelar a sesión');
    expect(text(el.querySelector('[data-request-reject]'))).toBe('Rexeitar');
    expect(text(el.querySelector('[data-request-approve]'))).toBe('Aprobar e crear 27 sesións');
    await expectNoAxeViolations(el);
  });

  it('aprobar crea coa súa clave e volve ás entidades; se xa se decidiu, o erro', async () => {
    const { fixture, el, toast, navigate } = await renderPending();
    el.querySelector<HTMLButtonElement>('[data-request-approve]')!.click();
    const first = await expectRequest({ method: 'POST', url: `${REQUEST}/approve` });
    expect(first.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(first.request.body.skip).toEqual(['2026-11-23']);
    flushProblem(first, 409, 'booking.series-request-decided');
    await settle(fixture);
    expect(text(el)).toContain('Esta solicitude xa se decidiu.');
    el.querySelector<HTMLButtonElement>('[data-request-approve]')!.click();
    (await expectRequest({ method: 'POST', url: `${REQUEST}/approve` })).flush(
      series({ organizationName: 'Club Patinaxe', sessions: 27, requestId: 'r1' }),
    );
    await settle(fixture);
    expect(toast).toHaveBeenCalledWith('booking.seasons.review.approved', { name: 'Club Patinaxe', count: 27 });
    expect(navigate).toHaveBeenCalledWith(['/', 'naron', 'xestion', 'entidades']);
  });

  it('rexeitar pide a mensaxe (obrigatoria) e avisa á entidade', async () => {
    const { fixture, el, toast, navigate } = await renderPending();
    el.querySelector<HTMLButtonElement>('[data-request-reject]')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-reject-message]')).toBeTruthy();
    });
    expect(text(document.querySelector('furar-dialog-container'))).toContain('Club Patinaxe · chégalle por correo');
    document.querySelector<HTMLButtonElement>('[data-reject-confirm]')!.click();
    await settle(fixture);
    expect(text(document.querySelector('furar-dialog-container'))).toContain('Escribe a mensaxe para a entidade.');
    const message = document.querySelector<HTMLTextAreaElement>('[data-reject-message]')!;
    message.value = 'O venres xa está asignado.';
    message.dispatchEvent(new Event('input'));
    document.querySelector<HTMLButtonElement>('[data-reject-confirm]')!.click();
    const reject = await expectRequest({ method: 'POST', url: `${REQUEST}/reject` });
    expect(reject.request.body).toEqual({ message: 'O venres xa está asignado.' });
    flushProblem(reject, 409, 'booking.series-request-decided');
    await settle(fixture);
    expect(text(document.querySelector('furar-dialog-container'))).toContain('Esta solicitude xa se decidiu.');
    document.querySelector<HTMLButtonElement>('[data-reject-confirm]')!.click();
    (await expectRequest({ method: 'POST', url: `${REQUEST}/reject` })).flush(seriesRequest({ status: 'REJECTED' }));
    await settle(fixture);
    expect(toast).toHaveBeenCalledWith('booking.seasons.review.rejected', { name: 'Club Patinaxe' });
    expect(navigate).toHaveBeenCalledWith(['/', 'naron', 'xestion', 'entidades']);
  });

  it('pechar «Rexeitar» sen escribir non fai nada', async () => {
    const { fixture, el, navigate } = await renderPending();
    el.querySelector<HTMLButtonElement>('[data-request-reject]')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-reject-keep]')).toBeTruthy();
    });
    document.querySelector<HTMLButtonElement>('[data-reject-keep]')!.click();
    await settle(fixture);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('xa decidida: o estado e a mensaxe, sen campos nin accións; se non existe, o erro', async () => {
    const { el } = await render(seriesRequest({ status: 'REJECTED', rejectionMessage: 'Non hai oco.' }));
    expect(text(el.querySelector('[data-chip="REJECTED"]'))).toBe('Non aprobada');
    expect(text(el)).toContain('Non hai oco.');
    expect(el.querySelector('furar-season-form')).toBeNull();
    expect(el.querySelector('[data-request-approve]')).toBeNull();
  });

  it('se a solicitude non existe, o erro coa volta', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(SeriesRequestPage);
    fixture.componentRef.setInput('id', 'r1');
    fixture.detectChanges();
    flushProblem(await expectRequest(REQUEST), 404, 'booking.series-request-not-found');
    await settle(fixture);
    expect(text(fixture.nativeElement as HTMLElement)).toContain('Non se puido cargar a solicitude.');
  });
});
