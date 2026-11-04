// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import type { LegalPageKey } from '../../core/municipality/legal-pages';
import { LegalPage } from './legal.page';

type Legal = NonNullable<Parameters<typeof municipality>[0]>['legal'];

const NOTICE = { activityVisible: 'P1Y', activityRetention: 'P3Y' };

describe('páxinas legais', () => {
  afterEach(() => backend().verify());

  async function render(page: LegalPageKey, legal: Legal = {}, fragment: string | null = null) {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), { provide: ActivatedRoute, useValue: { fragment: of(fragment) } }],
    });
    TestBed.inject(CurrentMunicipality).set(municipality({ legal }));
    const fixture = TestBed.createComponent(LegalPage);
    fixture.componentRef.setInput('page', page);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const settle = async (check: () => void) =>
      vi.waitFor(async () => {
        await fixture.whenStable();
        check();
      });
    return { fixture, el, settle };
  }

  describe('páxina de Furar', () => {
    it('declaración de accesibilidade: seccións do modelo oficial co concello', async () => {
      const { el } = await render('accessibility');
      expect(el.querySelector('h1')?.textContent).toContain('Declaración de accesibilidade');
      expect([...el.querySelectorAll('h2')].map((h) => h.textContent?.trim())).toEqual([
        'Situación de cumprimento',
        'Contido non accesible',
        'Preparación desta declaración',
        'Observacións e datos de contacto',
        'Procedemento de aplicación',
      ]);
      expect(el.textContent).toContain('O Concello de Narón comprométese');
      expect(el.querySelectorAll('a')).toHaveLength(0);
      expect(el.textContent).toContain('unidade responsable de accesibilidade do Concello de Narón');
      await expectNoAxeViolations(el);
    });

    it('declaración de accesibilidade: contacto directo e órgano de reclamación cos datos legais', async () => {
      const { el } = await render('accessibility', {
        accessibilityEmail: 'accesibilidade@naron.example',
        eOfficeUrl: 'https://sede.naron.example',
        accessibilityUnit: 'a Secretaría xeral do Concello de Narón',
        accessibilityComplaintUrl: 'https://sede.naron.example/reclamacion',
      });
      expect([...el.querySelectorAll('a')].map((a) => a.getAttribute('href'))).toEqual([
        'mailto:accesibilidade@naron.example',
        'https://sede.naron.example',
        'https://sede.naron.example/reclamacion',
      ]);
      expect(el.textContent).toContain('ante a Secretaría xeral do Concello de Narón, unidade responsable');
    });

    it('privacidade: responsable, DPD, encargado, conservación e dereitos cos datos do concello (art. 13)', async () => {
      const { el, settle } = await render('privacy', {
        legalEntityName: 'Concello de Narón',
        nif: 'P1505400J',
        address: 'Praza de Galicia, 1. 15570 Narón',
        generalEmail: 'concello@naron.example',
        eOfficeUrl: 'https://sede.naron.example',
        dpoEmail: 'dpd@naron.example',
        dpoPhone: '981 000 000',
        processor: 'Servizo TIC da Deputación',
      });
      (await expectRequest('/api/v1/privacy-notice')).flush(NOTICE);
      await settle(() => expect(el.textContent).toContain('dpd@naron.example'));
      expect(el.querySelector('h1')?.textContent).toContain('Privacidade');
      expect(el.textContent).toContain('Concello de Narón. Usa esta web para xestionar as contas e as reservas');
      const items = [...el.querySelectorAll('li')].map((li) => li.textContent?.trim());
      expect(items).toEqual(
        expect.arrayContaining([
          'NIF: P1505400J',
          'Domicilio: Praza de Galicia, 1. 15570 Narón',
          'Teléfono: 981 000 000',
          'Delegado de protección de datos: dpd@naron.example',
        ]),
      );
      expect(el.textContent).toContain('Servizo TIC da Deputación aloxa e opera o servizo como encargado');
      expect(el.textContent).toContain('As reservas, 2 anos');
      expect([...el.querySelectorAll('a')].map((a) => a.getAttribute('href'))).toEqual(
        expect.arrayContaining([
          'https://sede.naron.example',
          'mailto:concello@naron.example',
          'mailto:dpd@naron.example',
        ]),
      );
      // O persoal: o rexistro de actividade, bloqueado ao ano, borrado aos tres e o uso disciplinario.
      expect([...el.querySelectorAll('h2')].map((h) => h.textContent?.trim())).toContain('Persoal do concello');
      expect(el.textContent).toContain('tamén disciplinarias (artigo 87 da Lei Orgánica 3/2018)');
      expect(el.textContent).toContain(
        'Cada acción vese na xestión durante 1 ano. Despois queda bloqueada ata que se borra, aos 3 anos,',
      );
      expect(el.textContent).toContain('Ministerio Fiscal');
      // Non promete o que aínda non fai (AP2-02).
      expect(el.textContent).not.toContain('exportación');
      await expectNoAxeViolations(el);
    });

    it('privacidade sen datos legais: o que hai, sen DPD nin canles, e o concello como responsable', async () => {
      const { el, settle } = await render('privacy');
      (await expectRequest('/api/v1/privacy-notice')).flush(NOTICE);
      await settle(() => expect(el.querySelector('h2')).not.toBeNull());
      const headings = [...el.querySelectorAll('h2')].map((h) => h.textContent?.trim());
      expect(headings).not.toContain('Delegado de protección de datos');
      expect(el.textContent).toContain('Concello de Narón. Usa esta web');
      expect(el.textContent).toContain('O propio concello aloxa o servizo.');
      expect(el.textContent).not.toContain('en preparación');
      expect(el.querySelectorAll('[data-legal-body] a')).toHaveLength(0);
      await expectNoAxeViolations(el);
    });

    it('privacidade: o enderezo con #persoal (o do correo da invitación) leva á sección do persoal', async () => {
      const scroll = vi.fn();
      Element.prototype.scrollIntoView = scroll;
      const { el, settle } = await render('privacy', {}, 'persoal');
      (await expectRequest('/api/v1/privacy-notice')).flush(NOTICE);
      await settle(() => expect(document.activeElement?.id).toBe('persoal'));
      const heading = el.querySelector<HTMLElement>('h2#persoal')!;
      expect(heading.textContent).toBe('Persoal do concello');
      expect(scroll).toHaveBeenCalledTimes(1);
      expect(scroll.mock.contexts[0]).toBe(heading);
    });

    it('privacidade: erro da API visible', async () => {
      const { el, settle } = await render('privacy');
      flushProblem(await expectRequest('/api/v1/privacy-notice'), 500, 'common.internal');
      await settle(() => expect(el.querySelector('[role="alert"]')).not.toBeNull());
    });

    it('aviso legal: titular cos datos do concello, as obrigas do persoal e o software', async () => {
      const { el } = await render('legalNotice', {
        legalEntityName: 'Concello de Narón',
        nif: 'P1505400J',
        address: 'Praza de Galicia, 1. 15570 Narón',
        generalEmail: 'concello@naron.example',
        dir3Code: 'L01150548',
      });
      expect(el.querySelector('h1')?.textContent).toContain('Aviso legal');
      const items = [...el.querySelectorAll('li')].map((li) => li.textContent?.trim());
      expect(items.slice(0, 5)).toEqual([
        'Concello de Narón',
        'NIF: P1505400J',
        'Domicilio: Praza de Galicia, 1. 15570 Narón',
        'Correo electrónico: concello@naron.example',
        'Código DIR3: L01150548',
      ]);
      // As obrigas do persoal: publicadas, sen nada que aceptar (ADR-0027, revisión do 2026-11-03).
      expect([...el.querySelectorAll('h2')].map((h) => h.textContent?.trim())).toContain('Obrigas do persoal');
      expect(items).toHaveLength(10);
      expect(el.textContent).toContain('artigo 5 da Lei Orgánica 3/2018');
      expect(el.querySelector('input')).toBeNull();
      expect(el.textContent).toContain('EUPL-1.2');
      // A marca do pé leva a #software: o título conserva a ancla.
      expect(el.querySelector('h2#software')?.textContent?.trim()).toBe('Software');
      await expectNoAxeViolations(el);
    });
  });

  describe('texto propio', () => {
    it('pinta o texto gardado no idioma de quen mira, co estilo de Furar e sen nada executable', async () => {
      const { el, settle } = await render('legalNotice', { legalNoticeMode: 'TEXT' });
      (await expectRequest('/api/v1/municipalities/naron/legal-texts')).flush({
        legalNotice: {
          gl: '<h2>Titular</h2><p>Texto <strong>propio</strong></p><script>window.pwned = true</script>',
          es: '<p>Texto en castellano</p>',
        },
      });
      await settle(() => expect(el.querySelector('[data-legal-body] h2')?.textContent).toBe('Titular'));
      expect(el.querySelector('article')?.classList).toContain('f-prose');
      expect(el.querySelector('article')?.getAttribute('lang')).toBe('gl');
      expect(el.querySelector('script')).toBeNull();
      expect((window as unknown as { pwned?: boolean }).pwned).toBeUndefined();
      await expectNoAxeViolations(el);
    });

    it('sen texto no idioma de quen mira, o outro; sen ningún, o de Furar', async () => {
      const { el, settle } = await render('accessibility', { accessibilityMode: 'TEXT' });
      (await expectRequest('/api/v1/municipalities/naron/legal-texts')).flush({
        accessibility: { es: '<p>Solo en castellano</p>' },
      });
      await settle(() => expect(el.textContent).toContain('Solo en castellano'));
      expect(el.querySelector('article')?.getAttribute('lang')).toBe('es');

      TestBed.inject(CurrentMunicipality).set(municipality({ legal: { legalNoticeMode: 'TEXT' } }));
      const other = TestBed.createComponent(LegalPage);
      other.componentRef.setInput('page', 'legalNotice');
      other.detectChanges();
      (await expectRequest('/api/v1/municipalities/naron/legal-texts')).flush({});
      await vi.waitFor(async () => {
        await other.whenStable();
        expect((other.nativeElement as HTMLElement).textContent).toContain('EUPL-1.2');
      });
    });
  });

  it('páxina propia: ligazón á páxina do concello, sen pedir nada á API', async () => {
    const { el } = await render('privacy', { privacyMode: 'URL', privacyUrl: 'https://naron.example/privacidade' });
    const link = el.querySelector<HTMLAnchorElement>('article a');
    expect(link?.getAttribute('href')).toBe('https://naron.example/privacidade');
    expect(link?.getAttribute('rel')).toBe('noopener noreferrer');
    expect(link?.textContent).toContain('(páxina do concello)');
    await expectNoAxeViolations(el);
  });
});
