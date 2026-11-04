// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { renderUses } from '../../../../testing/facility-uses';
import { facility } from '../detail/facility.fixture';
import { FACILITY_URL, FUTBOL_7, USES_URL, use } from './uses.fixture';

describe('Sección Usos: lectura e modo edición (D1-22)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('fóra da edición: filas coas etiquetas das tarxetas (soft) e sen lapis', async () => {
    const { section } = await renderUses();
    const rows = [...section().querySelectorAll('ul[aria-label="Usos da instalación"] > li')];
    expect(rows).toHaveLength(3);
    const tags = (i: number) => [...rows[i].querySelectorAll('furar-tag')].map((tag) => tag.textContent?.trim());
    expect(tags(0)).toEqual(['Enteira', '90 min', 'Sempre']);
    expect(tags(1)).toEqual(['2 partes: Campo A, Campo B', '60 min', 'Sempre']);
    expect(tags(2)).toEqual(['30 prazas', '120 min', '31/10/2026', 'Bloquea os demais']);
    expect(
      rows.flatMap((row) => [...row.querySelectorAll('furar-tag')].map((t) => t.getAttribute('data-look'))),
    ).toEqual(Array(10).fill('soft'));
    expect(section().querySelector('button')).toBeNull();
    await expectNoAxeViolations(section());
  });

  it('no modo edición cada uso é unha tarxeta que abre o seu modal; «Novo uso» ao final', async () => {
    const { section, edit, click, dialog, form } = await renderUses();
    await edit();
    const tiles = [...section().querySelectorAll<HTMLButtonElement>('button[data-use-edit]')];
    expect(tiles.map((t) => t.querySelector('.font-bold')?.textContent?.trim())).toEqual([
      'Fútbol 11',
      'Fútbol 7',
      'Torneo de Samaín',
    ]);
    expect(section().querySelector('[data-use-add]')?.textContent).toContain('Novo uso');
    await expectNoAxeViolations(section());
    await click(tiles[1]);
    // Maqueta 21: «Editar uso» e, debaixo, a instalación.
    expect(dialog()?.querySelector('h2')?.textContent?.trim()).toBe('Editar uso');
    expect(dialog()?.querySelector('.f-dialog__subheading')?.textContent?.trim()).toBe('Campo de fútbol 1 de O Alto');
    expect(form().querySelector('input')?.value).toBe('Fútbol 7');
    await expectNoAxeViolations(dialog()!);
  });

  it('cada uso leva o seu prezo á dereita, fóra e dentro da edición (maqueta 54)', async () => {
    const paid = use({ id: 'u1', name: 'Pista', price: { kind: 'PAID', amountCents: 4500, per: 'BOOKING' } });
    const free = use({ id: 'u2', name: 'Escolas', price: { kind: 'FREE', per: 'BOOKING' } });
    const seats = use({
      id: 'u3',
      name: 'Piscina',
      rentalMode: 'SEATS',
      capacity: 20,
      price: { kind: 'PAID', amountCents: 350, per: 'PERSON' },
    });
    const none = use({ id: 'u4', name: 'Libre', price: { kind: 'NONE', per: 'BOOKING' } });
    const { section, edit } = await renderUses([paid, free, seats, none]);
    const prices = () =>
      [...section().querySelectorAll('[data-use-price]')].map((p) => p.textContent?.replace(/\s+/g, ' ').trim());
    const expected = ['45,00 € por reserva de 90 min', 'Gratis', '3,50 € por persoa', 'Sen prezo'];
    expect(prices()).toEqual(expected);
    expect(section().querySelectorAll('[data-use-price][data-muted]')).toHaveLength(1);
    await expectNoAxeViolations(section());
    await edit();
    expect(prices()).toEqual(expected);
  });

  it('baleiro: fóra da edición, o texto; en edición, «Novo uso»', async () => {
    const { section, edit } = await renderUses([]);
    expect(section().querySelector('[data-uses-empty]')?.textContent).toContain('Aínda non hai usos');
    await edit();
    expect(section().querySelector('[data-use-add]')).not.toBeNull();
  });

  it('erro ao cargar: aviso e «Tentar de novo» volve pedir', async () => {
    const { section, button, click, stable } = await renderUses('error');
    expect(section().textContent).toContain('Non se puideron cargar os usos');
    await click(button('Tentar de novo', section()));
    (await expectRequest({ method: 'GET', url: USES_URL })).flush([use()]);
    await vi.waitFor(async () => {
      await stable();
      expect(section().querySelectorAll('furar-row')).toHaveLength(1);
    });
  });

  it('cambiar un uso: nada vai á API ata «Gardar», que manda o If-Match do uso e as partes co seu id', async () => {
    const { section, open, form, type, apply, save, stable } = await renderUses([use(), FUTBOL_7]);
    await open('u2');
    await type(form().querySelector('input')!, 'Fútbol 7 rápido');
    await apply();
    backend().expectNone({ method: 'PUT', url: `${USES_URL}/u2` });
    expect(section().querySelector('[data-use-id="u2"]')?.textContent).toContain('Fútbol 7 rápido');
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${USES_URL}/u2` });
    expect(put.request.headers.get('If-Match')).toBe('"2"');
    expect(put.request.body.parts).toEqual([
      { id: 'p1', name: 'Campo A' },
      { id: 'p2', name: 'Campo B' },
    ]);
    put.flush({ ...FUTBOL_7, name: 'Fútbol 7 rápido', version: 3 });
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await stable();
      expect(section().querySelectorAll('furar-row')[1]?.textContent).toContain('Fútbol 7 rápido');
    });
  });

  it('Cancelar na barra desfai o feito nos modais sen ningunha petición', async () => {
    const { section, open, form, type, apply, cancel, el } = await renderUses([use(), FUTBOL_7]);
    await open('u1');
    await type(form().querySelector('input')!, 'Outro nome');
    await apply();
    await cancel();
    expect(el.querySelector('furar-action-bar')).toBeNull();
    expect(section().querySelector('furar-row')?.textContent).toContain('Fútbol 11');
    expect(document.activeElement).toBe(el.querySelector('[data-edit]'));
  });

  it('eliminar: o uso sae da lista e, ao gardar, arquívase co If-Match e recárgase a ficha', async () => {
    const { section, open, dialog, click, save, stable } = await renderUses([use(), FUTBOL_7]);
    await open('u2');
    await click(dialog()!.querySelector('[data-use-delete] button')!);
    expect(section().querySelector('[data-use-id="u2"]')).toBeNull();
    await save();
    const archive = await expectRequest({ method: 'POST', url: `${USES_URL}/u2/archive` });
    expect(archive.request.headers.get('If-Match')).toBe('"2"');
    archive.flush({ ...FUTBOL_7, archivedAt: '2026-10-25T12:00:00Z', version: 3 });
    (await expectRequest(FACILITY_URL)).flush(facility({ missing: ['SCHEDULE'] }));
    await vi.waitFor(async () => {
      await stable();
      expect(section().querySelectorAll('furar-row')).toHaveLength(1);
    });
  });

  it('se a API rexeita un uso, a ficha segue en edición co aviso e o erro vai ao campo ao reabrilo', async () => {
    const { section, open, form, type, apply, save, el, click } = await renderUses([use()]);
    await open('u1');
    await type(form().querySelector('input')!, 'Fútbol 7');
    await apply();
    await save();
    flushProblem(await expectRequest({ method: 'PUT', url: `${USES_URL}/u1` }), 409, 'facility.use-name-taken');
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(() => expect(el.querySelector('[data-edit-status]')?.textContent).toContain('Non se gardou todo'));
    expect(section().querySelector('[role="alert"]')?.textContent).toContain('Fútbol 7');
    await click(section().querySelector('[data-use-id="u1"]')!);
    expect(form().querySelector('[aria-invalid="true"]')).toBe(form().querySelector('input'));
  });

  it('un uso novo eliminado antes de gardar desaparece sen peticións', async () => {
    const { section, open, form, type, apply, click, dialog, cancel } = await renderUses([use()]);
    await open(null);
    await type(form().querySelector('input')!, 'Pista rápida');
    await apply();
    await click(section().querySelector('[data-use-id="new-0"]')!);
    expect(form().querySelector('input')?.value).toBe('Pista rápida');
    await click(dialog()!.querySelector('[data-use-delete] button')!);
    expect(section().querySelector('[data-use-id="new-0"]')).toBeNull();
    await cancel();
  });
});
