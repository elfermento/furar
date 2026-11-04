// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Casos do detector de texto solto (D25). Corre antes de i18n:check.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseTemplate } from '@angular/compiler';
import { findLooseText, isTextName, isVisibleText } from './template-text.mjs';

const keys = new Set(['picker.listLabel', 'customFields.admin.archive', 'errorPage.home']);
const isKey = (literal) => keys.has(literal) || (literal.endsWith('.') && [...keys].some((k) => k.startsWith(literal)));
const problems = (template) => {
  const out = [];
  findLooseText(parseTemplate(template, 't.html', {}).nodes, isKey, (message) => out.push(message));
  return out;
};

test('detecta literais en bindings de texto', () => {
  for (const template of [
    `<ul [attr.title]="'Lista de concellos'"></ul>`,
    `<input [placeholder]="busy ? 'Buscando' : 'Busca'" />`,
    `<button [attr.aria-label]="'Pechar'"></button>`,
    `<furar-field [label]="'Nome'" />`,
    `<p [attr.aria-label]="x ?? 'Sen nome'"></p>`,
    `<p>{{ 'Crear concello' }}</p>`,
    `<p>{{ ok && 'Gardado' }}</p>`,
    `<input [value]="'Enviar solicitude'" type="submit" />`,
    `<div [innerHTML]="'<b>Aviso</b>'"></div>`,
    `<p [textContent]="ok ? 'Feito' : x"></p>`,
  ]) {
    assert.notEqual(problems(template).length, 0, template);
  }
});

test('detecta texto entre etiquetas, atributos estáticos e claves inexistentes', () => {
  assert.notEqual(problems('<p>Ola</p>').length, 0);
  assert.notEqual(problems('<img alt="Logo" src="a.svg" />').length, 0);
  assert.notEqual(problems(`<p>{{ 'picker.nonExiste' | transloco }}</p>`).length, 0);
});

test('detecta o value que se ve nun <input> (QN3)', () => {
  for (const template of [
    '<input type="button" value="Enviar solicitude" />',
    '<input type="SUBMIT" value="Gardar" />',
    '<input type="reset" value="Borrar" />',
    '<input value="Escribe aquí" />',
    '<input [type]="kind" value="Aceptar" />',
    '<form><fieldset><input type="text" value="Nome" /></fieldset></form>',
  ]) {
    assert.notEqual(problems(template).length, 0, template);
  }
});

test('non conta o value que non se ve', () => {
  for (const template of [
    '<input type="hidden" value="naron" />',
    '<input type="checkbox" value="gl" />',
    '<input type="radio" value="public" />',
    '<option value="gl">{{ \'picker.listLabel\' | transloco }}</option>',
    '<button type="submit" value="save">{{ \'errorPage.home\' | transloco }}</button>',
    '<input type="submit" [value]="\'errorPage.home\' | transloco" />',
  ]) {
    assert.deepEqual(problems(template), [], template);
  }
  assert.ok(!isVisibleText('value', { name: 'li', attributes: [], inputs: [] }));
});

test('acepta claves, prefixos e literais que non chegan á pantalla', () => {
  for (const template of [
    `<ul [attr.aria-label]="'picker.listLabel' | transloco"></ul>`,
    `<p>{{ 'customFields.admin.' + (s === 'ACTIVE' ? 'archive' : 'reactivate') | transloco }}</p>`,
    `<p>{{ 'errorPage.' + kind + '.title' | transloco }}</p>`,
    `<p [attr.aria-invalid]="e ? 'true' : null" [id]="'cf-' + key" [class.x]="t === 'TEXT'"></p>`,
    `@if (type === 'TEXT') { <span>{{ value }}</span> }`,
    `<p [attr.title]="label(def, 'gl')"></p>`,
  ]) {
    assert.deepEqual(problems(template), [], template);
  }
});

test('nomes de texto', () => {
  for (const name of ['title', 'attr.title', 'aria-label', 'ariaLabel', 'placeholder', 'helpText', 'legend']) {
    assert.ok(isTextName(name), name);
  }
  for (const name of ['aria-labelledby', 'aria-describedby', 'id', 'context', 'titleKey', 'lang']) {
    assert.ok(!isTextName(name), name);
  }
});
