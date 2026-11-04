// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { furarLegalHtml } from './furar-legal-html';

/** Tradución de proba: a clave e os parámetros, para ver que chegan e que todo vai escapado. */
const t = (key: string, params: Record<string, string> = {}) =>
  [key, ...Object.entries(params).map(([k, v]) => `${k}=${v}`)].join(' ');

const NOTICE = { activityVisible: 'P1Y', activityRetention: 'P3Y' };

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
          eOfficeUrl: 'https://sede.naron.example',
          dpoEmail: 'dpd@naron.example',
          dpoPhone: '981 000 000',
          processor: 'Servizo <TIC>',
        },
        notice: NOTICE,
      },
      t,
    );
    expect(html.match(/<h2[ >]/g)).toHaveLength(9);
    // A sección do persoal leva a ancla fixa á que apunta o correo da invitación (RGPD art. 14).
    expect(html).toContain('<h2 id="persoal">privacy.staff.heading</h2>');
    expect(html).toContain('privacy.staff.retention visible=common.period.yearsOne count=1');
    expect(html).toContain(
      '<p>privacy.controller owner=Concello de Narón</p><ul><li>legalNotice.nif value=P1505400J</li>',
    );
    expect(html).toContain('<h2>privacy.dpoTitle</h2><ul><li>legalNotice.email <a href="mailto:dpd@naron.example">');
    expect(html).toContain('<li>privacy.phone value=981 000 000</li>');
    expect(html).toContain('privacy.recipientsProcessor processor=Servizo &lt;TIC&gt;');
    expect(html).toContain('<li>legalNotice.office <a href="https://sede.naron.example">');
    expect(html).toContain('<li>privacy.rightsDpo <a href="mailto:dpd@naron.example">');
  });

  it('Privacidade sen datos legais: o concello como responsable, sen DPD, sen canles e cos prazos por defecto', () => {
    const html = furarLegalHtml('privacy', { name: 'Narón', lang: 'gl', legal: {}, notice: null }, t);
    expect(html).toContain('<p>privacy.controller owner=legalNotice.ownerDefault ofName=de Narón</p><h2>');
    expect(html).not.toContain('privacy.dpoTitle');
    expect(html).not.toContain('<ul>');
    expect(html).toContain('<p>privacy.recipients</p>');
    expect(html).toContain('privacy.staff.retention visible=common.period.yearsOne count=1');
    expect(html.match(/<h2[ >]/g)).toHaveLength(8);
  });

  it('Accesibilidade: ligazóns só a correos e a https; sen unidade, a do concello', () => {
    const html = furarLegalHtml(
      'accessibility',
      {
        name: 'Narón',
        lang: 'gl',
        legal: { accessibilityEmail: 'a@naron.example', eOfficeUrl: 'https://sede.naron.example' },
        notice: null,
      },
      t,
    );
    expect(html).toContain('<a href="mailto:a@naron.example">a@naron.example</a>');
    expect(html).toContain('<a href="https://sede.naron.example">https://sede.naron.example</a>');
    expect(html).toContain('accessibility.complaintUnitDefault ofName=de Narón');
  });

  it('Aviso legal: titular, NIF, domicilio, correo e DIR3 só se os hai; todo valor escapado', () => {
    const html = furarLegalHtml(
      'legalNotice',
      {
        name: 'Narón',
        lang: 'gl',
        legal: { nif: 'P1505400J', address: '<script>alert(1)</script>', generalEmail: 'concello@naron.example' },
        notice: null,
      },
      t,
    );
    expect(html).toContain('<li>legalNotice.ownerDefault ofName=de Narón</li>');
    expect(html).toContain('<li>legalNotice.nif value=P1505400J</li>');
    expect(html).toContain('legalNotice.address value=&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(html).toContain('<a href="mailto:concello@naron.example">');
    expect(html).not.toContain('legalNotice.dir3');
    expect(html).not.toContain('<script');
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
