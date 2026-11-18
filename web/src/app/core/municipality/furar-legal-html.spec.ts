// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { furarCookiesHtml, furarLegalHtml } from './furar-legal-html';

/** Tradución de proba: a clave e os parámetros, para ver que chegan e que todo vai escapado. */
const t = (key: string, params: Record<string, string> = {}) =>
  [key, ...Object.entries(params).map(([k, v]) => `${k}=${v}`)].join(' ');

const NOTICE = {
  activityVisible: 'P1Y',
  activityRetention: 'P3Y',
  bookingRetention: 'P3Y',
  paidBookingRetention: 'P6Y',
  seriesRequestRetention: 'P1Y',
};

describe('furarLegalHtml', () => {
  it('Privacidade: as seccións do art. 13 cos datos legais do concello (responsable, DPD, encargado, dereitos)', () => {
    const html = furarLegalHtml(
      'privacy',
      {
        name: 'Narón',
        lang: 'gl',
        legal: {
          legalEntityName: 'Concello de Narón',
          nif: 'P1505400J',
          generalEmail: 'concello@naron.example',
          generalPhone: '981 111 111',
          eOfficeUrl: 'https://sede.naron.example',
          dpoEmail: 'dpd@naron.example',
          dpoPhone: '981 000 000',
          processor: 'Servizo <TIC>',
          processingRegisterUrl: 'https://sede.naron.example/rat',
        },
        notice: NOTICE,
      },
      t,
    );
    // A sección do persoal leva a ancla fixa á que apunta o correo da invitación (RGPD art. 14).
    expect(html).toContain('<h2 id="persoal">privacy.staff.heading</h2>');
    expect(html).toContain('privacy.staff.retention visible=common.period.yearsOne count=1');
    expect(html).toContain('<li>privacy.basic.controller owner=Concello de Narón</li>');
    expect(html).toContain(
      '<p>privacy.controller owner=Concello de Narón</p><ul><li>legalNotice.nif value=P1505400J</li>',
    );
    expect(html).toContain('<li>privacy.phone value=981 111 111</li>');
    expect(html).toContain(
      '<h2>privacy.dpoTitle</h2><p>privacy.dpo owner=Concello de Narón</p><ul><li>legalNotice.email',
    );
    expect(html).toContain('<li>privacy.phone value=981 000 000</li>');
    expect(html).toContain('privacy.basic.recipientsProcessor processor=Servizo &lt;TIC&gt;');
    expect(html).toContain('privacy.recipientsProcessor processor=Servizo &lt;TIC&gt;</p>');
    expect(html).toContain('<li>legalNotice.office <a href="https://sede.naron.example">');
    expect(html).toContain('<li>privacy.rightsDpo <a href="mailto:dpd@naron.example">');
    expect(html).toContain('<p>privacy.complaintDpo</p>');
    expect(html).toContain(
      '<h2>privacy.registerTitle</h2><p>privacy.register <a href="https://sede.naron.example/rat">',
    );
    // Os prazos das reservas son os do despregue, non uns fixos.
    expect(html).toContain(
      'privacy.retention.bookings bookings=common.period.yearsOther count=3 paid=common.period.yearsOther count=6',
    );
    // As partidas abertas (ADR-0040): a publicación do nome e o seu prazo, o do despregue.
    expect(html).toContain('<li>privacy.residents.dataOpenMatches</li>');
    expect(html).toContain('<li>privacy.retention.openMatches</li>');
    // 6.1.c e o mínimo de idade, en rexistro formal; sen consentimento como base.
    expect(html).toContain('<li>privacy.residents.basisLegal</li>');
    expect(html).toContain('<p>privacy.residents.minors</p>');
    expect(html).toContain('<li>privacy.residents.dataOnBehalf</li>');
    expect(html).toContain('<p>privacy.security.rateLimit</p>');
    // «Cookies» vai á parte: sae sempre, tamén cun texto propio.
    expect(html).not.toContain('privacy.cookies');
  });

  it('Partidas abertas: consérvanse coma a súa reserva, sen prazo propio', () => {
    const html = furarLegalHtml('privacy', { name: 'Narón', lang: 'gl', legal: {}, notice: NOTICE as never }, t);
    expect(html).toContain('<li>privacy.retention.openMatches</li>');
  });

  it('Privacidade sen datos legais: o concello como responsable, sen DPD nin rexistro e sen dicir quen aloxa o servizo', () => {
    const html = furarLegalHtml('privacy', { name: 'Narón', lang: 'gl', legal: {}, notice: null }, t);
    expect(html).toContain('<p>privacy.controller owner=legalNotice.ownerDefault ofName=de Narón</p><h2>');
    expect(html).not.toContain('privacy.dpoTitle');
    expect(html).not.toContain('privacy.complaintDpo');
    expect(html).not.toContain('privacy.registerTitle');
    expect(html).not.toContain('privacy.recipientsProcessor');
    expect(html).toContain('<li>privacy.basic.recipients</li>');
    expect(html).toContain('<p>privacy.recipientsServices</p>');
    expect(html).not.toContain('<li>privacy.phone');
    expect(html).toContain('privacy.staff.retention visible=common.period.yearsOne count=1');
    expect(html).toContain(
      'privacy.retention.bookings bookings=common.period.yearsOther count=2 paid=common.period.yearsOther count=5',
    );
  });

  it('Cookies: a ancla do pé e só almacenamento técnico', () => {
    const html = furarCookiesHtml(t);
    expect(html).toMatch(/^<h2 id="cookies">privacy\.cookies\.title<\/h2>/);
    expect(html.match(/<li>privacy\.cookies\./g)).toHaveLength(4);
    expect(html).toContain('<p>privacy.cookies.none</p>');
  });

  it('Accesibilidade: ligazóns só a correos e a https; o teléfono como segunda canle; sen unidade, a do concello', () => {
    const html = furarLegalHtml(
      'accessibility',
      {
        name: 'Narón',
        lang: 'gl',
        legal: {
          accessibilityEmail: 'a@naron.example',
          eOfficeUrl: 'https://sede.naron.example',
          generalPhone: '981 111 111',
        },
        notice: null,
        mapEnabled: true,
      },
      t,
    );
    expect(html).toContain('<a href="mailto:a@naron.example">a@naron.example</a>');
    expect(html).toContain('<a href="https://sede.naron.example">https://sede.naron.example</a>');
    expect(html).toContain('<li>accessibility.contactPhone value=981 111 111</li>');
    expect(html).not.toContain('accessibility.contactDefault');
    expect(html).toContain('<p>accessibility.complaintUnitDefault</p>');
    expect(html).toContain('<p>accessibility.outOfScopeMap</p>');
  });

  it('Accesibilidade sen canles nin mapa: o rexistro da entidade e nada fóra do ámbito', () => {
    const html = furarLegalHtml('accessibility', { name: 'Narón', lang: 'gl', legal: {}, notice: null }, t);
    expect(html).not.toContain('accessibility.contactPhone');
    expect(html).toContain('<p>accessibility.contactDefault</p>');
    expect(html).toContain('<p>accessibility.outOfScopeNone</p>');
    // Sen datas fixas: o estado e o método son os da autoavaliación do software.
    expect(html).toContain('<p>accessibility.status</p>');
  });

  it('Aviso legal: titular, NIF, domicilio, correo, teléfono e DIR3 só se os hai; todo valor escapado', () => {
    const html = furarLegalHtml(
      'legalNotice',
      {
        name: 'Narón',
        lang: 'gl',
        legal: {
          nif: 'P1505400J',
          address: '<script>alert(1)</script>',
          generalEmail: 'concello@naron.example',
          generalPhone: '981 111 111',
        },
        notice: null,
      },
      t,
    );
    expect(html).toContain('<li>legalNotice.ownerDefault ofName=de Narón</li>');
    expect(html).toContain('<li>legalNotice.nif value=P1505400J</li>');
    expect(html).toContain('legalNotice.address value=&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(html).toContain('<a href="mailto:concello@naron.example">');
    expect(html).toContain('<li>legalNotice.phone value=981 111 111</li>');
    expect(html).not.toContain('legalNotice.dir3');
    expect(html).not.toContain('<script');
    expect(html).toContain('<p>legalNotice.notOfficeGeneric</p>');
  });

  it('Aviso legal: condicións de uso informativas, coa normativa de uso só se hai ligazón', () => {
    const without = furarLegalHtml('legalNotice', { name: 'Narón', lang: 'gl', legal: {}, notice: null }, t);
    expect(without).toContain('<h2 id="condicions">legalNotice.conditions.title</h2>');
    expect(without).not.toContain('legalNotice.conditions.regulation');
    const withRegulation = furarLegalHtml(
      'legalNotice',
      {
        name: 'Narón',
        lang: 'gl',
        legal: { useRegulationUrl: 'https://naron.example/normativa', eOfficeUrl: 'https://sede.naron.example' },
        notice: null,
      },
      t,
    );
    expect(withRegulation).toContain(
      '<p>legalNotice.conditions.regulation <a href="https://naron.example/normativa">https://naron.example/normativa</a></p>',
    );
    expect(withRegulation).toContain('<p>legalNotice.notOffice <a href');
    expect(withRegulation).not.toContain('<input');
  });

  it('Aviso legal: as obrigas do persoal (credenciais, confidencialidade, avisar), sen nada que aceptar', () => {
    const html = furarLegalHtml('legalNotice', { name: 'Narón', lang: 'gl', legal: {}, notice: null }, t);
    expect(html).toContain('<h2>legalNotice.staffTitle</h2><p>legalNotice.staffIntro</p>');
    expect(html.match(/<li>legalNotice\.staffItems\./g)).toHaveLength(5);
    expect(html).toContain('<li>legalNotice.staffItems.confidentiality</li>');
    expect(html.indexOf('legalNotice.staffTitle')).toBeLessThan(html.indexOf('legalNotice.softwareTitle'));
    expect(html).not.toContain('<input');
  });

  it('un enderezo que non é https nin correo non vira ligazón', () => {
    const html = furarLegalHtml(
      'accessibility',
      { name: 'Narón', lang: 'gl', legal: { accessibilityComplaintUrl: 'javascript:alert(1)' }, notice: null },
      t,
    );
    expect(html).not.toContain('href="javascript');
    expect(html).toContain('accessibility.complaintLink javascript:alert(1)');
  });
});
