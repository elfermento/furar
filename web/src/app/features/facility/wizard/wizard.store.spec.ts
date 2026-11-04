// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { NO_MAP } from '../../../../testing/deployment';
import {
  WIZARD_BASE,
  WIZARD_NOTES,
  drawing,
  openWizard,
  setupWizard,
  startWizard,
  wizardSeason,
  wizardWeek,
} from '../../../../testing/facility-wizard';
import { definition } from '../../../../testing/fixtures';
import { SUGGESTED, facility } from '../detail/facility.fixture';
import { STEP } from './wizard';
import type { WizardStore } from './wizard.store';

const F = `${WIZARD_BASE}/facilities`;
const PADEL = { code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } };

/** Borrador co nome, os deportes e o enderezo; sen usos nin horario. */
const draft = (overrides: Parameters<typeof facility>[0] = {}) =>
  facility({
    id: 'f2',
    name: 'Pista de pádel 3',
    published: false,
    missing: ['USES', 'SCHEDULE'],
    details: { onlineBooking: true, showFreeSlots: false, sports: [PADEL], customFields: {} },
    ...overrides,
  });

/** Un campo propio das instalacións, obrigatorio. */
const requiredField = () =>
  definition({ code: 'pitch', usages: [{ target: 'FACILITY', required: true, visibility: 'PUBLIC' }] });

describe('WizardStore: estado do asistente «Nova instalación»', () => {
  let store: WizardStore;

  afterEach(() => backend().verify());

  describe('abrir', () => {
    beforeEach(() => (store = setupWizard()));

    it('baleiro: co pago por instalación, o paso 7 pide o pago e un campo propio obrigatorio fai obrigatorio o paso 3', async () => {
      await startWizard(store, { definitions: [requiredField()], notes: { ...WIZARD_NOTES, scope: 'FACILITY' } });
      expect(store.scope()).toBe('FACILITY');
      expect(store.contactRequired()).toBe(true);
      expect(store.data.controls.customFields.controls['pitch']).toBeDefined();
      expect(store.ok().contact).toBe(false);
      expect(store.started()).toBe(false);

      store.payment.controls.payment.controls.gl.setValue('Só en efectivo');
      expect(store.ok().rules).toBe(false);
      expect(store.missing().map((item) => item.key)).toEqual([
        'name',
        'sports',
        'address',
        'contact',
        'uses',
        'rulesAndPayment',
      ]);
      expect(store.missing().find((item) => item.key === 'rulesAndPayment')?.step).toBe(STEP.rules);
    });

    it('baleiro: se a API non dá campos propios nin pago, segue co pago igual para todas', async () => {
      const started = store.start();
      flushProblem(await expectRequest(`${WIZARD_BASE}/custom-fields?target=FACILITY`), 500, 'common.unexpected');
      flushProblem(await expectRequest(`${WIZARD_BASE}/payment-notes`), 500, 'common.unexpected');
      await started;
      expect(store.definitions()).toEqual([]);
      expect(store.notes()).toBeNull();
      expect(store.scope()).toBe('ALL');
      expect(store.contactRequired()).toBe(false);
      // Co pago igual para todas, o pago mal escrito non conta.
      store.payment.controls.refund.controls.es.setValue('Solo en efectivo');
      expect(store.ok().rules).toBe(true);
    });

    it('un borrador: enche cada paso cos seus datos e segue no primeiro obrigatorio sen completar', async () => {
      const saturday = wizardWeek().map((row) =>
        row.day === 'SATURDAY' ? { ...row, intervals: [{ startMinute: 600, endMinute: 840 }] } : row,
      );
      const photo = { ...SUGGESTED, kind: 'PHOTO' as const, id: 'p1', alt: { gl: 'Pista', es: 'Pista' } };
      await openWizard(store, draft({ images: [photo] }), { seasons: [wizardSeason(saturday)] });

      expect(store.state()).toBe('ready');
      expect(store.step()).toBe(STEP.uses);
      expect(store.max()).toBe(STEP.uses);
      expect(store.name.value).toBe('Pista de pádel 3');
      expect(store.name.dirty).toBe(false);
      expect(store.data.controls.sports.value).toEqual([PADEL]);
      expect(store.location.controls.street.value).toBe('Rúa do Alto');
      expect(store.scheduleMode.value).toBe('DAYS');
      expect(store.images().map((image) => image.id)).toEqual(['p1']);
      expect(store.started()).toBe(true);
      expect(store.missing().map((item) => item.key)).toEqual(['uses']);
    });

    it('un borrador sen enderezo nin tempada parte do horario por defecto e segue no paso 2', async () => {
      await openWizard(store, draft({ address: undefined }));
      expect(store.step()).toBe(STEP.location);
      expect(store.location.controls.ineCode.value).toBe('15054');
      expect(store.scheduleMode.value).toBe('SAME');
      expect(store.ok().schedule).toBe(true);
    });

    it('un borrador que non se pode ler queda en erro', async () => {
      const opened = store.open('f404');
      flushProblem(await expectRequest(`${F}/f404`), 404, 'facility.not-found');
      await opened;
      expect(store.state()).toBe('error');
      expect(store.step()).toBe(0);
    });
  });

  describe('Seguinte e Gardar como borrador', () => {
    beforeEach(async () => {
      store = setupWizard();
      await startWizard(store);
    });

    function writeName(name: string) {
      store.name.setValue(name);
      store.name.markAsDirty();
    }

    /** Responde á creación do borrador (o primeiro gardado). */
    async function created(facility = draft({ address: undefined })) {
      (await expectRequest({ method: 'POST', url: F })).flush(facility);
      (await expectRequest(`${F}/${facility.id}/uses`)).flush([]);
      (await expectRequest(`${F}/${facility.id}/schedule`)).flush({ today: '2026-11-03', seasons: [] });
    }

    it('«Seguinte» cun paso mal queda nel, cos erros á vista e sen gardar', async () => {
      expect(await store.next()).toBe(false);
      expect(store.step()).toBe(STEP.data);
      expect(store.tried().has(STEP.data)).toBe(true);
      expect(store.name.touched).toBe(true);
      expect(store.savedAt()).toBeNull();
    });

    it('«Seguinte» no paso 1 crea a instalación, garda os deportes e a descrición e pasa ao 2', async () => {
      writeName('  Pista de pádel 3 ');
      store.data.controls.sports.setValue([PADEL]);
      store.data.markAsDirty();
      store.description.setValue({ gl: 'Pista cuberta', es: 'Pista cubierta' });
      store.description.markAsDirty();
      const step = store.next();
      const created = await expectRequest({ method: 'POST', url: F });
      expect(created.request.body).toEqual({ name: 'Pista de pádel 3' });
      created.flush(draft({ details: { onlineBooking: true, showFreeSlots: false, sports: [], customFields: {} } }));
      (await expectRequest(`${F}/f2/uses`)).flush([]);
      (await expectRequest(`${F}/f2/schedule`)).flush({ today: '2026-11-03', seasons: [] });
      const description = await expectRequest({ method: 'PUT', url: `${F}/f2/description` });
      expect(description.request.body).toEqual({ gl: 'Pista cuberta', es: 'Pista cubierta' });
      expect(description.request.headers.get('If-Match')).toBe('"3"');
      description.flush(draft({ version: 4 }));
      const details = await expectRequest({ method: 'PUT', url: `${F}/f2/details` });
      expect(details.request.body).toMatchObject({ sports: [{ code: 'padel' }], onlineBooking: true });
      expect(details.request.headers.get('If-Match')).toBe('"4"');
      details.flush(draft({ version: 5 }));

      expect(await step).toBe(true);
      expect(store.step()).toBe(STEP.location);
      expect(store.max()).toBe(STEP.location);
      expect(store.savedAt()).toMatch(/\d{2}:\d{2}/);
      expect(store.name.dirty).toBe(false);
      expect(store.data.dirty).toBe(false);
      expect(store.facility()?.version).toBe(5);
    });

    it('o nome repetido volve ao paso 1 co erro no nome', async () => {
      writeName('Pavillón');
      store.goTo(STEP.contact);
      const saved = store.saveDraft();
      flushProblem(await expectRequest({ method: 'POST', url: F }), 409, 'facility.name-taken', {
        params: { name: 'Pavillón' },
      });
      expect(await saved).toBe(false);
      expect(store.step()).toBe(STEP.data);
      expect(store.name.hasError('facility.name-taken')).toBe(true);
      expect(store.problem()).toBeNull();
      expect(store.busy()).toBe(false);
    });

    it('«Gardar como borrador» sen nome volve ao paso 1 e non garda', async () => {
      store.goTo(STEP.photos);
      expect(await store.saveDraft()).toBe(false);
      expect(store.step()).toBe(STEP.data);
      expect(store.tried().has(STEP.data)).toBe(true);
    });

    it('«Gardar como borrador» garda só os pasos visitados que están ben (os Datos sen deportes, non)', async () => {
      writeName('Ximnasio');
      store.location.controls.street.setValue('Rúa Nova');
      store.location.markAsDirty();
      store.goTo(STEP.location);
      // O enderezo está a medias: non se garda; o horario non se viu: tampouco.
      const saved = store.saveDraft();
      await created();
      expect(await saved).toBe(true);
      expect(store.facility()?.id).toBe('f2');
    });

    it('un erro de campo do enderezo vai ao seu campo e o aviso queda no paso', async () => {
      writeName('Ximnasio');
      store.data.controls.sports.setValue([PADEL]);
      store.data.markAsDirty();
      const first = store.next();
      await created();
      (await expectRequest({ method: 'PUT', url: `${F}/f2/details` })).flush(draft());
      expect(await first).toBe(true);
      store.location.setValue({
        street: 'Rúa Nova',
        streetNumber: '',
        postalCode: '15570',
        locality: 'Narón',
        ineCode: '15054',
        point: { latitude: 43.5, longitude: -8.2 },
      });
      store.location.markAsDirty();
      const step = store.next();
      const address = await expectRequest({ method: 'PUT', url: `${F}/f2/address` });
      expect(address.request.body).toMatchObject({ street: 'Rúa Nova', postalCode: '15570' });
      flushProblem(address, 400, 'validation.failed', {
        errors: [{ field: 'address.street', code: 'validation.size', params: { max: 200 } }],
      });
      expect(await step).toBe(false);
      expect(store.step()).toBe(STEP.location);
      expect(store.location.controls.street.hasError('validation.size')).toBe(true);
      expect(store.problem()?.code).toBe('validation.failed');
    });

    it('un erro de campo dos Datos vai ao seu campo', async () => {
      writeName('Ximnasio');
      store.data.controls.sports.setValue([PADEL]);
      store.data.markAsDirty();
      const step = store.next();
      await created();
      flushProblem(await expectRequest({ method: 'PUT', url: `${F}/f2/details` }), 400, 'validation.failed', {
        errors: [{ field: 'details.phone', code: 'validation.pattern', params: {} }],
      });
      expect(await step).toBe(false);
      expect(store.data.controls.phone.hasError('validation.pattern')).toBe(true);
      expect(store.problem()?.fieldErrors).toHaveLength(1);
    });
  });

  it('descartar antes de gardar nada non pide nada', async () => {
    store = setupWizard();
    await startWizard(store);
    expect(await store.discard()).toBe(true);
  });

  it('sen mapa no despregue, o enderezo sen chincheta vale para publicar', async () => {
    store = setupWizard(NO_MAP);
    await openWizard(store, draft({ address: { ...draft().address!, latitude: undefined, longitude: undefined } }));
    expect(store.ok().location).toBe(true);
    expect(store.step()).toBe(STEP.uses);
  });

  it('os debuxos pídense unha vez; se fallan, vólvense pedir ao velos outra vez', async () => {
    store = setupWizard();
    await startWizard(store);
    store.loadDrawings();
    flushProblem(await expectRequest(`${WIZARD_BASE}/drawings`), 500, 'common.unexpected');
    await vi.waitFor(() => {
      store.loadDrawings();
      backend()
        .expectOne(`${WIZARD_BASE}/drawings`)
        .flush([drawing('pool', 'Piscina')]);
    });
    await vi.waitFor(() => expect(store.drawings().map((item) => item.code)).toEqual(['pool']));
    store.loadDrawings();
    backend().expectNone(`${WIZARD_BASE}/drawings`);
  });
});
