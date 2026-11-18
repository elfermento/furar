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

describe('FacilityWizardPage: asistente «Nova instalación»', () => {
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
    expect(el.querySelector('[data-wizard-back]')?.getAttribute('aria-label')).toBe('Volver a Instalacións');
    expect(el.querySelector('furar-stepper')?.getAttribute('data-layout')).toBe('vertical');
    expect(el.querySelectorAll('furar-stepper nav li')).toHaveLength(8);
    expect(el.querySelector('furar-stepper [aria-current="step"]')?.textContent).toContain('Nome e deportes');
    expect(el.querySelector('[data-previous]')).toBeNull();
    expect(el.querySelector('[data-skip]')).toBeNull();
    const name = el.querySelector<HTMLInputElement>('#wizard-name')!;
    // Exemplo e explicación, todo no campo: nin liña de axuda nin «?».
    expect(name.placeholder).toBe('Pista de pádel 1, como a coñece a veciñanza');
    expect(el.querySelector('furar-field:has(#wizard-name) .f-help')).toBeNull();
    expect(el.querySelector('furar-field:has(#wizard-name) furar-help-tip')).toBeNull();
    // O tipo e a cuberta (sen dato, si ou non), despregables.
    expect(el.querySelector('furar-field #wizard-type')).not.toBeNull();
    expect(el.querySelector('furar-field furar-select #wizard-covered, furar-field #wizard-covered')).not.toBeNull();
    expect(el.querySelector('furar-switch #wizard-covered')).toBeNull();
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
        entryControl: false,
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
        entryControl: false,
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
    vi.waitFor(() => expect(leaveDialog()?.querySelector('[data-leave]')?.textContent?.trim()).toBe('Saír sen gardar'));
  /** A confirmación de descartar o borrador, se está aberta. */
  const confirmDialog = () => document.querySelector<HTMLElement>('furar-confirm');

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

  it('«Publicar» con algo que falta segue activo, non garda: di cantas cousas faltan e leva o foco á lista', async () => {
    const { el, click, store, stable } = await openComplete();
    store().name.setValue('');
    store().data.controls.sports.setValue([]);
    store().forms.touch();
    await stable();
    const publish = el.querySelector<HTMLButtonElement>('[data-publish]')!;
    expect(publish.getAttribute('aria-disabled')).toBeNull();
    expect(publish.disabled).toBe(false);
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
    (await expectRequest(`${BASE}/facilities?page=0&size=100`)).flush({ items: [], totalItems: 0 });
    expect(replace).toHaveBeenCalledWith('/nova/f9');
    expect(view.el.querySelector('furar-page-header')?.textContent).toMatch(/Borrador gardado ás \d{2}:\d{2}/);
  });

  describe('saír', () => {
    it('sen nada escrito sae sen preguntar e, sen borrador, non hai papeleira', async () => {
      const { el, page } = await startEmpty();
      expect(el.querySelector('[data-discard-draft]')).toBeNull();
      expect(await page().canLeave()).toBe(true);
      expect(leaveDialog()).toBeNull();
    });

    it('co nome escrito e sen gardar pregunta: pechar queda; «Saír sen gardar» sae sen pedir nada', async () => {
      const { el, page, toast, stable } = await startEmpty();
      const name = el.querySelector<HTMLInputElement>('#wizard-name')!;
      name.value = 'Ximnasio';
      name.dispatchEvent(new Event('input'));
      await stable();

      let leaving = page().canLeave();
      await shownLeave();
      expect(leaveDialog()!.querySelector('h2')?.textContent?.trim()).toBe('Gardar os cambios?');
      expect(leaveDialog()!.textContent).toContain('Aínda non se gardou: se saes sen gardar, pérdese.');
      expect(leaveDialog()!.querySelector('[data-save-draft]')?.textContent?.trim()).toBe('Gardar e saír');
      expect(leaveDialog()!.querySelector('[data-discard]')).toBeNull();
      leaveDialog()!.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
      expect(await leaving).toBe(false);

      leaving = page().canLeave();
      await shownLeave();
      leaveDialog()!.querySelector<HTMLButtonElement>('[data-leave]')!.click();
      expect(await leaving).toBe(true);
      expect(toast).not.toHaveBeenCalled();
      // Xa decidiu: non volve preguntar.
      expect(await page().canLeave()).toBe(true);
    });

    it('«Gardar e saír» garda e sae; se non se pode gardar, queda', async () => {
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

    it('un borrador sen cambios sae sen preguntar; con cambios, o borrador queda como estaba', async () => {
      const { page, store } = await openComplete();
      expect(await page().canLeave()).toBe(true);
      expect(leaveDialog()).toBeNull();

      store().houseRules.markAsDirty();
      let leaving = page().canLeave();
      await shownLeave();
      expect(leaveDialog()!.textContent).toContain('Se saes sen gardar, o borrador queda como estaba.');
      leaveDialog()!.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
      expect(await leaving).toBe(false);

      // Sen nome non se ofrece gardar, só saír.
      store().name.setValue('');
      leaving = page().canLeave();
      await shownLeave();
      expect(leaveDialog()!.querySelector('[data-save-draft]')).toBeNull();
      expect(leaveDialog()!.textContent).toContain('Sen nome non se pode gardar como borrador.');
      leaveDialog()!.querySelector<HTMLButtonElement>('[data-leave]')!.click();
      expect(await leaving).toBe(true);
    });

    it('a papeleira da cabeceira pregunta e descarta o borrador; se falla, queda', async () => {
      const { el, click, navigate, toast, stable } = await openComplete();
      const trash = el.querySelector<HTMLButtonElement>('[data-discard-draft] button')!;
      expect(trash.getAttribute('aria-label')).toBe('Descartar borrador');
      expect(trash.getAttribute('title')).toBe('Descartar borrador');

      await click(trash);
      await vi.waitFor(() => expect(confirmDialog()).not.toBeNull());
      expect(confirmDialog()!.textContent).toContain('Descartar o borrador?');
      expect(confirmDialog()!.textContent).toContain(
        'Bórrase «Pista de pádel 3» con todo o que ten. Non se pode desfacer.',
      );
      confirmDialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
      (await expectRequest({ method: 'DELETE', url: `${BASE}/facilities/f2` })).flush('', {
        status: 500,
        statusText: 'x',
      });
      await vi.waitFor(() => expect(el.querySelector('furar-problem-alert [role="alert"]')).not.toBeNull());
      expect(navigate).not.toHaveBeenCalled();
      await vi.waitFor(async () => {
        await stable();
        expect(trash.disabled).toBe(false);
      });

      await click(trash);
      await vi.waitFor(() => expect(confirmDialog()).not.toBeNull());
      confirmDialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
      (await expectRequest({ method: 'DELETE', url: `${BASE}/facilities/f2` })).flush('');
      await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(['../..'], expect.anything()));
      expect(toast).toHaveBeenCalledWith('facility.wizard.discarded');
    });
  });
});
