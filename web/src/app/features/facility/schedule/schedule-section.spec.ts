// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { renderSchedule } from '../../../../testing/facility-schedule';
import { selectText } from '../../../../testing/select';
import { facility } from '../detail/facility.fixture';
import { FACILITY_URL, SCHEDULE_URL, SEASONS_URL, SUMMER, schedule, season } from './schedule.fixture';

const text = (el: Element | null | undefined) => el?.textContent?.replace(/\s+/g, ' ').trim();

describe('Sección Horario: fóra da edición', () => {
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

  it('tempada sen fin: «Desde 1 set. 2026 · sen fin»', async () => {
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

describe('Sección Horario: modo edición en tarxetas', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = () => undefined;
  });

  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const cards = (section: HTMLElement) => [...section.querySelectorAll<HTMLElement>('[data-season]')];
  const errors = (card: HTMLElement) =>
    [...card.querySelectorAll('.f-field-error')].map((error) => error.textContent?.trim());

  it('cada tempada na tarxeta do asistente: título, nome, datas, «Ata» cun interruptor e a papeleira pequena', async () => {
    const { section, edit } = await renderSchedule();
    await edit();
    expect(section().querySelector('[data-seasons]')).toBeNull();
    const [winter, summer] = cards(section());
    expect(cards(section())).toHaveLength(2);
    expect(winter.querySelector('.f-season-card__head h3')?.textContent?.trim()).toBe('Inverno');
    expect(winter.querySelector<HTMLInputElement>('[data-season-name]')?.value).toBe('Inverno');
    expect(winter.querySelectorAll('furar-date-field')).toHaveLength(2);
    expect(winter.querySelector('[data-season-has-end] [role="switch"]')?.getAttribute('aria-checked')).toBe('true');
    expect(winter.querySelector('[data-season-no-end]')).toBeNull();
    const remove = winter.querySelector<HTMLButtonElement>('[data-season-delete] button')!;
    expect(remove.dataset['round']).toBe('true');
    expect(remove.dataset['size']).toBe('sm');
    expect(remove.getAttribute('aria-label')).toBe('Eliminar a tempada Inverno');
    // Inverno ten o sábado distinto: «Por días», coas filas abertas.
    expect(winter.querySelector('[role="radio"][aria-checked="true"]')?.textContent?.trim()).toBe('Por días');
    expect(winter.querySelectorAll('fieldset[data-day-row]')).toHaveLength(8);
    expect(summer.querySelector('h3')?.textContent?.trim()).toBe('Verán');
    const add = section().querySelector<HTMLButtonElement>('[data-season-add]')!;
    expect(add.textContent?.trim()).toBe('Engadir tempada');
    expect(add.dataset['variant']).toBe('secondary');
    await expectNoAxeViolations(section());
  });

  it('«+ Tramo» nunha fila e gardar fai o PUT coa semana', async () => {
    const { section, edit, click, save, stable } = await renderSchedule();
    await edit();
    const [winter] = cards(section());
    const row = winter.querySelector<HTMLElement>('fieldset[data-day-row="0"]')!;
    const add = row.querySelector<HTMLButtonElement>('[data-interval-add]')!;
    expect(add.textContent?.trim()).toBe('Engadir tramo');
    await click(add);
    expect(row.querySelectorAll('[data-interval]')).toHaveLength(2);
    // O foco, no primeiro campo do novo tramo; con dous, cada un coa súa papeleira dentro do grupo.
    await vi.waitFor(() =>
      expect(document.activeElement?.id).toBe(row.querySelectorAll('[data-interval]')[1]!.querySelector('button')!.id),
    );
    const removes = [
      ...row.querySelectorAll<HTMLButtonElement>(
        'furar-field-action [data-interval-remove] button, [data-interval-remove] button',
      ),
    ];
    expect(removes.map((b) => b.getAttribute('aria-label'))).toEqual([
      'Quitar o tramo 8:00–23:00',
      'Quitar o tramo 23:00–24:00',
    ]);
    await expectNoAxeViolations(row);
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
      expect(cards(section())).toHaveLength(0);
    });
  });

  it('quitar un tramo: o foco vai ao tramo anterior e, cun só tramo, sen papeleira', async () => {
    const { section, edit, click } = await renderSchedule();
    await edit();
    const [winter] = cards(section());
    const row = winter.querySelector<HTMLElement>('fieldset[data-day-row="0"]')!;
    await click(row.querySelector<HTMLButtonElement>('[data-interval-add]')!);
    await click(row.querySelectorAll<HTMLButtonElement>('[data-interval-remove] button')[1]!);
    expect(row.querySelectorAll('[data-interval]')).toHaveLength(1);
    expect(row.querySelector('[data-interval-remove]')).toBeNull();
    await vi.waitFor(() => expect(document.activeElement).toBe(row.querySelector('[data-interval] button')));
  });

  it('cada día: o nome á esquerda e o interruptor ao final; as horas só existen co día aberto e volven ao acendelo', async () => {
    const { section, edit, click, save, stable } = await renderSchedule();
    await edit();
    const [winter] = cards(section());
    const row = (i: number) => winter.querySelector<HTMLElement>(`fieldset[data-day-row="${i}"]`)!;
    const toggle = (i: number) => row(i).querySelector<HTMLButtonElement>('[role="switch"]')!;
    // A orde da fila: o nome, despois o interruptor (á dereita do todo) e, só aberto, as horas.
    const line = row(0).querySelector('legend + div')!;
    expect(line.firstElementChild?.textContent?.trim()).toBe('Luns');
    expect(line.children[1].tagName.toLowerCase()).toBe('furar-switch');
    // As horas debaixo, cada tramo unha fila «Abre | Pecha»; cun só tramo, sen papeleira.
    expect(line.querySelector('[data-day-hours]')).toBeNull();
    expect(row(0).querySelectorAll('furar-time-picker')).toHaveLength(2);
    expect([...row(0).querySelectorAll('[data-interval] .f-label')].map((l) => l.textContent?.trim())).toEqual([
      'Abre',
      'Pecha',
    ]);
    expect(row(0).querySelector('furar-field-action, [data-interval-remove]')).toBeNull();
    expect(row(0).textContent).not.toContain('Pechado');

    // Festivos, pechado: nin horas nin «+ Tramo» no DOM.
    expect(toggle(7).getAttribute('aria-checked')).toBe('false');
    expect(row(7).querySelector('[data-day-hours]')).toBeNull();
    expect(row(7).querySelector('furar-time-picker')).toBeNull();
    expect(row(7).querySelector('[data-interval-add]')).toBeNull();

    // Apagar o luns quita as horas do DOM; acendelo de novo recupera as que tiña.
    await click(toggle(0));
    expect(row(0).querySelector('[data-day-hours]')).toBeNull();
    expect(row(0).querySelector('furar-time-picker')).toBeNull();
    await click(toggle(0));
    expect(row(0).querySelectorAll('furar-time-picker')).toHaveLength(2);
    // Acender un día sen tramos propón un válido (9:00 – 14:00).
    await click(toggle(7));
    expect(row(7).querySelectorAll('furar-time-picker')).toHaveLength(2);
    expect(row(7).querySelector('.f-field-error')).toBeNull();
    await expectNoAxeViolations(row(7));

    await save();
    const put = await expectRequest({ method: 'PUT', url: `${SEASONS_URL}/s1` });
    const week = put.request.body.week as { intervals: { startMinute: number; endMinute: number }[] }[];
    expect(week[0].intervals).toEqual([{ startMinute: 480, endMinute: 1380 }]);
    expect(week[7].intervals).toEqual([{ startMinute: 540, endMinute: 840 }]);
    for (const { startMinute, endMinute } of week.flatMap((day) => day.intervals)) {
      expect(startMinute % 15).toBe(0);
      expect(endMinute % 15).toBe(0);
      expect(endMinute).toBeGreaterThan(startMinute);
    }
    put.flush(season({ version: 5 }));
    (await expectRequest({ method: 'GET', url: SCHEDULE_URL })).flush(schedule());
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await stable();
      expect(cards(section())).toHaveLength(0);
    });
  });

  it('fila con erros: «Gardar» non manda nada e o erro queda na fila', async () => {
    const { section, edit, pickTime, save, el } = await renderSchedule();
    await edit();
    const row = cards(section())[0].querySelector<HTMLElement>('fieldset[data-day-row="0"]')!;
    await pickTime(row.querySelectorAll('furar-time-picker')[1], 420);
    await save();
    expect(backend().match({ method: 'PUT', url: `${SEASONS_URL}/s1` })).toHaveLength(0);
    expect(row.querySelector('.f-field-error')?.textContent).toContain('remata');
    expect(el.querySelector('furar-action-bar')).not.toBeNull();
  });

  it('«Engadir tempada»: tarxeta nova co foco no nome; se as datas pisan outra, o erro vai no seu «Desde»', async () => {
    const { section, edit, click, type, pickDate, save } = await renderSchedule(schedule({ seasons: [season()] }));
    await edit();
    await click(section().querySelector('[data-season-add]')!);
    const card = cards(section())[1];
    expect(card.querySelector('h3')?.textContent?.trim()).toBe('Nova tempada');
    expect(document.activeElement).toBe(card.querySelector('[data-season-name]'));
    expect(selectText(card.querySelector('[data-season-copy]')!)).toBe('Inverno');
    await type(card.querySelector<HTMLInputElement>('[data-season-name]')!, 'Semana Santa');
    await pickDate(card, 0, 0);
    await pickDate(card, 1, 3);
    expect(errors(card).join(' ')).toContain('Estas datas pisan a tempada «Inverno»');
    await expectNoAxeViolations(section());
    await save();
    backend().expectNone({ method: 'POST', url: SEASONS_URL });
  });

  it('nova tempada sen solape: pendente ata gardar; entón POST e PUT coa semana copiada da anterior', async () => {
    const { section, edit, click, type, pickDate, save, stable } = await renderSchedule(
      schedule({ seasons: [SUMMER], currentSeasonId: undefined }),
    );
    await edit();
    await click(section().querySelector('[data-season-add]')!);
    const card = cards(section())[1];
    await type(card.querySelector<HTMLInputElement>('[data-season-name]')!, 'Outono');
    const from = await pickDate(card, 0, 0);
    const to = await pickDate(card, 1, 3);
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
      expect(cards(section())).toHaveLength(0);
    });
  });

  it('«Ata» apagado quita a data (non queda nin desactivada); ao gardar, o PUT vai sen endDate', async () => {
    const { section, edit, click, save, stable } = await renderSchedule(schedule({ seasons: [season()] }));
    await edit();
    const [card] = cards(section());
    await click(card.querySelector('[data-season-has-end] [role="switch"]')!);
    expect(card.querySelector('[data-season-end]')).toBeNull();
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

  it('eliminar unha tempada: sae da lista e, ao gardar, DELETE co If-Match; o 412 queda no aviso', async () => {
    const { section, edit, click, save, el, stable } = await renderSchedule();
    await edit();
    await click(cards(section())[1].querySelector('[data-season-delete] button')!);
    expect(cards(section())).toHaveLength(1);
    expect(document.activeElement).toBe(section().querySelector('[data-season-add]'));
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

  it('a única tempada tamén se pode quitar na ficha', async () => {
    const { section, edit } = await renderSchedule(schedule({ seasons: [season()] }));
    await edit();
    expect(cards(section())[0].querySelector('[data-season-delete]')).not.toBeNull();
  });

  it('Cancelar volve ás tempadas gardadas sen peticións', async () => {
    const { section, edit, cancel, type } = await renderSchedule(schedule({ seasons: [season(), SUMMER] }));
    await edit();
    await type(cards(section())[0].querySelector<HTMLInputElement>('[data-season-name]')!, 'Outro nome');
    await cancel();
    expect(text(section().querySelector('[data-seasons] button'))).toBe('Inverno Agora');
  });

  it('nome baleiro: non se garda e o erro vai no campo', async () => {
    const { section, edit, type, save } = await renderSchedule(schedule({ seasons: [season()] }));
    await edit();
    const [card] = cards(section());
    await type(card.querySelector<HTMLInputElement>('[data-season-name]')!, ' ');
    await save();
    backend().expectNone({ method: 'PUT', url: `${SEASONS_URL}/s1` });
    expect(card.querySelector('[data-season-name]')?.getAttribute('aria-invalid')).toBe('true');
    expect(errors(card)).toEqual(['Este campo é obrigatorio.']);
  });
});
