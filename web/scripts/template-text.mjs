// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Detector de texto solto nos modelos (D25, ADR-0014). Percorre a árbore que xera o
// compilador de Angular (non expresións regulares), así que ve o mesmo ca o build:
//  - texto entre etiquetas;
//  - atributos estáticos de texto (title, alt, placeholder, aria-label, label, help...);
//  - o valor dun <input> que se ve (value="…" ou [value]="'…'" en button, submit, reset,
//    text...; non en hidden, checkbox ou radio, que non o pintan);
//  - propiedades que escriben contido ([innerHTML], [innerText], [textContent]);
//  - literais de texto que chegan á pantalla nun binding de texto ([attr.title]="'…'",
//    [placeholder], [ariaLabel], [label]...) ou nunha interpolación {{ … }};
//  - claves que se pasan ao pipe transloco e non existen.
// Un literal só conta se acaba na saída: as ramas dun ternario, os operandos de +, ?? e ||
// e o lado dereito de &&. Os literais en comparacións, chamadas, índices ou argumentos de
// pipes non son texto visible (p. ex. @if (type === 'TEXT')).
// Úsano scripts/i18n-check.mjs (con parseTemplate) e a regra furar/no-loose-text do
// ESLint (coa árbore de @angular-eslint/template-parser): mesma lóxica nos dous.
import { RecursiveAstVisitor, TmplAstRecursiveVisitor } from '@angular/compiler';

/** Última palabra do nome (camelCase ou kebab) que indica texto que le a persoa. */
const TEXT_WORDS = new Set([
  'label',
  'title',
  'alt',
  'placeholder',
  'help',
  'hint',
  'legend',
  'caption',
  'description',
  'message',
  'summary',
  'text',
  'tooltip',
  'valuetext',
  'roledescription',
]);

/** true se o atributo ou input leva texto visible ou lido polo lector de pantalla. */
export function isTextName(name) {
  const words = name
    .replace(/^attr\./, '')
    .replace(/([a-z])([A-Z])/g, '$1-$2')
    .toLowerCase()
    .split(/[-.]/);
  return TEXT_WORDS.has(words.at(-1));
}

/** Propiedades que poñen contido no DOM: calquera literal nelas chega á pantalla. */
const CONTENT_PROPERTIES = new Set(['innerhtml', 'outerhtml', 'innertext', 'outertext', 'textcontent']);

/** Tipos de <input> que non pintan o seu value (o resto, incluído sen type, si). */
const HIDDEN_VALUE_TYPES = new Set(['hidden', 'checkbox', 'radio', 'color', 'range', 'file', 'image']);

/**
 * true se o atributo ou binding `name` do elemento `element` leva texto que se ve ou se le:
 * nomes de texto (isTextName), propiedades de contido e o value dun <input> que se ve.
 * Un [type] dinámico conta como visible: non se pode saber e o literal non debería estar aí.
 */
export function isVisibleText(name, element) {
  const bare = name.replace(/^attr\./, '');
  if (isTextName(bare) || CONTENT_PROPERTIES.has(bare.toLowerCase())) return true;
  if (bare.toLowerCase() !== 'value' || element?.name?.toLowerCase() !== 'input') return false;
  if (element.inputs?.some((input) => input.name.replace(/^attr\./, '') === 'type')) return true;
  const type = element.attributes?.find((attribute) => attribute.name.toLowerCase() === 'type');
  return !HIDDEN_VALUE_TYPES.has((type?.value ?? 'text').trim().toLowerCase());
}

const hasLetters = (text) => /\p{L}/u.test(text);

/** Literais dunha expresión que acaban na saída (modo 'text') ou que son clave (modo 'key'). */
class OutputLiterals extends RecursiveAstVisitor {
  constructor(report) {
    super();
    this.report = report;
    this.mode = 'text';
  }
  with(mode, ast) {
    const previous = this.mode;
    this.mode = mode;
    ast?.visit(this);
    this.mode = previous;
  }
  visitLiteralPrimitive(ast) {
    if (typeof ast.value === 'string' && this.mode !== 'off') this.report(this.mode, ast.value);
  }
  visitTemplateLiteral(ast) {
    for (const element of ast.elements) {
      if (this.mode === 'text' && hasLetters(element.text)) this.report('text', element.text);
    }
    for (const expression of ast.expressions) expression.visit(this);
  }
  visitConditional(ast) {
    this.with('off', ast.condition);
    ast.trueExp.visit(this);
    ast.falseExp.visit(this);
  }
  visitBinary(ast) {
    if (ast.operation === '+' && this.mode === 'key') {
      // 'ns.' + x + '.title': o primeiro literal é o prefixo da clave; o resto, sufixos.
      ast.left.visit(this);
      this.with('off', ast.right);
    } else if (['+', '??', '||'].includes(ast.operation)) {
      ast.left.visit(this);
      ast.right.visit(this);
    } else if (ast.operation === '&&') {
      this.with('off', ast.left);
      ast.right.visit(this);
    } else {
      this.with('off', ast.left);
      this.with('off', ast.right);
    }
  }
  visitPipe(ast) {
    this.with(ast.name === 'transloco' && this.mode !== 'off' ? 'key' : this.mode, ast.exp);
    for (const arg of ast.args) this.with('off', arg);
  }
  visitCall(ast) {
    this.with('off', ast.receiver);
    for (const arg of ast.args) this.with('off', arg);
  }
  visitSafeCall(ast) {
    this.visitCall(ast);
  }
  visitKeyedRead(ast) {
    this.with('off', ast.receiver);
    this.with('off', ast.key);
  }
  visitSafeKeyedRead(ast) {
    this.visitKeyedRead(ast);
  }
  visitLiteralArray(ast) {
    for (const expression of ast.expressions) this.with('off', expression);
  }
  visitLiteralMap(ast) {
    for (const value of ast.values) this.with('off', value);
  }
  visitPrefixNot(ast) {
    this.with('off', ast.expression);
  }
  visitTypeofExpression(ast) {
    this.with('off', ast.expression);
  }
}

/**
 * Percorre os nodos dun modelo e chama a report(mensaxe, sourceSpan) por cada texto solto.
 * isKey(literal) di se un literal é unha clave de tradución (ou un prefixo «ns.»).
 */
export function findLooseText(nodes, isKey, report) {
  const expression = (ast, span, where) => {
    const literals = new OutputLiterals((mode, value) => {
      if (mode === 'key' && !isKey(value)) {
        report(`${where}: «${value}» non é unha clave de tradución`, span);
      } else if (mode === 'text' && hasLetters(value) && !isKey(value)) {
        report(`${where}: texto literal «${value}» sen traducir (usa unha clave e | transloco)`, span);
      }
    });
    ast?.visit(literals);
  };
  class Visitor extends TmplAstRecursiveVisitor {
    element = null;
    visitElement(element) {
      const parent = this.element;
      this.element = element;
      super.visitElement(element);
      this.element = parent;
    }
    visitText(text) {
      if (hasLetters(text.value)) report(`texto sen traducir no modelo: «${text.value.trim()}»`, text.sourceSpan);
    }
    visitTextAttribute(attribute) {
      if (isVisibleText(attribute.name, this.element) && hasLetters(attribute.value)) {
        report(`atributo ${attribute.name}="${attribute.value}" sen traducir`, attribute.sourceSpan);
      }
    }
    visitBoundText(text) {
      expression(text.value, text.sourceSpan, 'interpolación');
    }
    visitBoundAttribute(attribute) {
      if (isVisibleText(attribute.name, this.element))
        expression(attribute.value, attribute.sourceSpan, `[${attribute.name}]`);
    }
  }
  const visitor = new Visitor();
  for (const node of nodes) node.visit(visitor);
}
