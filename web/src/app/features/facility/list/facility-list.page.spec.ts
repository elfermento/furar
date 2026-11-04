// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ChipMenuComponent } from '../../../shared/ui/menu/chip-menu.component';
import { AffectedBookingsFlow } from '../affected-bookings/affected-bookings.flow';
import type { BookingDecision } from '../affected-bookings/affected-bookings.store';
import { ADDRESS, facility } from '../detail/facility.fixture';
import { FacilityListPage, cardAddress } from './facility-list.page';
import type { FacilitySummary } from './facility.store';
import { FacilityStore, PAGE_SIZE, toSummary } from './facility.store';

const LIST = '/api/v1/municipalities/naron/facilities';
const listUrl = (page = 0) => `${LIST}?page=${page}&size=${PAGE_SIZE}`;

function summary(overrides: Partial<FacilitySummary> = {}): FacilitySummary {
  return { ...toSummary(facility({ missing: [] })), ...overrides };
}

/** Na orde do servidor (alfabética no idioma de quen mira): a web non a cambia. */
const FACILITIES = [
  summary({ id: 'f1', name: 'Campo de fútbol 1 de O Alto' }),
  summary({
    id: 'f2',
    name: 'Pabellón de Piñeiros',
    status: 'TEMPORARILY_CLOSED',
    version: 7,
    address: { ...ADDRESS, street: 'Estrada de Castela', streetNumber: '210' },
  }),
  summary({ id: 'f3', name: 'Pista de atletismo', missing: ['USES', 'SCHEDULE'] }),
];

const page = (items: FacilitySummary[], total = items.length, at = 0) => ({
  items,
  page: at,
  size: PAGE_SIZE,
  totalItems: total,
});

describe('FacilityListPage: listado de instalacións (D1-20)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(items: FacilitySummary[] | null = FACILITIES, providers: unknown[] = []) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), ...(providers as never[])] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(FacilityListPage);
    const el = fixture.nativeElement as HTMLElement;
    const stable = async () => {
      await new Promise((resolve) => setTimeout(resolve));
      await fixture.whenStable();
    };
    await fixture.whenStable();
    if (items) {
      (await expectRequest(listUrl())).flush(page(items));
      await vi.waitFor(async () => {
        await stable();
        expect(el.querySelector('[data-facility], furar-status-state:not([data-mode="skeleton"])')).not.toBeNull();
      });
    }
    const cards = () => [...el.querySelectorAll<HTMLElement>('[data-facility]')];
    const chip = (index: number) =>
      fixture.debugElement.queryAll(By.directive(ChipMenuComponent))[index].componentInstance as ChipMenuComponent;
    return { fixture, el, stable, cards, chip };
  }

  it('tarxetas na orde do servidor: ligazón enteira á ficha, enderezo e chip como botón aparte', async () => {
    const { el, cards } = await render();
    expect(el.querySelectorAll('h1')).toHaveLength(1);
    expect(el.querySelector('h1')?.textContent).toContain('Instalacións');
    expect(cards().map((c) => c.querySelector('a')?.textContent?.trim())).toEqual([
      'Campo de fútbol 1 de O Alto',
      'Pabellón de Piñeiros',
      'Pista de atletismo',
    ]);
    const [first, second, third] = cards();
    // Unha soa ligazón por tarxeta (estirada a toda ela) e o chip, botón propio por riba.
    expect(first.querySelectorAll('a')).toHaveLength(1);
    expect(first.querySelector('a')?.getAttribute('href')).toBe('/f1');
    expect(first.querySelector('.f-media-card__place')?.textContent?.trim()).toBe('Rúa do Alto, 12 · 15570 Narón');
    expect(second.querySelector('.f-media-card__place')?.textContent?.trim()).toBe(
      'Estrada de Castela, 210 · 15570 Narón',
    );
    // Imaxe de portada (a primeira ou o debuxo suxerido) e deportes en etiquetas soft.
    expect(first.querySelector('img')?.getAttribute('src')).toBeTruthy();
    expect(first.querySelectorAll('furar-tag[data-look="soft"]').length).toBe(facility().details.sports.length);
    expect(first.querySelector('furar-chip-menu button')?.getAttribute('aria-label')).toBe(
      'Estado de Campo de fútbol 1 de O Alto: Activa. Cambiar',
    );
    expect(second.querySelector('furar-chip-menu button')?.textContent).toContain('Pechada temporalmente');
    // Borrador ao lado do chip só se lle falta algo para publicarse (P15).
    expect(first.querySelector('[data-draft]')).toBeNull();
    expect(third.querySelector('[data-draft]')?.textContent?.trim()).toBe('Borrador');
    expect(el.querySelector('[data-new]')?.textContent).toContain('Nova instalación');
    await expectNoAxeViolations(el);
  });

  it('cargando con esqueleto e erro con «Tentar de novo»', async () => {
    const { el, stable } = await render(null);
    expect(el.querySelector('furar-status-state[data-mode="skeleton"]')?.getAttribute('role')).toBe('status');
    flushProblem(await expectRequest(listUrl()), 500, 'common.internal');
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-status-state[data-mode="error"]')?.textContent).toContain(
        'Non se puideron cargar as instalacións',
      );
    });
    await expectNoAxeViolations(el);
    el.querySelector<HTMLButtonElement>('furar-status-state button')!.click();
    (await expectRequest(listUrl())).flush(page(FACILITIES));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelectorAll('[data-facility]')).toHaveLength(3);
    });
  });

  it('baleiro: aviso con «Nova instalación» e sen o botón da cabeceira', async () => {
    const { el } = await render([]);
    const empty = el.querySelector('furar-status-state[data-mode="empty"]');
    expect(empty?.textContent).toContain('Aínda non hai instalacións');
    expect(el.querySelectorAll('[data-new]')).toHaveLength(1);
    expect(empty?.querySelector('[data-new]')?.textContent).toContain('Nova instalación');
    await expectNoAxeViolations(el);
  });

  it('cambia o estado coa versión da tarxeta e avisa; «De baixa» pide confirmación (P17)', async () => {
    const { el, stable, cards, chip } = await render();
    chip(1).selected.emit('ACTIVE');
    const put = await expectRequest({ method: 'PUT', url: `${LIST}/f2/status` });
    expect(put.request.headers.get('If-Match')).toBe('"7"');
    expect(put.request.body).toEqual({ status: 'ACTIVE' });
    put.flush(facility({ id: 'f2', name: 'Pabellón de Piñeiros', version: 8, status: 'ACTIVE', missing: [] }));
    await vi.waitFor(async () => {
      await stable();
      expect(cards()[1].querySelector('furar-chip-menu button')?.textContent).toContain('Activa');
    });
    expect(TestBed.inject(ToastService).current()).toMatchObject({
      key: 'facility.list.statusChanged',
      params: { name: 'Pabellón de Piñeiros', status: 'Activa' },
    });

    // A seguinte vai xa coa versión nova da tarxeta.
    chip(1).selected.emit('TEMPORARILY_CLOSED');
    const again = await expectRequest({ method: 'PUT', url: `${LIST}/f2/status` });
    expect(again.request.headers.get('If-Match')).toBe('"8"');
    again.flush(facility({ id: 'f2', version: 9, status: 'TEMPORARILY_CLOSED', missing: [] }));
    await stable();

    chip(0).selected.emit('WITHDRAWN');
    await stable();
    expect(document.body.textContent).toContain('Dar de baixa?');
    expect(document.body.textContent).toContain('«Campo de fútbol 1 de O Alto» deixa de verse');
    document.querySelector<HTMLButtonElement>('[data-confirm-cancel]')!.click();
    await stable();
    backend().expectNone({ method: 'PUT', url: `${LIST}/f1/status` });
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('');
  });

  it('con reservas afectadas reenvía co que se decidiu no modal (D1-19)', async () => {
    const decisions: BookingDecision[] = [{ bookingId: 'b1', action: 'KEEP', channels: [] }];
    const flow = {
      run: vi.fn(async (send: (d?: BookingDecision[]) => Promise<unknown>, options: { summary?: string }) => {
        expect(options.summary).toBe('Campo de fútbol 1 de O Alto: Pechada temporalmente');
        return { done: true, value: await send(decisions) };
      }),
    };
    const { chip } = await render(FACILITIES, [{ provide: AffectedBookingsFlow, useValue: flow }]);
    chip(0).selected.emit('TEMPORARILY_CLOSED');
    const put = await expectRequest({ method: 'PUT', url: `${LIST}/f1/status` });
    expect(put.request.body).toEqual({ status: 'TEMPORARILY_CLOSED', decisions });
    put.flush(facility({ version: 4, status: 'TEMPORARILY_CLOSED' }));
    await vi.waitFor(() => expect(flow.run).toHaveBeenCalledTimes(1));
  });

  it('412: aviso e listado ao día sen perder a páxina', async () => {
    const { el, stable, cards, chip } = await render();
    chip(0).selected.emit('TEMPORARILY_CLOSED');
    flushProblem(await expectRequest({ method: 'PUT', url: `${LIST}/f1/status` }), 412, 'common.stale-version');
    (await expectRequest(listUrl())).flush(page([{ ...FACILITIES[0], status: 'WITHDRAWN', version: 5 }]));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('[role="alert"]')?.textContent).toContain('cambiaron');
      expect(cards()).toHaveLength(1);
    });
    expect(cards()[0].querySelector('furar-chip-menu button')?.textContent).toContain('De baixa');
  });

  it('en castelán: textos e lista pedida de novo para a orde do idioma', async () => {
    const { el, stable } = await render();
    TestBed.inject(TranslocoService).setActiveLang('es');
    (await expectRequest(listUrl())).flush(page(FACILITIES));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('h1')?.textContent).toContain('Instalaciones');
    });
    expect(el.querySelectorAll('[data-facility]')[1].querySelector('furar-chip-menu button')?.textContent).toContain(
      'Cerrada temporalmente',
    );
    expect(el.querySelector('[data-draft]')?.textContent?.trim()).toBe('Borrador');
    TestBed.inject(TranslocoService).setActiveLang('gl');
    (await expectRequest(listUrl())).flush(page(FACILITIES));
  });

  it('un borrador sen publicar: abre o asistente, «Borrador» e o paso onde segue, sen chip de estado', async () => {
    const draft = summary({ id: 'f4', name: 'Ximnasio novo', published: false, missing: ['USES', 'SCHEDULE'] });
    const { cards } = await render([draft]);
    const [card] = cards();
    expect(card.querySelector('a')?.getAttribute('href')).toBe('/nova/f4');
    expect(card.querySelector('[data-draft]')?.textContent?.trim()).toBe('Borrador');
    expect(card.querySelector('[data-draft-step]')?.textContent?.trim()).toBe('Paso 5 de 8');
    expect(card.querySelector('furar-chip-menu')).toBeNull();
  });
});

describe('cardAddress', () => {
  it('enderezo sen número: só vía, CP e localidade', () => {
    const address = { ...ADDRESS, streetNumber: undefined };
    expect(cardAddress(summary({ address }))).toBe('Rúa do Alto · 15570 Narón');
  });
});

describe('FacilityStore', () => {
  afterEach(() => backend().verify());

  it('carga todas as páxinas', async () => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), FacilityStore] });
    const store = TestBed.inject(FacilityStore);
    const many = Array.from({ length: PAGE_SIZE }, (_, i) => summary({ id: `a${i}`, name: `A${i}` }));
    const loading = store.load('naron');
    (await expectRequest(listUrl(0))).flush(page(many, PAGE_SIZE + 1));
    (await expectRequest(listUrl(1))).flush(page([summary({ id: 'z', name: 'Z' })], PAGE_SIZE + 1, 1));
    await loading;
    expect(store.state()).toBe('ready');
    expect(store.facilities()).toHaveLength(PAGE_SIZE + 1);
    expect(store.facilities().at(-1)?.id).toBe('z');
  });

  it('con `?nova=1` (desde «Para abrir as reservas») abre o asistente sen deixar o parámetro', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/?nova=1');
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(FacilityListPage);
    await fixture.whenStable();
    (await expectRequest(listUrl())).flush(page(FACILITIES));
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['nova'], expect.objectContaining({ replaceUrl: true }));
  });
});
