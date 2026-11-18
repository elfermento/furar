// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, flushMatching, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { SLUG_BASE, organization, organizationRow, seriesRequest } from '../../../../testing/seasons';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { OrganizationsPage } from './organizations.page';

const ORGS = `${SLUG_BASE}/organizations`;
const REQUESTS = `${SLUG_BASE}/booking-series-requests`;

describe('Entidades (ADR-0039)', () => {
  afterEach(() => {
    // O número da pestana (o contador) pídese só ao aprobar, rexeitar ou eliminar.
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
  const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');

  async function render(
    rows = [
      organizationRow(),
      organizationRow({ id: 'o2', name: 'Club Patinaxe', status: 'REQUEST', managers: 0, phone: undefined }),
    ],
    requests = [seriesRequest()],
  ) {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(OrganizationsPage);
    fixture.detectChanges();
    (await expectRequest((r) => r.url === ORGS)).flush(rows);
    (await expectRequest((r) => r.url === REQUESTS)).flush(requests);
    await settle(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('as solicitudes en tarxetas pulsables e a táboa co estado en chip; o filtro do estado; axe', async () => {
    const { fixture, el } = await render();
    const card = el.querySelector('[data-series-request="r1"]')!;
    expect(text(card.querySelector('.f-status-card__name'))).toBe('Club Patinaxe');
    expect(text(card.querySelector('[data-line="place"]'))).toBe('Pavillón · Pista central · Patinaxe');
    expect(text(card.querySelector('[data-line="when"]'))).toBe('venres · 18:00–19:30 · do 20 nov. ao 18 xuño');
    expect(text(card.querySelector('[data-chip="PENDING"]'))).toBe('Pendente');
    expect(text(card.querySelector('.f-season-ago'))).toMatch(/^solicitada /);
    const rows = [...el.querySelectorAll('[data-organization-row]')];
    expect(rows.map((row) => text(row.querySelector('th')))).toEqual(['Club Baloncesto', 'Club Patinaxe']);
    expect(text(rows[0].querySelector('[data-status]'))).toBe('En tempada');
    expect(text(rows[1].querySelectorAll('td')[2])).toBe('Sen responsables');
    expect(text(el.querySelector('[data-organization-item="o1"] .f-orgs__item-meta'))).toBe(
      '981 000 210 · Iria Lago · 2 responsables',
    );
    await expectNoAxeViolations(el);

    // O filtro do estado deixa só as que teñen solicitude.
    (fixture.componentInstance as unknown as { toggle: (v: string) => void }).toggle('REQUEST');
    await settle(fixture);
    expect(el.querySelectorAll('[data-organization-row]')).toHaveLength(1);
    (fixture.componentInstance as unknown as { toggle: (v: string) => void }).toggle('REQUEST');
    await settle(fixture);
    expect(el.querySelectorAll('[data-organization-row]')).toHaveLength(2);
  });

  it('picar unha solicitude leva a revisala', async () => {
    const { el } = await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    el.querySelector<HTMLButtonElement>('[data-series-request="r1"] .f-status-card__open')!.click();
    expect(navigate).toHaveBeenCalledWith(['/', 'naron', 'xestion', 'entidades', 'solicitudes', 'r1']);
  });

  it('sen entidades: o baleiro e sen solicitudes; se falla, «Tentar de novo»', async () => {
    const { fixture, el } = await render([], []);
    expect(el.querySelector('[data-series-requests]')).toBeNull();
    expect(el.querySelector('[data-organizations-empty]')).not.toBeNull();
    (fixture.componentInstance as unknown as { load: () => void }).load();
    flushProblem(await expectRequest((r) => r.url === ORGS), 500, 'common.unexpected');
    flushMatching((r) => r.url === REQUESTS, []);
    await settle(fixture);
    expect(text(el)).toContain('Non se puideron cargar as entidades.');
  });

  it('«Nova entidade»: o correo é obrigatorio, os responsables con Intro e en chips; gardar crea e avisa', async () => {
    const { fixture, el } = await render([], []);
    const toast = vi.spyOn(TestBed.inject(ToastService), 'show');
    el.querySelector<HTMLButtonElement>('[data-organization-new]')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-organization-name]')).toBeTruthy();
    });
    const d = dialog()!;
    expect(d.querySelector('[data-organization-delete]')).toBeNull();
    d.querySelector<HTMLButtonElement>('[data-organization-save]')!.click();
    await settle(fixture);
    expect(d.querySelector('[data-organization-name]')?.getAttribute('aria-invalid')).toBe('true');
    expect(d.querySelector('[data-organization-email]')?.getAttribute('aria-invalid')).toBe('true');

    const type = (selector: string, value: string) => {
      const input = d.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    type('[data-organization-name]', 'Escola de Fútbol');
    type('[data-organization-email]', 'escola@example.org');
    type('[data-organization-phone]', '600 000 118');
    const manager = d.querySelector<HTMLInputElement>('[data-organization-manager]')!;
    type('[data-organization-manager]', 'non-e-correo');
    manager.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle(fixture);
    expect(manager.getAttribute('aria-invalid')).toBe('true');
    type('[data-organization-manager]', 'adestra@example.org');
    manager.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    type('[data-organization-manager]', 'ADESTRA@example.org');
    manager.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle(fixture);
    expect([...d.querySelectorAll('.f-chip__label')].map(text)).toEqual(['adestra@example.org']);
    // Quitar un chip e volver engadilo.
    d.querySelector<HTMLButtonElement>('[data-chip-remove="0"]')!.click();
    await settle(fixture);
    expect(d.querySelectorAll('.f-chip__label')).toHaveLength(0);
    type('[data-organization-manager]', 'adestra@example.org');

    d.querySelector<HTMLButtonElement>('[data-organization-save]')!.click();
    const post = await expectRequest({ method: 'POST', url: ORGS });
    expect(post.request.body).toEqual({
      name: 'Escola de Fútbol',
      email: 'escola@example.org',
      phone: '600 000 118',
      managers: ['adestra@example.org'],
    });
    post.flush(organization({ id: 'o3', name: 'Escola de Fútbol' }));
    const reload = await expectRequest((r) => r.url === ORGS);
    // Mentres se actualiza, a táboa segue á vista: o foco pode volver ao botón que abriu o diálogo.
    await settle(fixture);
    expect(el.querySelector('[data-organizations-table]')).not.toBeNull();
    reload.flush([organizationRow({ id: 'o3', name: 'Escola de Fútbol' })]);
    (await expectRequest((r) => r.url === REQUESTS)).flush([]);
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()).toBeNull();
    });
    expect(toast).toHaveBeenCalledWith('booking.seasons.organization.saved', { name: 'Escola de Fútbol' });
    // A lista da páxina (non outra) queda coa entidade nova.
    expect(el.querySelector('[data-organization-row="o3"]')).not.toBeNull();
  });

  it('o nome repetido vai ao campo; con máis de dez responsables, o erro no campo', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('[data-organization-row="o1"] .f-orgs__open')!.click();
    (await expectRequest(`${ORGS}/o1`)).flush(
      organization({ managers: Array.from({ length: 10 }, (_, i) => `m${i}@example.org`) }),
    );
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-organization-name]')).toBeTruthy();
    });
    const d = dialog()!;
    expect(d.querySelector<HTMLInputElement>('[data-organization-name]')!.value).toBe('Club Baloncesto');
    const manager = d.querySelector<HTMLInputElement>('[data-organization-manager]')!;
    manager.value = 'outro@example.org';
    manager.dispatchEvent(new Event('input'));
    manager.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle(fixture);
    expect(text(d)).toContain('Unha entidade pode ter ata 10 responsables.');
    manager.value = '';
    manager.dispatchEvent(new Event('input'));
    manager.dispatchEvent(new Event('blur'));
    d.querySelector<HTMLButtonElement>('[data-organization-save]')!.click();
    const put = await expectRequest({ method: 'PUT', url: `${ORGS}/o1` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    flushProblem(put, 409, 'booking.organization-name-taken');
    await settle(fixture);
    expect(d.querySelector('[data-organization-name]')?.getAttribute('aria-invalid')).toBe('true');
    expect(text(d)).toContain('Xa hai unha entidade con ese nome.');
  });

  it('eliminar pregunta; con sesións futuras, o erro queda no diálogo; ben, pecha e avisa', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('[data-organization-item="o1"]')!.click();
    (await expectRequest(`${ORGS}/o1`)).flush(organization());
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-organization-delete]')).toBeTruthy();
    });
    const remove = async () => {
      dialog()!.querySelector<HTMLButtonElement>('[data-organization-delete] button')!.click();
      await vi.waitFor(async () => {
        await settle(fixture);
        expect(document.querySelector('[data-confirm-accept]')).toBeTruthy();
      });
      document.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
      return expectRequest({ method: 'DELETE', url: `${ORGS}/o1` });
    };
    flushProblem(await remove(), 409, 'booking.organization-has-future-bookings');
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(text(dialog())).toContain('A entidade ten sesións ou reservas futuras.');
    });
    (await remove()).flush(null);
    // A fila sae xa, sen agardar a recarga; o foco, que volvería a ela, vai ao título (WCAG 2.4.3).
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-organization-name]')).toBeNull();
      expect(el.querySelector('[data-organization-item="o1"]')).toBeNull();
      expect(document.activeElement).toBe(el.querySelector('h1'));
    });
    (await expectRequest((r) => r.url === ORGS)).flush([]);
    (await expectRequest((r) => r.url === REQUESTS)).flush([]);
    await settle(fixture);
    expect(document.activeElement).toBe(el.querySelector('h1'));
  });

  it('se non se pode abrir unha entidade, un aviso e sen diálogo', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('[data-organization-row="o1"] .f-orgs__open')!.click();
    flushProblem(await expectRequest(`${ORGS}/o1`), 404, 'booking.organization-not-found');
    await settle(fixture);
    expect(dialog()).toBeNull();
    expect(TestBed.inject(ToastService).current()).toMatchObject({
      key: 'booking.seasons.organizations.loadOne',
      tone: 'danger',
      reason: { key: 'errors.booking.organization-not-found' },
    });
  });
});
