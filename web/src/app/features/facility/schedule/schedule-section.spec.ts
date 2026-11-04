// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { renderSchedule } from '../../../../testing/facility-schedule';
import { pickOption, selectText } from '../../../../testing/select';
import { withPopoverApi } from '../../../../testing/popover';
import { facility } from '../detail/facility.fixture';
import { FACILITY_URL, SCHEDULE_URL, SEASONS_URL, SUMMER, schedule, season } from './schedule.fixture';

const text = (el: Element | null | undefined) => el?.textContent?.replace(/\s+/g, ' ').trim();

describe('Sección Horario: fóra da edición (D1-23a)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('selector coas tempadas só co nome (a vixente escollida e con «Agora»), datas coa icona e semana sen «Hoxe»', async () => {
    const { section, rows } = await renderSchedule();
    const options = [...section().querySelectorAll<HTMLButtonElement>('[data-seasons] button')];
    expect(options.map((o) => text(o))).toEqual(['Inverno Agora', 'Verán']);
    expect(section().querySelectorAll('[data-season-now]')).toHaveLength(1);
    expect(options[0].querySelector('[data-season-now]')).not.toBeNull();
    expect(options[0].getAttribute('aria-pressed')).toBe('true');
    expect(section().querySelector('[data-season-add]')).toBeNull();
    expect(text(section().querySelector('[data-season-range]'))).toBe('1 set. 2026 – 30 xuño 2027');
    expect(section().querySelector('[data-season-range] furar-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(rows().map((row) => text(row))).toEqual([
      'Luns 8:00 – 23:00',
      'Martes 8:00 – 23:00',
      'Mércores 8:00 – 23:00',
      'Xoves 8:00 – 23:00',
      'Venres 8:00 – 23:00',
      'Sábado 9:00 – 14:00 · 16:00 – 20:00',
      'Domingo 9:00 – 14:00',
      'Festivos Pechado',
    ]);
    expect(section().textContent).not.toContain('Hoxe');
    await expectNoAxeViolations(section());
  });

  it('tempada sen fin (maqueta 54): «Desde 1 set. 2026 · sen fin»', async () => {
    const { section } = await renderSchedule(schedule({ seasons: [season({ endDate: undefined })] }));
    expect(text(section().querySelector('[data-season-range]'))).toBe('Desde 1 set. 2026 · sen fin');
  });

  it('sen tempada vixente hoxe, ningunha leva «Agora»', async () => {
    const { section } = await renderSchedule(schedule({ currentSeasonId: undefined }));
    expect(section().querySelector('[data-season-now]')).toBeNull();
  });

  it('escoller outra tempada mostra as súas datas e a súa semana', async () => {
    const { section, click, rows } = await renderSchedule();
    await click(section().querySelectorAll('[data-seasons] button')[1]);
    expect(text(section().querySelector('[data-season-range]'))).toBe('1 xul. 2027 – 31 ago. 2027');
    expect(text(rows()[0])).toBe('Luns Pechado');
  });

  it('baleiro: «Aínda non hai horario»', async () => {
    const { section } = await renderSchedule(schedule({ seasons: [], currentSeasonId: undefined }));
    expect(section().textContent).toContain('Aínda non hai horario');
    expect(section().querySelector('[data-seasons]')).toBeNull();
  });

  it('erro ao cargar: aviso e «Tentar de novo» volve pedir', async () => {
    const { section, click, button, rows, stable } = await renderSchedule('error');
    expect(section().textContent).toContain('Non se puido cargar o horario');
    await click(button('Tentar de novo', section()));
    (await expectRequest({ method: 'GET', url: SCHEDULE_URL })).flush(schedule());
    await vi.waitFor(async () => {
      await stable();
      expect(rows()).toHaveLength(8);
    });
  });
});

describe('Sección Horario: modo edición (D1-23a)', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = () => undefined;
  });

  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('nome e datas como campos con «Eliminar» na mesma liña; «+» no selector', async () => {
    const { section, edit } = await renderSchedule();
    await edit();
    const fields = section().querySelector<HTMLElement>('[data-season-fields]')!;
    expect(fields.querySelector<HTMLInputElement>('input')?.value).toBe('Inverno');
    expect(fields.querySelectorAll('furar-date-field')).toHaveLength(2);
    expect(fields.querySelector('[data-season-delete]')?.textContent).toContain('Eliminar');
    expect(section().querySelector('[data-season-add]')?.getAttribute('aria-label')).toBe('Tempada');
    await expectNoAxeViolations(section());
  });

  it('picar nunha fila ábrea; «+ Tramo» engade e picar fóra volve a resolto co cambio; gardar fai o PUT', async () => {
    const { section, edit, editing, openRow, click, save, stable } = await renderSchedule();
    await edit();
    await openRow(0);
    expect(editing()).toHaveLength(1);
    const row = editing()[0] as HTMLElement;
    expect(document.activeElement).toBe(row.querySelector('[role="switch"]'));
    await click(row.querySelector<HTMLButtonElement>('[data-interval-add]')!);
    expect(row.querySelectorAll('[data-interval]')).toHaveLength(2);
    expect(row.querySelector('furar-delete-button button')?.getAttribute('aria-label')).toBe(
      'Eliminar o tramo 1 de Luns',
    );
    await expectNoAxeViolations(section());
    await click(document.body);
    expect(editing()).toHaveLength(0);
    expect(section().querySelector('[data-day-edit="0"]')?.textContent).toContain('8:00 – 23:00 · 23:00 – 24:00');
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${SEASONS_URL}/s1` });
    expect(put.request.headers.get('If-Match')).toBe('"4"');
    expect(put.request.body.week[0].intervals).toEqual([
      { startMinute: 480, endMinute: 1380 },
      { startMinute: 1380, endMinute: 1440 },
    ]);
    put.flush(season({ version: 5 }));
    (await expectRequest({ method: 'GET', url: SCHEDULE_URL })).flush(schedule());
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await stable();
      expect(section().querySelector('[data-season-fields]')).toBeNull();
    });
  });

  it('Esc na lista de horas dunha fila só pecha a lista: a fila segue en edición', async () => {
    const restore = withPopoverApi();
    try {
      const { edit, editing, openRow, click, stable } = await renderSchedule();
      await edit();
      await openRow(0);
      const row = editing()[0] as HTMLElement;
      const trigger = row.querySelector<HTMLButtonElement>('furar-time-picker button')!;
      await click(trigger);
      document.activeElement!.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }),
      );
      await stable();
      expect(editing()).toHaveLength(1);
      expect(document.activeElement).toBe(trigger);
      trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
      await stable();
      expect(editing()).toHaveLength(0);
    } finally {
      restore();
    }
  });

  it('fila con erros: queda aberta e «Gardar» non manda nada', async () => {
    const { edit, editing, openRow, click, pickTime, save, el } = await renderSchedule();
    await edit();
    await openRow(0);
    const row = editing()[0] as HTMLElement;
    await pickTime(row.querySelectorAll('furar-time-picker')[1], 420);
    await click(document.body);
    expect(editing()).toHaveLength(1);
    expect(row.querySelector('.f-field-error')?.textContent).toContain('remata');
    await save();
    expect(backend().match({ method: 'PUT', url: `${SEASONS_URL}/s1` })).toHaveLength(0);
    expect(el.querySelector('furar-action-bar')).not.toBeNull();
  });

  it('«+»: o solape cunha da lista avisa no modal; unha nova queda pendente e ao gardar créase coa súa semana', async () => {
    const { section, edit, click, button, dialog, type, pickDate } = await renderSchedule(
      schedule({ seasons: [season()] }),
    );
    await edit();
    await click(section().querySelector('[data-season-add]')!);
    expect(dialog()?.querySelector('h2')?.textContent).toContain('Nova tempada');
    expect(selectText(dialog()!.querySelector('furar-select')!)).toBe('Inverno');
    await expectNoAxeViolations(dialog()!);
    await type(dialog()!.querySelector<HTMLInputElement>('input')!, 'Semana Santa');
    await pickDate(dialog()!, 0, 0);
    await pickDate(dialog()!, 1, 3);
    await click(button('Aceptar', dialog()!));
    expect(dialog()?.querySelector('[role="alert"]')?.textContent).toContain('Estas datas pisan a tempada «Inverno»');
    await click(button('Cancelar', dialog()!));
    backend().expectNone({ method: 'POST', url: SEASONS_URL });
  });

  it('nova tempada sen solape: pendente ata gardar; entón POST e PUT coa semana copiada', async () => {
    const { section, edit, click, button, dialog, type, pickDate, save, stable } = await renderSchedule(
      schedule({ seasons: [SUMMER], currentSeasonId: undefined }),
    );
    await edit();
    await click(section().querySelector('[data-season-add]')!);
    await type(dialog()!.querySelector<HTMLInputElement>('input')!, 'Outono');
    const from = await pickDate(dialog()!, 0, 0);
    const to = await pickDate(dialog()!, 1, 3);
    await click(button('Aceptar', dialog()!));
    expect(dialog()).toBeNull();
    const chosen = section().querySelector('[data-seasons] button[aria-pressed="true"]');
    expect(text(chosen)).toBe('Outono');
    backend().expectNone({ method: 'POST', url: SEASONS_URL });
    await save();
    const post = await expectRequest({ method: 'POST', url: SEASONS_URL });
    expect(post.request.body).toEqual({ name: 'Outono', startDate: from, endDate: to });
    const created = season({ id: 's3', name: 'Outono', startDate: from, endDate: to, version: 0, week: [] });
    post.flush(created);
    const put = await expectRequest({ method: 'PUT', url: `${SEASONS_URL}/s3` });
    expect(put.request.headers.get('If-Match')).toBe('"0"');
    expect(put.request.body.week.every((d: { intervals: unknown[] }) => d.intervals.length === 0)).toBe(true);
    put.flush({ ...created, version: 1 });
    (await expectRequest({ method: 'GET', url: SCHEDULE_URL })).flush(schedule({ seasons: [SUMMER, created] }));
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await stable();
      expect(section().querySelector('[data-season-fields]')).toBeNull();
    });
  });

  it('«Sen data de fin» baleira e desactiva «Ata»; ao gardar, o PUT vai sen endDate', async () => {
    const { section, edit, click, save, stable } = await renderSchedule(schedule({ seasons: [season()] }));
    await edit();
    const fields = section().querySelector<HTMLElement>('[data-season-fields]')!;
    const noEnd = fields.querySelector<HTMLInputElement>('[data-season-no-end]')!;
    expect(noEnd.checked).toBe(false);
    await click(noEnd);
    expect(fields.querySelectorAll<HTMLButtonElement>('.f-date-field__button')[1].disabled).toBe(true);
    expect(text(section().querySelector('[data-seasons] button'))).toContain('Inverno');
    await expectNoAxeViolations(section());
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${SEASONS_URL}/s1` });
    expect(put.request.body.startDate).toBe('2026-09-01');
    expect(put.request.body.endDate).toBeUndefined();
    put.flush(season({ endDate: undefined, version: 5 }));
    (await expectRequest({ method: 'GET', url: SCHEDULE_URL })).flush(
      schedule({ seasons: [season({ endDate: undefined, version: 5 })] }),
    );
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await stable();
      expect(text(section().querySelector('[data-season-range]'))).toBe('Desde 1 set. 2026 · sen fin');
    });
  });

  it('nova tempada sen fin: choca coas posteriores e, se non, o POST vai sen endDate', async () => {
    const { section, edit, click, button, dialog, type, pickDate, save } = await renderSchedule(
      schedule({ seasons: [SUMMER], currentSeasonId: undefined }),
    );
    await edit();
    await click(section().querySelector('[data-season-add]')!);
    await type(dialog()!.querySelector<HTMLInputElement>('input')!, 'Todo o ano');
    const from = await pickDate(dialog()!, 0, 0);
    await click(dialog()!.querySelector('[data-season-no-end]')!);
    await click(button('Aceptar', dialog()!));
    expect(dialog()?.querySelector('[role="alert"]')?.textContent).toContain('Estas datas pisan a tempada «Verán»');
    await click(button('Cancelar', dialog()!));
    await click(section().querySelector('[data-season-delete] button')!);
    await click(section().querySelector('[data-season-add]')!);
    await type(dialog()!.querySelector<HTMLInputElement>('input')!, 'Todo o ano');
    await pickDate(dialog()!, 0, 0);
    await click(dialog()!.querySelector('[data-season-no-end]')!);
    await click(button('Aceptar', dialog()!));
    expect(dialog()).toBeNull();
    await save();
    const remove = await expectRequest({ method: 'DELETE', url: `${SEASONS_URL}/s2` });
    remove.flush(null, { status: 204, statusText: 'No Content' });
    const post = await expectRequest({ method: 'POST', url: SEASONS_URL });
    expect(post.request.body).toEqual({ name: 'Todo o ano', startDate: from });
    post.flush(season({ id: 's3', name: 'Todo o ano', startDate: from, endDate: undefined, version: 0, week: [] }));
    (await expectRequest({ method: 'PUT', url: `${SEASONS_URL}/s3` })).flush(
      season({ id: 's3', name: 'Todo o ano', startDate: from, endDate: undefined, version: 1 }),
    );
    (await expectRequest({ method: 'GET', url: SCHEDULE_URL })).flush(schedule({ seasons: [] }));
    (await expectRequest(FACILITY_URL)).flush(facility());
  });

  it('eliminar unha tempada: sae do selector e, ao gardar, DELETE co If-Match; o 409 queda no aviso', async () => {
    const { section, edit, click, save, el, stable } = await renderSchedule();
    await edit();
    await click(section().querySelectorAll('[data-seasons] button')[1]);
    await click(section().querySelector('[data-season-delete] button')!);
    expect(section().querySelectorAll('[data-seasons] button[aria-pressed]')).toHaveLength(1);
    await save();
    const remove = await expectRequest({ method: 'DELETE', url: `${SEASONS_URL}/s2` });
    expect(remove.request.headers.get('If-Match')).toBe('"1"');
    flushProblem(remove, 412, 'common.stale-version');
    (await expectRequest({ method: 'GET', url: SCHEDULE_URL })).flush(schedule());
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('[data-edit-status]')?.textContent).toContain('Non se gardou todo');
    });
    expect(section().querySelector('[role="alert"]')?.textContent).toContain('Verán');
  });

  it('Cancelar volve ás tempadas gardadas sen peticións', async () => {
    const { section, edit, cancel, type } = await renderSchedule(schedule({ seasons: [season(), SUMMER] }));
    await edit();
    await type(section().querySelector<HTMLInputElement>('[data-season-fields] input')!, 'Outro nome');
    await cancel();
    expect(text(section().querySelector('[data-seasons] button'))).toBe('Inverno Agora');
  });

  it('«empezar co horario de» ningunha: a nova empeza pechada; o nome baleiro non se pode gardar', async () => {
    const { section, edit, click, button, dialog, type, pickDate, save, stable } = await renderSchedule(
      schedule({ seasons: [SUMMER], currentSeasonId: undefined }),
    );
    await edit();
    await click(section().querySelector('[data-season-add]')!);
    await type(dialog()!.querySelector<HTMLInputElement>('input')!, 'Outono');
    await pickDate(dialog()!, 0, 0);
    await pickDate(dialog()!, 1, 3);
    await pickOption(dialog()!.querySelector('furar-select')!, 'Ningunha (todo pechado)', stable);
    await stable();
    await click(button('Aceptar', dialog()!));
    expect(text(section().querySelector('[data-seasons] button[aria-pressed="true"]'))).toBe('Outono');
    await type(section().querySelector<HTMLInputElement>('[data-season-fields] input')!, ' ');
    await save();
    backend().expectNone({ method: 'POST', url: SEASONS_URL });
    expect(section().querySelector('[data-season-fields] input')?.getAttribute('aria-invalid')).toBe('true');
    expect(text(section().querySelector('[data-season-fields] .f-field-error'))).toBe('Este campo é obrigatorio.');
  });
});
