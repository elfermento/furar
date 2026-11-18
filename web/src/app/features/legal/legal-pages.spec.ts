// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { NO_MAP, provideDeploymentSettings } from '../../../testing/deployment';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { LegalPage } from './legal.page';
import type { LegalPageName } from './legal.page';

type Legal = NonNullable<Parameters<typeof municipality>[0]>['legal'];

const NOTICE = {
  activityVisible: 'P1Y',
  activityRetention: 'P3Y',
  bookingRetention: 'P2Y',
  paidBookingRetention: 'P5Y',
};

describe('páxinas legais', () => {
  afterEach(() => backend().verify());

  async function render(page: LegalPageName, legal: Legal = {}, fragment: string | null = null, map = NO_MAP) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(),
        provideDeploymentSettings(map),
        { provide: ActivatedRoute, useValue: { fragment: of(fragment) } },
      ],
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
      expect(el.textContent).toContain('Concello de Narón comprométese');
      expect(el.querySelectorAll('a')).toHaveLength(0);
      expect(el.textContent).toContain('unidade responsable de accesibilidade da entidade titular');
      // Sen datas fixas: o estado é o da autoavaliación do software, que se repite en cada versión.
      expect(el.textContent).not.toMatch(/de 20\d\d/);
      expect(el.textContent).toContain('Polo rexistro electrónico ou polo rexistro presencial');
      await expectNoAxeViolations(el);
    });

    it('declaración de accesibilidade: contacto directo e órgano de reclamación cos datos legais', async () => {
      const { el } = await render('accessibility', {
        accessibilityEmail: 'accesibilidade@naron.example',
        eOfficeUrl: 'https://sede.naron.example',
        accessibilityUnit: 'a Secretaría xeral do Concello de Narón',
        accessibilityComplaintUrl: 'https://sede.naron.example/reclamacion',
        generalPhone: '981 111 111',
      });
      expect([...el.querySelectorAll('a')].map((a) => a.getAttribute('href'))).toEqual([
        'mailto:accesibilidade@naron.example',
        'https://sede.naron.example',
        'https://sede.naron.example/reclamacion',
      ]);
      expect(el.textContent).toContain(
        'ante a Secretaría xeral do Concello de Narón, unidade responsable de accesibilidade (artigo 13',
      );
      // O teléfono xeral é a canle complementaria (RD 1112/2018 art. 11).
      expect([...el.querySelectorAll('li')].map((li) => li.textContent?.trim())).toContain('Teléfono: 981 111 111');
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
        processingRegisterUrl: 'https://sede.naron.example/rat',
      });
      (await expectRequest('/api/v1/privacy-notice')).flush(NOTICE);
      await settle(() => expect(el.textContent).toContain('dpd@naron.example'));
      expect(el.querySelector('h1')?.textContent).toContain('Privacidade');
      expect(el.textContent).toContain('Concello de Narón é responsable dos datos persoais');
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
      expect(el.textContent).toContain('Reservas: 2 anos desde a fin da franxa');
      expect(el.textContent).toContain('A partir de 14 anos podes crear a túa conta');
      expect(el.textContent).toContain('artigo 6.1.c do RGPD');
      expect([...el.querySelectorAll('a')].map((a) => a.getAttribute('href'))).toEqual(
        expect.arrayContaining([
          'https://sede.naron.example',
          'mailto:concello@naron.example',
          'mailto:dpd@naron.example',
          'https://sede.naron.example/rat',
        ]),
      );
      // O persoal: o rexistro de actividade, bloqueado ao ano, borrado aos tres e o uso disciplinario.
      expect([...el.querySelectorAll('h2')].map((h) => h.textContent?.trim())).toContain('Persoal do concello');
      expect(el.textContent).toContain('tamén disciplinarias (artigo 87 da Lei Orgánica 3/2018)');
      expect(el.textContent).toContain(
        'Cada acción vese na xestión durante 1 ano. Despois queda bloqueada ata que se borra, aos 3 anos,',
      );
      expect(el.textContent).toContain('Ministerio Fiscal');
      // Non promete o que aínda non fai.
      expect(el.textContent).not.toContain('exportación');
      await expectNoAxeViolations(el);
    });

    it('privacidade sen datos legais: o que hai, sen DPD nin canles, e o concello como responsable', async () => {
      const { el, settle } = await render('privacy');
      (await expectRequest('/api/v1/privacy-notice')).flush(NOTICE);
      await settle(() => expect(el.querySelector('[data-legal-body] h2')).not.toBeNull());
      const headings = [...el.querySelectorAll('h2')].map((h) => h.textContent?.trim());
      expect(headings).not.toContain('Delegado de protección de datos');
      expect(el.textContent).toContain('Concello de Narón é responsable');
      // Sen encargado, non se afirma quen aloxa o servizo.
      expect(el.textContent).not.toContain('aloxa o servizo');
      expect(el.textContent).not.toContain('Rexistro de actividades de tratamento');
      expect(el.textContent).not.toContain('en preparación');
      // Só a sede da AEPD: sen datos, ningunha ligazón do concello.
      expect([...el.querySelectorAll('[data-legal-body] a')].map((a) => a.getAttribute('href'))).toEqual([
        'https://sede.aepd.gob.es',
      ]);
      expect(el.querySelector('[data-legal-cookies] h2#cookies')?.textContent).toBe('Cookies');
      await expectNoAxeViolations(el);
    });

    it('privacidade: «Cookies» sae sempre, tamén sen páxina, e #cookies leva a ela', async () => {
      const scroll = vi.fn();
      Element.prototype.scrollIntoView = scroll;
      const { el, settle } = await render('privacy', { privacyMode: 'NONE' }, 'cookies');
      await settle(() => expect(document.activeElement?.id).toBe('cookies'));
      expect(el.querySelector('[data-legal-none]')).not.toBeNull();
      expect(el.textContent).toContain('Non se usan cookies de análise');
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
      // Condicións de uso: informativas, sen nada que aceptar nin a normativa se non hai ligazón.
      expect(el.querySelector('h2#condicions')?.textContent?.trim()).toBe('Condicións de uso');
      expect(el.textContent).not.toContain('Normativa de uso das instalacións:');
      expect(el.textContent).not.toMatch(/acept/i);
      // As obrigas do persoal: publicadas, sen nada que aceptar (ADR-0027, revisión do 2026-11-03).
      expect([...el.querySelectorAll('h2')].map((h) => h.textContent?.trim())).toContain('Obrigas do persoal');
      // Os da titularidade e as obrigas; e, en «Software», o código, a licenza e as de terceiros.
      expect(items).toHaveLength(13);
      expect(el.textContent).toContain('Este sitio web non é unha sede electrónica.');
      expect(el.textContent).toContain('artigo 5 da Lei Orgánica 3/2018');
      expect(el.querySelector('input')).toBeNull();
      expect(el.textContent).toContain('versión 1.2');
      // «Código e licenzas» do pé leva a #software: o título conserva a ancla e enlaza os tres ficheiros.
      expect(el.querySelector('h2#software')?.textContent?.trim()).toBe('Software');
      const software = [...el.querySelectorAll<HTMLAnchorElement>('h2#software ~ ul a')].map((a) => [
        a.textContent?.trim(),
        a.getAttribute('href'),
      ]);
      expect(software).toEqual([
        ['Código fonte', '/codigo-fonte'],
        ['Licenza (EUPL-1.2)', '/LICENSE.txt'],
        ['Licenzas de terceiros', '/3rdpartylicenses.txt'],
      ]);
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
        expect((other.nativeElement as HTMLElement).textContent).toContain('versión 1.2');
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

  describe('privacidade das solicitudes de alta (/alta-concello/privacidade)', () => {
    it('do despregue, sen concello nin petición: responsable, finalidade, base, conservación e dereitos; pasa axe', async () => {
      TestBed.configureTestingModule({
        providers: [
          ...provideTestApi(),
          provideDeploymentSettings(NO_MAP, null, true),
          { provide: ActivatedRoute, useValue: { fragment: of(null) } },
        ],
      });
      const fixture = TestBed.createComponent(LegalPage);
      fixture.componentRef.setInput('page', 'joinRequests');
      fixture.detectChanges();
      await fixture.whenStable();
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('h1')?.textContent?.trim()).toBe('Privacidade');
      expect(el.querySelector('h2')?.textContent?.trim()).toBe('Solicitudes de alta dun concello');
      const lines = [...el.querySelectorAll('[data-legal-join-requests] li')].map((li) => li.textContent?.trim());
      expect(lines).toHaveLength(5);
      expect(lines[0]).toBe('Responsable: quen opera este servizo, que é quen recibe a solicitude e che contesta.');
      expect(lines[2]).toContain('RGPD art. 6.1.b');
      expect(lines[3]).toContain('como moito 7 días');
      expect(lines[4]).toContain('Axencia Española de Protección de Datos');
      // Nin os textos do concello nin a sección «Cookies».
      expect(el.querySelector('[data-legal-body]')).toBeNull();
      expect(el.querySelector('[data-legal-cookies]')).toBeNull();
      backend().expectNone(() => true);
      await expectNoAxeViolations(el);
    });
  });
});
