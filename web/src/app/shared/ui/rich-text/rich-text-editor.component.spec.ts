// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import type { Editor } from '@tiptap/core';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { RichTextEditorComponent } from './rich-text-editor.component';

@Component({
  imports: [ReactiveFormsModule, RichTextEditorComponent],
  template: `
    <p id="axuda">Axuda</p>
    <furar-rich-text-editor
      fieldId="texto"
      label="Texto de proba"
      lang="gl"
      describedBy="axuda"
      [formControl]="control"
    />
  `,
})
class HostComponent {
  readonly control = new FormControl('<h2>Titular</h2><p>Ola</p>', { nonNullable: true });
}

/** jsdom non mide: ProseMirror pide rectángulos ao levar a selección á vista. */
const rect = { x: 0, y: 0, top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0, toJSON: () => ({}) };
const originals = { rects: Range.prototype.getClientRects, box: Range.prototype.getBoundingClientRect };

describe('RichTextEditorComponent', () => {
  beforeAll(() => {
    Range.prototype.getClientRects = () => [rect] as unknown as DOMRectList;
    Range.prototype.getBoundingClientRect = () => rect as DOMRect;
  });
  afterAll(() => {
    Range.prototype.getClientRects = originals.rects;
    Range.prototype.getBoundingClientRect = originals.box;
  });

  async function render(initial?: string) {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(HostComponent);
    if (initial !== undefined) fixture.componentInstance.control.setValue(initial);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const area = () => el.querySelector<HTMLElement>('#texto');
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(area()).not.toBeNull();
    });
    const component = fixture.debugElement.query(By.directive(RichTextEditorComponent))
      .componentInstance as RichTextEditorComponent;
    const editor = () => (component as unknown as { editor: Editor }).editor;
    const buttons = () => [...el.querySelectorAll<HTMLButtonElement>('.f-rte__btn')];
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    return { fixture, el, area: () => area()!, editor, buttons, settle };
  }

  it('ao arrincar non cambia o valor do formulario aínda que o editor o escriba doutro xeito', async () => {
    // O servidor garda a ligazón con `href` antes de `rel`; o editor serialízaa ao revés.
    const saved = '<p><a href="https://sede.example.gal" rel="noopener noreferrer">Sede</a></p>';
    const { fixture, editor, settle } = await render(saved);
    await settle();
    expect(editor().getHTML()).not.toBe(saved);
    expect(fixture.componentInstance.control.value).toBe(saved);
    expect(fixture.componentInstance.control.dirty).toBe(false);
  });

  it('barra accesible con atallos e área co nome, o idioma, a axuda e o texto do formulario', async () => {
    const { el, area, buttons } = await render();
    const bar = el.querySelector('[role="toolbar"]');
    expect(bar?.getAttribute('aria-label')).toBe('Formato');
    expect(bar?.getAttribute('aria-controls')).toBe('texto');
    expect(buttons().map((b) => b.getAttribute('aria-label'))).toEqual([
      'Título',
      'Subtítulo',
      'Negrita',
      'Cursiva',
      'Lista',
      'Lista numerada',
      'Ligazón',
    ]);
    expect(buttons().map((b) => b.tabIndex)).toEqual([0, -1, -1, -1, -1, -1, -1]);
    const bold = buttons()[2]!;
    expect(bold.getAttribute('aria-keyshortcuts')).toBe('Control+B');
    expect(bold.getAttribute('aria-pressed')).toBe('false');
    expect(bold.nextElementSibling?.textContent).toContain('Ctrl+B');
    expect(bold.textContent?.trim()).toBe('');
    expect(buttons()[4]!.nextElementSibling?.textContent).toContain('Ctrl+Maiús+8');
    expect(buttons()[6]!.hasAttribute('aria-pressed')).toBe(false);
    expect(area().getAttribute('role')).toBe('textbox');
    expect(area().getAttribute('aria-multiline')).toBe('true');
    expect(area().getAttribute('aria-label')).toBe('Texto de proba');
    expect(area().getAttribute('lang')).toBe('gl');
    expect(area().getAttribute('aria-describedby')).toBe('axuda');
    expect(area().querySelector('h2')?.textContent).toBe('Titular');
    await expectNoAxeViolations(el);
  });

  it('frechas, Inicio e Fin moven o foco pola barra; Esc agocha o tooltip', async () => {
    const { el, buttons, settle } = await render();
    buttons()[0]!.focus();
    buttons()[0]!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    await settle();
    expect(document.activeElement).toBe(buttons()[1]);
    expect(buttons()[1]!.tabIndex).toBe(0);
    buttons()[1]!.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    await settle();
    expect(document.activeElement).toBe(buttons()[6]);
    buttons()[6]!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(el.querySelector('[role="toolbar"]')?.hasAttribute('data-tips-off')).toBe(true);
  });

  it('o formulario e o editor van da man; baleiro é «»', async () => {
    const { fixture, area, editor, settle } = await render();
    const control = fixture.componentInstance.control;
    control.setValue('<p>Novo texto</p>');
    await settle();
    expect(area().textContent).toBe('Novo texto');
    editor().commands.setContent('<h3>Subtítulo</h3><ul><li><p>Un</p></li></ul>', { emitUpdate: true });
    await settle();
    expect(control.value).toBe('<h3>Subtítulo</h3><ul><li><p>Un</p></li></ul>');
    editor().commands.setContent('', { emitUpdate: true });
    expect(control.value).toBe('');
  });

  it('pegar mete só o texto, nunca o HTML de fóra', async () => {
    const { area, settle } = await render();
    const paste = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(paste, 'clipboardData', {
      value: {
        types: ['text/html', 'text/plain'],
        getData: (type: string) =>
          type === 'text/html' ? '<h2 style="color:red">Mal</h2><img src=x onerror=alert(1)>' : 'Só o texto',
      },
    });
    area().dispatchEvent(paste);
    await settle();
    expect(area().textContent).toContain('Só o texto');
    expect(area().querySelector('img')).toBeNull();
    expect(area().querySelectorAll('h2')).toHaveLength(1);
    expect(area().innerHTML).not.toContain('style=');
  });

  it('Ctrl+K abre o campo da ligazón: só https ou correo; Esc pecha sen ligazón', async () => {
    const { fixture, el, area, settle } = await render();
    area().dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true, cancelable: true }));
    await settle();
    const input = () => el.querySelector<HTMLInputElement>('#texto-href');
    await vi.waitFor(() => expect(document.activeElement).toBe(input()));
    const type = async (value: string) => {
      input()!.value = value;
      input()!.dispatchEvent(new Event('input'));
      input()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      await settle();
    };
    await type('javascript:alert(1)');
    expect(el.querySelector('#texto-href-error')?.textContent).toContain('https:// ou mailto:');
    expect(input()?.getAttribute('aria-invalid')).toBe('true');
    await type('https://sede.naron.example');
    expect(input()).toBeNull();
    expect(area().querySelector('a')?.getAttribute('href')).toBe('https://sede.naron.example');
    expect(fixture.componentInstance.control.value).toContain('href="https://sede.naron.example"');

    el.querySelector<HTMLButtonElement>('[data-cmd="link"]')?.click();
    await settle();
    input()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(input()).toBeNull();
  });
});
