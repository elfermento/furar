// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { PAYMENT_NOTES_URL, paymentNotes, renderDetail } from '../../../../testing/facility-detail';
import { By } from '@angular/platform-browser';
import { TranslocoService } from '@jsverse/transloco';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ChipMenuComponent } from '../../../shared/ui/menu/chip-menu.component';
import { FacilityDetailPage } from './facility-detail.page';
import { facility } from './facility.fixture';
import { answerCustomFields } from '../data-card/data-card.fixture';
import { answerBlocks } from '../blocks/blocks.fixture';

const URL = '/api/v1/municipalities/naron/facilities/f1';

describe('FacilityDetailPage: esqueleto da ficha (D1-21a)', () => {
  afterEach(() => {
    // As seccións Usos (D1-22) e Horario (D1-23a) piden o seu ao cargar a ficha: aquí non se proban.
    backend()
      .match({ method: 'GET', url: `${URL}/uses` })
      .forEach((request) => request.flush([]));
    backend()
      .match({ method: 'GET', url: `${URL}/schedule` })
      .forEach((request) => request.flush({ seasons: [], holidays: [] }));
    answerCustomFields(backend());
    answerBlocks(backend());
    backend()
      .match(PAYMENT_NOTES_URL)
      .forEach((request) => request.flush(paymentNotes('ALL')));
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(loaded: Parameters<typeof facility>[0] | null = {}) {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(FacilityDetailPage);
    fixture.componentRef.setInput('id', 'f1');
    const el = fixture.nativeElement as HTMLElement;
    const stable = async () => {
      await new Promise((resolve) => setTimeout(resolve));
      await fixture.whenStable();
    };
    await fixture.whenStable();
    if (loaded) {
      (await expectRequest(URL)).flush(facility(loaded));
      await vi.waitFor(async () => {
        await fixture.whenStable();
        expect(el.querySelector('h1')).not.toBeNull();
      });
    }
    const button = (text: string) =>
      [...el.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes(text))!;
    return { fixture, el, stable, button };
  }

  it('cabeceira, un h1, seccións con h2 e o foco no título ao entrar', async () => {
    const { el } = await render();
    expect(el.querySelectorAll('h1')).toHaveLength(1);
    expect(el.querySelector('h1')?.textContent).toContain('Campo de fútbol 1 de O Alto');
    expect(el.querySelector('.f-page-header__intro')?.textContent?.trim()).toBe('Rúa do Alto, 12 · 15570 Narón');
    // O chip de estado vai na liña do título; ao lado, «Editar» e «Eliminar».
    expect(el.querySelector('.f-page-header__row furar-chip-menu')).not.toBeNull();
    expect(el.querySelector('[data-edit]')?.textContent).toContain('Editar');
    expect(el.querySelector('[data-delete] button')?.textContent).toContain('Eliminar');
    expect(document.activeElement).toBe(el.querySelector('h1'));
    const headings = [...el.querySelectorAll('h2')].map((h) => h.textContent?.trim());
    expect(headings).toEqual([
      'Datos',
      'Usos',
      'Descrición',
      'Normas da instalación',
      'Horario',
      'Bloqueos',
      'Servizos e accesibilidade',
      'Localización',
    ]);
    expect(el.querySelector('a[href$=".."], a[href]')?.textContent).toContain('Volver');
    expect(el.querySelector('furar-chip-menu button')?.getAttribute('aria-label')).toContain('Activa');
    await expectNoAxeViolations(el);
  });

  it('Datos vai primeiro no DOM e a grella non reordena (orde de foco = orde visual)', async () => {
    const { el } = await render();
    const data = el.querySelector('#facility-data')!;
    const uses = el.querySelector('#facility-uses')!;
    expect(data.compareDocumentPosition(uses) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(el.innerHTML).not.toMatch(/\border-/);
    for (const id of ['photos', 'data', 'uses', 'description', 'schedule', 'blocks', 'amenities', 'location']) {
      if (id !== 'photos') expect(el.querySelector(`#facility-${id}`)).not.toBeNull();
    }
  });

  it('aviso de borrador sen role=alert e ligazóns que levan o foco ao h2 da sección', async () => {
    const { el } = await render({ missing: ['SPORTS', 'USES', 'SCHEDULE'] });
    const callout = el.querySelector('furar-facility-draft-notice furar-callout')!;
    expect(callout.getAttribute('role')).toBeNull();
    expect(callout.textContent).toContain('Borrador');
    const links = [...callout.querySelectorAll('a')];
    expect(links.map((a) => a.textContent?.trim())).toEqual(['Os deportes', 'Polo menos un uso', 'O horario']);
    expect(links[2].getAttribute('href')).toMatch(/#facility-schedule$/);
    links[2].click();
    expect(document.activeElement).toBe(el.querySelector('#facility-schedule h2'));
    links[0].click();
    expect(document.activeElement).toBe(el.querySelector('#facility-data h2'));
    await expectNoAxeViolations(el);
  });

  it('sen nada que falte, sen aviso de borrador', async () => {
    const { el } = await render({ missing: [] });
    expect(el.querySelector('furar-facility-draft-notice')).toBeNull();
  });

  it('cargando con esqueleto; 404 con h1 e sen reintentar', async () => {
    const { fixture, el, stable } = await render(null);
    expect(el.querySelector('furar-status-state[data-mode="skeleton"]')?.getAttribute('role')).toBe('status');
    flushProblem(await expectRequest(URL), 404, 'facility.not-found');
    await stable();
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toContain('Non atopamos esta instalación');
    expect(el.querySelector('furar-status-state button')).toBeNull();
    await expectNoAxeViolations(el);
  });

  it('erro de carga con «Tentar de novo»', async () => {
    const { el, stable, button } = await render(null);
    flushProblem(await expectRequest(URL), 500, 'common.internal');
    await stable();
    button('Tentar de novo').click();
    (await expectRequest(URL)).flush(facility());
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('h1')?.textContent).toContain('Campo de fútbol');
    });
  });

  it('cambia o estado desde o chip; «De baixa» pide confirmación', async () => {
    const { fixture, el, stable } = await render();
    const chip = fixture.debugElement.query(By.directive(ChipMenuComponent)).componentInstance as ChipMenuComponent;
    chip.selected.emit('TEMPORARILY_CLOSED');
    const put = await expectRequest({ method: 'PUT', url: `${URL}/status` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    put.flush(facility({ version: 4, status: 'TEMPORARILY_CLOSED' }));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-chip-menu button')?.getAttribute('aria-label')).toContain('Pechada');
    });

    chip.selected.emit('WITHDRAWN');
    await stable();
    const cancel = document.querySelector<HTMLButtonElement>('[data-confirm-cancel]');
    expect(document.body.textContent).toContain('Dar de baixa?');
    cancel?.click();
    await stable();
    backend().expectNone({ method: 'PUT', url: `${URL}/status` });
  });

  it('en castelán', async () => {
    const { fixture, el, stable } = await render();
    TestBed.inject(TranslocoService).setActiveLang('es');
    await stable();
    await fixture.whenStable();
    expect(el.textContent).toContain('Servicios y accesibilidad');
    expect(el.querySelector('furar-chip-menu button')?.getAttribute('aria-label')).toContain('Activa');
    expect(el.textContent).toContain('Borrador: los vecinos aún no la ven');
  });

  it('«Editar» pon a ficha en edición coa barra fixa; «Cancelar» sae sen peticións e devolve o foco', async () => {
    const { el, stable, button } = await render();
    button('Editar').click();
    await stable();
    const bar = el.querySelector('furar-action-bar')!;
    expect(bar.querySelector('[role="region"]')?.getAttribute('aria-label')).toBe(
      'Edición de Campo de fútbol 1 de O Alto',
    );
    expect(el.querySelector('[data-edit]')).toBeNull();
    await expectNoAxeViolations(el);
    (bar.querySelector('[data-edit-cancel]') as HTMLButtonElement).click();
    await stable();
    expect(el.querySelector('furar-action-bar')).toBeNull();
    expect(document.activeElement).toBe(el.querySelector('[data-edit]'));
  });

  it('eliminar: hai que escribir a frase; DELETE co If-Match e volta ao listado', async () => {
    const { el, stable } = await render();
    (el.querySelector('[data-delete] button') as HTMLButtonElement).click();
    await stable();
    const confirm = () => document.querySelector<HTMLElement>('furar-dialog-container')!;
    expect(confirm().textContent).toContain('Eliminar a instalación?');
    expect(confirm().textContent).toContain('As fotos seguen na biblioteca');
    const accept = confirm().querySelector<HTMLButtonElement>('[data-confirm-accept]')!;
    expect(accept.getAttribute('aria-disabled')).toBe('true');
    const phrase = confirm().querySelector<HTMLInputElement>('[data-confirm-phrase]')!;
    expect(confirm().querySelector(`label[for="${phrase.id}"]`)?.textContent).toContain('«eliminar instalación»');
    phrase.value = ' Eliminar instalación ';
    phrase.dispatchEvent(new Event('input'));
    await stable();
    expect(accept.getAttribute('aria-disabled')).toBeNull();
    await expectNoAxeViolations(confirm());
    accept.click();
    const remove = await expectRequest({ method: 'DELETE', url: URL });
    expect(remove.request.headers.get('If-Match')).toBe('"3"');
    remove.flush(null, { status: 204, statusText: 'No Content' });
    await stable();
  });

  it('con reservas non se elimina: explica que hai que dala de baixa e ofrécea', async () => {
    const { el, stable } = await render({ deletable: false });
    (el.querySelector('[data-delete] button') as HTMLButtonElement).click();
    await stable();
    const confirm = document.querySelector<HTMLElement>('furar-dialog-container')!;
    expect(confirm.textContent).toContain('Non se pode eliminar');
    expect(confirm.textContent).toContain('dáa de baixa');
    expect(confirm.querySelector('[data-confirm-phrase]')).toBeNull();
    confirm.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/status` });
    expect(put.request.body).toMatchObject({ status: 'WITHDRAWN' });
    put.flush(facility({ version: 4, status: 'WITHDRAWN', deletable: false }));
    await stable();
  });
});

describe('FacilityDetailPage: «Cancelar» con cambios sen gardar', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('pregunta: «Seguir editando» non toca nada; «Descartar os cambios» sae sen peticións', async () => {
    const { el, edit, type, click, stable } = await renderDetail();
    await edit();
    await type(el.querySelector<HTMLInputElement>('#facility-data-phone')!, '981 000 000');
    const confirm = () => document.querySelector<HTMLElement>('[role="alertdialog"]');
    await click(el.querySelector('[data-edit-cancel]')!);
    expect(confirm()?.textContent).toContain('Descartar os cambios?');
    expect(confirm()?.textContent).toContain('Hai cambios sen gardar. Se os descartas, pérdense.');
    await click(document.querySelector('[data-confirm-cancel]')!);
    expect(confirm()).toBeNull();
    expect(el.querySelector('furar-action-bar')).not.toBeNull();
    expect(el.querySelector<HTMLInputElement>('#facility-data-phone')?.value).toBe('981 000 000');
    await click(el.querySelector('[data-edit-cancel]')!);
    expect(document.querySelector('[data-confirm-accept]')?.textContent?.trim()).toBe('Descartar os cambios');
    await click(document.querySelector('[data-confirm-accept]')!);
    await stable();
    expect(el.querySelector('furar-action-bar')).toBeNull();
  });

  it('sen cambios, sae sen preguntar', async () => {
    const { el, edit, click } = await renderDetail();
    await edit();
    await click(el.querySelector('[data-edit-cancel]')!);
    expect(document.querySelector('[role="alertdialog"]')).toBeNull();
    expect(el.querySelector('furar-action-bar')).toBeNull();
  });
});
