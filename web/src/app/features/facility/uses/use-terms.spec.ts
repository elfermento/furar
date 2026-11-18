// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { renderUses } from '../../../../testing/facility-uses';
import { pickOption } from '../../../../testing/select';
import { facility } from '../detail/facility.fixture';
import { FACILITY_URL, SAMAIN, USES_URL, use } from './uses.fixture';

const RULES = {
  minAdvanceHours: 2,
  maxAdvanceDays: 7,
  maxSlots: 2,
  capCount: 2,
  capPeriod: 'WEEK',
  cancelHours: 24,
} as const;
const errorText = (root: Element) =>
  [...root.querySelectorAll('.f-field-error')].map((e) => e.textContent?.replace(/\s+/g, ' ').trim());

describe('Modal do uso: Cando, Duración e prezo e Condicións da reserva', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function edit(target = use({ slotMinutes: 60 })) {
    const ui = await renderUses([target]);
    await ui.open(target.id);
    const rule = (key: string) => ui.form().querySelector<HTMLElement>(`[data-rule="${key}"]`)!;
    const head = (key: string) => rule(key).querySelector<HTMLButtonElement>('.f-use-rule__head')!;
    const field = (name: string) => ui.form().querySelector<HTMLInputElement>(`[data-field="${name}"]`)!;
    /** «Aceptar» e «Gardar»: devolve o corpo do PUT. */
    const put = async () => {
      await ui.apply();
      await ui.save();
      const request = await expectRequest({ method: 'PUT', url: `${USES_URL}/${target.id}` });
      const body = request.request.body;
      request.flush(use({ ...target, version: 3 }));
      (await expectRequest(FACILITY_URL)).flush(facility());
      return body;
    };
    return { ...ui, rule, head, field, put };
  }

  it('«Cando» é un campo coas datas e os días; o panel cambia os días e o valor', async () => {
    const { whenTrigger, openWhen, when, click, button, put } = await edit();
    expect(whenTrigger().textContent).toContain('Sempre · Todos os días');
    await openWhen();
    await click(button('Fins de semana', when()!));
    expect(whenTrigger().textContent).toContain('Fins de semana');
    await click(button('Luns a venres', when()!));
    expect(whenTrigger().textContent).toContain('De luns a venres');
    const day = (name: string) => when()!.querySelector<HTMLButtonElement>(`[aria-label="${name}"]`)!;
    await click(day('martes'));
    await click(day('xoves'));
    expect(whenTrigger().textContent).toContain('Luns, mércores e venres');
    await expectNoAxeViolations(when()!);
    await click(button('Listo', when()!));
    expect(document.activeElement).toBe(whenTrigger());
    expect((await put()).weekdays).toBe(0b0010101);
  });

  it('con datas: «Do … ao …» ou «O …» e «bloquea os demais usos»', async () => {
    const { whenTrigger } = await edit(SAMAIN);
    expect(whenTrigger().textContent).toContain('O 31/10/2026 · bloquea os demais usos');
  });

  it('sen ningún día: erro baixo o resumo e no panel, igual ca o servidor; non garda', async () => {
    const { openWhen, when, click, button, form, apply, dialog } = await edit();
    await openWhen();
    await click(button('Todos', when()!));
    await click(when()!.querySelector<HTMLButtonElement>('[aria-label="luns"]')!);
    for (const name of ['martes', 'mércores', 'xoves', 'venres', 'sábado', 'domingo'])
      await click(when()!.querySelector<HTMLButtonElement>(`[aria-label="${name}"]`)!);
    await click(button('Listo', when()!));
    expect(form().querySelector('[data-use-when]')?.textContent).toContain('Ningún día');
    await apply();
    expect(dialog()).not.toBeNull();
    expect(errorText(form())).toContain('Escolle polo menos un día da semana.');
    expect(errorText(when()!)).toContain('Escolle polo menos un día da semana.');
  });

  it('prezo: despregable Sen prezo · Gratis · De pago; o importe é obrigatorio e de 0,01 a 1.000 €', async () => {
    const { choose, chosen, form, type, apply, dialog, put } = await edit();
    expect(chosen('price')).toBe('Sen prezo');
    expect(form().querySelector('[data-use-amount]')).toBeNull();
    await choose('price', 'De pago');
    const amount = form().querySelector<HTMLInputElement>('[data-use-amount] input')!;
    expect(form().querySelector('[data-use-amount]')?.textContent?.replace(/\s+/g, ' ')).toContain(
      'por reserva de 60 min',
    );
    await apply();
    expect(dialog()).not.toBeNull();
    expect(errorText(form())).toContain('Un uso de pago leva un importe de 0,01 a 1.000 euros.');
    expect(document.activeElement).toBe(amount);
    await type(amount, '1000,01');
    await apply();
    expect(errorText(form())).toContain('Un uso de pago leva un importe de 0,01 a 1.000 euros.');
    await type(amount, '30,5');
    await expectNoAxeViolations(form());
    expect((await put()).price).toEqual({ kind: 'PAID', amountCents: 3050 });
  });

  it('prazas: o importe é por persoa; Gratis non leva importe', async () => {
    const seats = use({ rentalMode: 'SEATS', capacity: 20, price: { kind: 'PAID', amountCents: 300, per: 'PERSON' } });
    const { choose, form, put } = await edit(seats);
    expect(form().querySelector<HTMLInputElement>('[data-use-amount] input')?.value).toBe('3,00');
    expect(form().querySelector('[data-use-amount]')?.textContent).toContain('por persoa');
    await choose('price', 'Gratis');
    expect(form().querySelector('[data-use-amount]')).toBeNull();
    expect((await put()).price).toEqual({ kind: 'FREE' });
  });

  it('normas: baleiras por defecto («Sen establecer»); cada unha garda co seu texto', async () => {
    const { head, rule, field, click, type, form, put, stable } = await edit();
    for (const key of ['advance', 'duration', 'cap', 'cancel']) {
      expect(head(key).textContent).toContain('Sen establecer');
      expect(head(key).getAttribute('aria-expanded')).toBe('false');
    }
    await click(head('advance'));
    expect(head('advance').getAttribute('aria-expanded')).toBe('true');
    expect(document.activeElement).toBe(field('minAdvanceHours'));
    await type(field('minAdvanceHours'), '2');
    await type(field('maxAdvanceDays'), '7');
    expect(head('advance').textContent).toContain('De 2 horas a 7 días antes');
    // Campos con rótulo e o número pegado á unidade («Polo menos | 2 horas antes»).
    const advance = rule('advance');
    expect([...advance.querySelectorAll('furar-field')].map((f) => f.getAttribute('data-affix'))).toEqual([
      'tight',
      'tight',
    ]);
    expect([...advance.querySelectorAll('.f-label')].map((l) => l.textContent?.trim())).toEqual([
      'Polo menos',
      'Como moito',
    ]);
    expect([...advance.querySelectorAll('.f-field-affix__unit')].map((u) => u.textContent?.trim())).toEqual([
      'horas antes',
      'días antes',
    ]);
    expect(advance.querySelector('[data-rule-clear]')?.parentElement?.className).toContain('justify-end');
    await click(head('duration'));
    expect(head('advance').getAttribute('aria-expanded')).toBe('false');
    await type(field('maxSlots'), '2');
    expect(head('duration').textContent).toContain('Ata 2 horas');
    // O exemplo, no «?» de «Como moito».
    expect(rule('duration').querySelector('[id$="-maxSlots-help"]')?.textContent).toContain('60 min × 2 = 2 horas');
    await click(head('cap'));
    await type(field('capCount'), '3');
    await pickOption(form().querySelector('[data-rule="cap"] furar-select')!, 'mes', stable);
    await stable();
    expect(head('cap').textContent).toContain('3 reservas por mes');
    await click(head('cancel'));
    await type(field('cancelHours'), '24');
    expect(head('cancel').textContent).toContain('Ata 24 horas antes');
    await expectNoAxeViolations(form());
    expect((await put()).rules).toEqual({
      minAdvanceHours: 2,
      maxAdvanceDays: 7,
      maxSlots: 2,
      capCount: 3,
      capPeriod: 'MONTH',
      cancelHours: 24,
    });
  });

  it('cada condición baléirase con «Quitar a condición» e deixa de ir no corpo', async () => {
    const { head, click, form, put } = await edit(use({ slotMinutes: 60, rules: { ...RULES } }));
    expect(head('advance').textContent).toContain('De 2 horas a 7 días antes');
    expect(head('duration').textContent).toContain('Ata 2 horas');
    expect(head('cap').textContent).toContain('2 reservas por semana');
    expect(head('cancel').textContent).toContain('Ata 24 horas antes');
    for (const key of ['advance', 'duration', 'cap', 'cancel']) {
      await click(head(key));
      await click(form().querySelector<HTMLButtonElement>(`[data-rule-clear="${key}"]`)!);
      expect(head(key).textContent).toContain('Sen establecer');
      expect(document.activeElement).toBe(head(key));
    }
    expect((await put()).rules).toEqual({});
  });

  it('valida os rangos igual ca o servidor e abre a norma co erro', async () => {
    const { head, field, click, type, form, apply, dialog } = await edit();
    await click(head('duration'));
    await type(field('maxSlots'), '17');
    await click(head('cap'));
    await type(field('capCount'), '0');
    await apply();
    expect(dialog()).not.toBeNull();
    // A norma aberta co erro recibe o foco; a pechada ábrese despois.
    // O erro, debaixo do grupo, co rótulo do campo diante.
    expect(errorText(form())).toEqual(['O valor está fóra do rango permitido.']);
    expect(document.activeElement).toBe(field('capCount'));
    await type(field('capCount'), '50');
    await apply();
    expect(head('duration').getAttribute('aria-expanded')).toBe('true');
    expect(head('cap').getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(field('maxSlots'));
    await type(field('maxSlots'), '16');
    await click(head('advance'));
    await type(field('minAdvanceHours'), '721');
    await apply();
    expect(document.activeElement).toBe(field('minAdvanceHours'));
    await type(field('minAdvanceHours'), '0');
    await type(field('maxAdvanceDays'), '0');
    await apply();
    expect(document.activeElement).toBe(field('maxAdvanceDays'));
    await type(field('maxAdvanceDays'), '365');
    await click(head('cancel'));
    await type(field('cancelHours'), '720');
    await apply();
    expect(dialog()).toBeNull();
    backend().expectNone({ method: 'PUT', url: `${USES_URL}/u1` });
  });
});
