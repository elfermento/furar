// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { facility } from '../detail/facility.fixture';
import { use } from '../uses/uses.fixture';
import { FacilityWizardPage } from './facility-wizard.page';
import { STEP } from './wizard';
import { WizardStore } from './wizard.store';

const BASE = '/api/v1/municipalities/naron';
const NOTES = {
  scope: 'ALL',
  payment: { gl: 'No mostrador', es: 'En el mostrador' },
  refund: { gl: 'Polo concello', es: 'Por el ayuntamiento' },
  furarPayment: { gl: 'Furar paga', es: 'Furar paga' },
  furarRefund: { gl: 'Furar devolve', es: 'Furar devuelve' },
  paymentDefault: false,
  refundDefault: false,
};
const WEEK = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map((day) => ({
  day,
  intervals: [{ startMinute: 480, endMinute: 1380 }],
}));
const SCHEDULE = {
  today: '2026-11-03',
  seasons: [{ id: 's1', name: 'Todo o ano', startDate: '2026-01-01', endDate: '2026-12-31', version: 1, week: WEEK }],
  holidays: [],
  exceptions: [],
};

describe('FacilityWizardPage: asistente «Nova instalación» (maqueta 53)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(id: string | null = null) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(),
        provideRouter([
          { path: 'nova', component: FacilityWizardPage },
          { path: 'nova/:id', component: FacilityWizardPage },
        ]),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create();
    const toast = vi.spyOn(TestBed.inject(ToastService), 'show');
    await harness.navigateByUrl(id ? `/nova/${id}` : '/nova', FacilityWizardPage);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const el = harness.routeNativeElement as HTMLElement;
    const stable = async () => {
      await new Promise((resolve) => setTimeout(resolve));
      await harness.fixture.whenStable();
    };
    const page = () => harness.routeDebugElement!.componentInstance as FacilityWizardPage;
    const store = () => harness.routeDebugElement!.injector.get(WizardStore);
    const click = async (target: Element | null) => {
      (target as HTMLElement).click();
      await stable();
    };
    /** O paso que se ve (`data-step-key`). */
    const shown = () => el.querySelector('[data-wizard-step]')?.getAttribute('data-step-key');
    return { fixture: harness.fixture, el, stable, navigate, toast, page, store, click, shown };
  }

  async function startEmpty() {
    const view = await render();
    await view.fixture.whenStable();
    (await expectRequest(`${BASE}/custom-fields?target=FACILITY`)).flush([]);
    (await expectRequest(`${BASE}/payment-notes`)).flush(NOTES);
    await view.stable();
    return view;
  }

  it('paso 1: indicador de oito pasos, nome obrigatorio e «Seguinte» sen deportes queda co erro', async () => {
    const { el, stable } = await startEmpty();
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Nova instalación');
    expect(el.querySelectorAll('furar-stepper nav li')).toHaveLength(8);
    expect(el.querySelector('furar-stepper [aria-current="step"]')?.textContent).toContain('Nome e deportes');
    expect(el.querySelector('[data-previous]')).toBeNull();
    expect(el.querySelector('[data-skip]')).toBeNull();
    const name = el.querySelector<HTMLInputElement>('#wizard-name')!;
    name.value = 'Pavillón de Piñeiros';
    name.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('[data-next]')!.click();
    await vi.waitFor(async () => {
      await stable();
      expect(el.textContent).toContain('Escolle polo menos un deporte.');
    });
    expect(el.querySelector('furar-stepper [aria-current="step"]')?.textContent).toContain('Nome e deportes');
    await expectNoAxeViolations(el);
  });

  it('«Gardar como borrador» co nome: crea a instalación sen publicar e volve ao listado', async () => {
    const { el, stable, navigate, toast } = await startEmpty();
    const name = el.querySelector<HTMLInputElement>('#wizard-name')!;
    name.value = 'Ximnasio novo';
    name.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('[data-draft]')!.click();
    const created = await expectRequest({ method: 'POST', url: `${BASE}/facilities` });
    expect(created.request.body).toEqual({ name: 'Ximnasio novo' });
    created.flush(facility({ id: 'f9', name: 'Ximnasio novo', published: false, address: undefined }));
    (await expectRequest(`${BASE}/facilities/f9/uses`)).flush([]);
    (await expectRequest(`${BASE}/facilities/f9/schedule`)).flush({ ...SCHEDULE, seasons: [] });
    await vi.waitFor(async () => {
      await stable();
      expect(navigate).toHaveBeenCalledWith(['..'], expect.anything());
    });
    expect(toast).toHaveBeenCalledWith('facility.wizard.draftSaved');
  });

  it('un borrador completo abre en «Revisar» e «Publicar» publícao e leva á ficha', async () => {
    const draft = facility({
      id: 'f2',
      name: 'Pista de pádel 3',
      published: false,
      missing: [],
      details: {
        onlineBooking: true,
        showFreeSlots: false,
        sports: [{ code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } }],
        customFields: {},
      },
    });
    const { el, stable, navigate, toast } = await render('f2');
    (await expectRequest(`${BASE}/facilities/f2`)).flush(draft);
    (await expectRequest(`${BASE}/facilities/f2/uses`)).flush([use({ facilityId: 'f2' })]);
    (await expectRequest(`${BASE}/facilities/f2/schedule`)).flush(SCHEDULE);
    (await expectRequest(`${BASE}/custom-fields?target=FACILITY`)).flush([]);
    (await expectRequest(`${BASE}/payment-notes`)).flush(NOTES);
    (await expectRequest(`${BASE}/drawings`)).flush([]);
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('[data-publish]')).not.toBeNull();
    });
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Pista de pádel 3');
    expect(el.querySelector('#wizard-missing')).toBeNull();
    expect(el.querySelector('[data-publish]')?.getAttribute('aria-disabled')).toBeNull();
    // «Normas»: co pago igual para todas as instalacións, o paso 7 non leva «e pago».
    expect(el.querySelector('furar-stepper nav li:nth-child(7)')?.textContent).toContain('Normas');
    expect(el.querySelector('furar-stepper nav li:nth-child(7)')?.textContent).not.toContain('pago');
    await expectNoAxeViolations(el);

    el.querySelector<HTMLButtonElement>('[data-publish]')!.click();
    const publish = await expectRequest({ method: 'PUT', url: `${BASE}/facilities/f2/publication` });
    expect(publish.request.headers.get('If-Match')).toBe(`"${draft.version}"`);
    publish.flush({ ...draft, published: true, version: draft.version + 1 });
    await vi.waitFor(async () => {
      await stable();
      expect(navigate).toHaveBeenCalledWith(['../..', 'f2'], expect.anything());
    });
    expect(toast).toHaveBeenCalledWith('facility.wizard.published');
  });

  /** Borrador completo: abre en «Revisar». */
  async function openComplete() {
    const draft = facility({
      id: 'f2',
      name: 'Pista de pádel 3',
      published: false,
      missing: [],
      details: {
        onlineBooking: true,
        showFreeSlots: false,
        sports: [{ code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } }],
        customFields: {},
      },
    });
    const view = await render('f2');
    (await expectRequest(`${BASE}/facilities/f2`)).flush(draft);
    (await expectRequest(`${BASE}/facilities/f2/uses`)).flush([use({ facilityId: 'f2' })]);
    (await expectRequest(`${BASE}/facilities/f2/schedule`)).flush(SCHEDULE);
    (await expectRequest(`${BASE}/custom-fields?target=FACILITY`)).flush([]);
    (await expectRequest(`${BASE}/payment-notes`)).flush(NOTES);
    (await expectRequest(`${BASE}/drawings`)).flush([]);
    await vi.waitFor(async () => {
      await view.stable();
      expect(view.el.querySelector('[data-publish]')).not.toBeNull();
    });
    return view;
  }

  /** O diálogo de saír, se está aberto. */
  const leaveDialog = () => document.querySelector<HTMLElement>('furar-wizard-leave-dialog');
  /** Agarda a que o diálogo de saír estea pintado. */
  const shownLeave = () =>
    vi.waitFor(() => expect(leaveDialog()?.querySelector('[data-stay]')?.textContent?.trim()).toBe('Seguir'));

  it('desde «Revisar»: «Editar» leva ao paso; «Saltar», «Anterior» e «Volver a revisar» moven sen gardar', async () => {
    const { el, click, shown } = await openComplete();
    await click(el.querySelector(`[data-edit-step="${STEP.contact}"]`));
    expect(shown()).toBe('contact');
    // Fóra do paso 1, o título é o nome.
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Pista de pádel 3');
    expect(el.querySelector('#wizard-step-title')?.textContent?.trim()).toBe('Contacto e servizos');
    await click(el.querySelector('[data-skip]'));
    expect(shown()).toBe('photos');
    await click(el.querySelector('[data-previous]'));
    expect(shown()).toBe('contact');
    await click(el.querySelector(`furar-stepper [data-step="${STEP.uses}"]`));
    expect(shown()).toBe('uses');
    // «Usos» non se pode saltar.
    expect(el.querySelector('[data-skip]')).toBeNull();
    await click(el.querySelector('[data-back-to-review]'));
    expect(shown()).toBe('review');
  });

  it('«Publicar» con algo que falta non garda: di cantas cousas faltan e leva o foco á lista', async () => {
    const { el, click, store, stable } = await openComplete();
    store().name.setValue('');
    store().data.controls.sports.setValue([]);
    store().forms.touch();
    await stable();
    const publish = el.querySelector<HTMLButtonElement>('[data-publish]')!;
    expect(publish.getAttribute('aria-disabled')).toBe('true');
    expect(el.querySelector('#wizard-publish-why')?.textContent?.trim()).toBe('Faltan 2 cousas');
    await click(publish);
    await vi.waitFor(() => expect(document.activeElement?.id).toBe('wizard-missing'));

    store().data.controls.sports.setValue([{ code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } }]);
    store().forms.touch();
    await stable();
    expect(el.querySelector('#wizard-publish-why')?.textContent?.trim()).toBe('Falta 1 cousa');
  });

  it('desde o indicador, un uso aberto e mal escrito non deixa cambiar de paso', async () => {
    const { el, click, store, shown } = await openComplete();
    await click(el.querySelector(`furar-stepper [data-step="${STEP.uses}"]`));
    await click(el.querySelector('[data-use-other]'));
    await click(el.querySelector(`furar-stepper [data-step="${STEP.data}"]`));
    expect(shown()).toBe('uses');
    expect(store().useEditor.editor()).not.toBeNull();
    await vi.waitFor(() => expect(document.activeElement?.id).toBe('wizard-use-name'));
  });

  it('un borrador que non carga: aviso de erro e «Tentar de novo» volve pedilo', async () => {
    const { el, click } = await render('f404');
    flushProblem(await expectRequest(`${BASE}/facilities/f404`), 404, 'facility.not-found');
    await vi.waitFor(() =>
      expect(el.querySelector('furar-status-state h1')?.textContent).toContain('Non se puido cargar'),
    );
    const retry = [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Tentar de novo');
    await click(retry!);
    flushProblem(await expectRequest(`${BASE}/facilities/f404`), 404, 'facility.not-found');
    await vi.waitFor(() => expect(el.querySelector('furar-status-state')).not.toBeNull());
  });

  it('ao crear a instalación co «Seguinte» do paso 1, o URL pasa a nova/:id sen navegar', async () => {
    const view = await startEmpty();
    const replace = vi.spyOn(TestBed.inject(Location), 'replaceState');
    const name = view.el.querySelector<HTMLInputElement>('#wizard-name')!;
    name.value = 'Pavillón';
    name.dispatchEvent(new Event('input'));
    view.store().data.controls.sports.setValue([{ code: 'futsal', label: { gl: 'Fútbol sala', es: 'Fútbol sala' } }]);
    view.store().data.markAsDirty();
    await view.click(view.el.querySelector('[data-next]'));
    (await expectRequest({ method: 'POST', url: `${BASE}/facilities` })).flush(
      facility({ id: 'f9', name: 'Pavillón', published: false, address: undefined }),
    );
    (await expectRequest(`${BASE}/facilities/f9/uses`)).flush([]);
    (await expectRequest(`${BASE}/facilities/f9/schedule`)).flush({ ...SCHEDULE, seasons: [] });
    (await expectRequest({ method: 'PUT', url: `${BASE}/facilities/f9/details` })).flush(
      facility({ id: 'f9', name: 'Pavillón', published: false, address: undefined, version: 4 }),
    );
    await vi.waitFor(async () => {
      await view.stable();
      expect(view.shown()).toBe('location');
    });
    expect(replace).toHaveBeenCalledWith('/nova/f9');
    expect(view.el.querySelector('furar-page-header')?.textContent).toMatch(/Borrador gardado ás \d{2}:\d{2}/);
  });

  describe('saír a medias', () => {
    it('sen nada escrito sae sen preguntar', async () => {
      const { page } = await startEmpty();
      expect(await page().canLeave()).toBe(true);
      expect(leaveDialog()).toBeNull();
    });

    it('co nome escrito pregunta: «Seguir» queda, «Descartar» sae (sen nada gardado, sen pedir nada)', async () => {
      const { el, page, toast, stable } = await startEmpty();
      const name = el.querySelector<HTMLInputElement>('#wizard-name')!;
      name.value = 'Ximnasio';
      name.dispatchEvent(new Event('input'));
      await stable();

      let leaving = page().canLeave();
      await shownLeave();
      expect(leaveDialog()!.querySelector('[data-save-draft]')).not.toBeNull();
      leaveDialog()!.querySelector<HTMLButtonElement>('[data-stay]')!.click();
      expect(await leaving).toBe(false);

      leaving = page().canLeave();
      await shownLeave();
      leaveDialog()!.querySelector<HTMLButtonElement>('[data-discard]')!.click();
      expect(await leaving).toBe(true);
      expect(toast).toHaveBeenCalledWith('facility.wizard.discarded');
      // Xa decidiu: non volve preguntar.
      expect(await page().canLeave()).toBe(true);
    });

    it('«Gardar borrador» garda e sae; se non se pode gardar, queda', async () => {
      const { el, page, toast, stable } = await startEmpty();
      const name = el.querySelector<HTMLInputElement>('#wizard-name')!;
      name.value = 'Ximnasio';
      name.dispatchEvent(new Event('input'));
      await stable();

      let leaving = page().canLeave();
      await shownLeave();
      leaveDialog()!.querySelector<HTMLButtonElement>('[data-save-draft]')!.click();
      flushProblem(await expectRequest({ method: 'POST', url: `${BASE}/facilities` }), 409, 'facility.name-taken');
      expect(await leaving).toBe(false);
      // Co nome repetido xa non se ofrece gardar: cámbiase o nome.
      await vi.waitFor(async () => {
        await stable();
        expect(el.querySelector('#wizard-name')?.getAttribute('aria-invalid')).toBe('true');
      });
      name.value = 'Ximnasio 2';
      name.dispatchEvent(new Event('input'));
      await stable();

      leaving = page().canLeave();
      await shownLeave();
      leaveDialog()!.querySelector<HTMLButtonElement>('[data-save-draft]')!.click();
      (await expectRequest({ method: 'POST', url: `${BASE}/facilities` })).flush(
        facility({ id: 'f9', name: 'Ximnasio', published: false, address: undefined }),
      );
      (await expectRequest(`${BASE}/facilities/f9/uses`)).flush([]);
      (await expectRequest(`${BASE}/facilities/f9/schedule`)).flush({ ...SCHEDULE, seasons: [] });
      expect(await leaving).toBe(true);
      expect(toast).toHaveBeenCalledWith('facility.wizard.draftSaved');
    });

    it('un borrador gardado sen nome non se pode gardar: «Descartar» elimínao', async () => {
      const { page, store, toast } = await openComplete();
      store().name.setValue('');
      let leaving = page().canLeave();
      await shownLeave();
      expect(leaveDialog()!.querySelector('[data-save-draft]')).toBeNull();
      expect(leaveDialog()!.textContent).toContain('Sen nome non se pode gardar como borrador.');
      leaveDialog()!.querySelector<HTMLButtonElement>('[data-discard]')!.click();
      (await expectRequest({ method: 'DELETE', url: `${BASE}/facilities/f2` })).flush('', {
        status: 500,
        statusText: 'x',
      });
      expect(await leaving).toBe(false);

      leaving = page().canLeave();
      await shownLeave();
      leaveDialog()!.querySelector<HTMLButtonElement>('[data-discard]')!.click();
      (await expectRequest({ method: 'DELETE', url: `${BASE}/facilities/f2` })).flush('');
      expect(await leaving).toBe(true);
      expect(toast).toHaveBeenCalledWith('facility.wizard.discarded');
    });
  });
});
