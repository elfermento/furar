// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Datas en galego na consola da conta. A consola formatea con
// toLocaleString('gl', { dateStyle: 'long', timeStyle: 'short' }) e Chrome (tamén o de escritorio)
// non trae os datos de galego de ICU: sae «2026 M11 17 16:16». Se o navegador non sabe galego, isto
// fai só eses formatos («17 de novembro de 2026, 16:16»); o resto queda como o dá o navegador.
const meses = ['xaneiro', 'febreiro', 'marzo', 'abril', 'maio', 'xuño', 'xullo', 'agosto', 'setembro', 'outubro', 'novembro', 'decembro'];

const sabeGalego = () => {
  try {
    return new Intl.DateTimeFormat('gl', { month: 'long' }).format(new Date(2026, 10, 1)) === 'novembro';
  } catch {
    return true;
  }
};

const galego = (locales) => String((Array.isArray(locales) ? locales[0] : locales) ?? '').toLowerCase().startsWith('gl');

if (!sabeGalego()) {
  const orixinal = Date.prototype.toLocaleString;
  Date.prototype.toLocaleString = function toLocaleString(locales, options) {
    const o = options ?? {};
    const soEstilos = Object.keys(o).every((k) => k === 'dateStyle' || k === 'timeStyle');
    const estilos = (o.dateStyle === undefined || o.dateStyle === 'long') && (o.timeStyle === undefined || o.timeStyle === 'short');
    if (!galego(locales) || !soEstilos || !estilos || (!o.dateStyle && !o.timeStyle) || Number.isNaN(this.getTime())) {
      return orixinal.call(this, locales, options);
    }
    const data = o.dateStyle ? `${this.getDate()} de ${meses[this.getMonth()]} de ${this.getFullYear()}` : '';
    const hora = o.timeStyle ? `${String(this.getHours()).padStart(2, '0')}:${String(this.getMinutes()).padStart(2, '0')}` : '';
    return [data, hora].filter(Boolean).join(', ');
  };
}
