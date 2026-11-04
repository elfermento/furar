// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef, OnDestroy } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  booleanAttribute,
  computed,
  effect,
  forwardRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { Editor } from '@tiptap/core';
import { IconComponent } from '../icon/icon.component';
import { HORIZONTAL, rovingIndex } from '../roving-index';
import { StatusStateComponent } from '../status-state/status-state.component';
import { RichTextLinkComponent } from './rich-text-link.component';
import type { RichTextCommand, RichTextState, RichTextTool } from './rich-text-tools';
import { NO_FORMAT, RICH_TEXT_TOOLS } from './rich-text-tools';

let nextId = 0;
/** A área leva o estilo de prosa da páxina pública (mesma tipografía ao editar e ao ler). */
const AREA_CLASS = ['f-rte__area', 'f-prose'].join(' ');

/**
 * Editor de texto con formato mínimo (docs/deseno.md, «Editor de texto»): títulos h2 e h3, negra,
 * cursiva, lista, lista numerada e ligazón (https ou correo). TipTap (MIT, só o núcleo libre)
 * cárgase en diferido a primeira vez que se pinta un editor (`rich-text-setup.ts`): nin entra na
 * carga inicial nin o precarga o service worker. Valor: HTML (`''` se está baleiro); o servidor
 * sanéao sempre cunha lista branca.
 *
 * Accesibilidade: a área é un `textbox` multiliña co nome que pon quen o usa; a barra é un
 * `toolbar` cunha soa parada de tabulador (frechas, Inicio e Fin) e cada botón di o seu atallo no
 * tooltip e en `aria-keyshortcuts`. O tooltip vese co rato e co foco e Esc agóchao (1.4.13). Pegar
 * mete só o texto. A ligazón pídese nun campo baixo a barra (Ctrl/⌘+K), nunca cun `prompt`.
 */
@Component({
  selector: 'furar-rich-text-editor',
  imports: [IconComponent, RichTextLinkComponent, StatusStateComponent, TranslocoPipe],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => RichTextEditorComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-rte', '[attr.data-invalid]': 'invalid() ? "true" : null' },
  template: `
    <div
      class="f-rte__bar"
      role="toolbar"
      [attr.aria-label]="'ui.richText.toolbar' | transloco"
      [attr.aria-controls]="fieldId()"
      [attr.data-tips-off]="tipsOff() ? '' : null"
      (focusin)="tipsOff.set(false)"
      (pointerover)="tipsOff.set(false)"
    >
      @for (tool of tools; track tool.cmd; let i = $index) {
        @if (tool.group) {
          <span class="f-rte__sep" aria-hidden="true"></span>
        }
        <!-- O tooltip vai fóra do botón: dentro, o seu texto sumaríase ao nome (2.5.3). -->
        <span class="f-rte__tool" [attr.data-tip]="tool.tip ?? null">
          <button
            type="button"
            class="f-rte__btn"
            [attr.data-cmd]="tool.cmd"
            [attr.aria-label]="'ui.richText.' + tool.cmd | transloco"
            [attr.aria-keyshortcuts]="mac ? tool.aria.mac : tool.aria.pc"
            [attr.aria-pressed]="tool.cmd === 'link' ? null : state()[tool.cmd]"
            [attr.tabindex]="i === stop() ? 0 : -1"
            [attr.aria-disabled]="!ready() || disabled() ? 'true' : null"
            (mousedown)="$event.preventDefault()"
            (keydown)="onToolbarKeydown($event)"
            (click)="run(tool, i)"
          >
            @if (tool.level) {
              <span class="f-rte__glyph" [attr.data-level]="tool.level" aria-hidden="true"></span>
            } @else if (tool.icon) {
              <furar-icon [name]="tool.icon" />
            }
          </button>
          <span class="f-rte__tip" aria-hidden="true"
            >{{ 'ui.richText.' + tool.cmd | transloco
            }}<kbd>{{ mac ? tool.mac : ('ui.richText.keys.' + tool.cmd | transloco) }}</kbd></span
          >
        </span>
      }
    </div>
    @if (linkHref(); as link) {
      <furar-rich-text-link [fieldId]="fieldId() + '-href'" [href]="link.href" (done)="applyLink($event)" />
    }
    <div #mount class="f-rte__mount"></div>
    @if (!ready()) {
      <furar-status-state mode="loading" />
    }
  `,
})
export class RichTextEditorComponent implements ControlValueAccessor, OnDestroy {
  private readonly mount = viewChild.required<ElementRef<HTMLElement>>('mount');

  /** Id da área editable (para `aria-controls`, a etiqueta e os erros). */
  readonly fieldId = input(`furar-rte-${nextId++}`);
  /** Nome accesible da área («Texto de privacidade en galego»). */
  readonly label = input.required<string>();
  /** Idioma do texto (`lang` da área, para a corrección e os lectores de pantalla). */
  readonly lang = input<string | null>(null);
  /** Ids da axuda e do erro que describen a área. */
  readonly describedBy = input<string | null>(null);
  readonly invalid = input(false, { transform: booleanAttribute });

  protected readonly tools = RICH_TEXT_TOOLS;
  protected readonly mac = /Mac|iPhone|iPad/.test(globalThis.navigator?.platform ?? '');
  protected readonly ready = signal(false);
  protected readonly disabled = signal(false);
  protected readonly state = signal<RichTextState>(NO_FORMAT);
  /** Botón da barra que recibe o tabulador. */
  protected readonly stop = signal(0);
  protected readonly tipsOff = signal(false);
  /** O campo da ligazón aberto, co enderezo da ligazón onde está o cursor (ou baleiro). */
  protected readonly linkHref = signal<{ href: string } | null>(null);

  private editor: Editor | null = null;
  private value = '';
  private destroyed = false;
  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  /** Atributos da área editable; cambian co nome, o idioma e o erro. */
  private readonly attributes = computed<Record<string, string>>(() => {
    const lang = this.lang();
    const describedBy = this.describedBy();
    return {
      id: this.fieldId(),
      class: AREA_CLASS,
      role: 'textbox',
      'aria-multiline': 'true',
      'aria-label': this.label(),
      spellcheck: 'true',
      ...(lang ? { lang } : {}),
      ...(describedBy ? { 'aria-describedby': describedBy } : {}),
      ...(this.invalid() ? { 'aria-invalid': 'true' } : {}),
    };
  });

  constructor() {
    afterNextRender(() => void this.load());
    effect(() => {
      const attributes = this.attributes();
      const editor = this.editor;
      if (this.ready() && editor) {
        editor.setOptions({ editorProps: { ...editor.options.editorProps, attributes } });
      }
    });
    inject(DestroyRef).onDestroy(() => (this.destroyed = true));
  }

  private async load(): Promise<void> {
    const { createRichTextEditor } = await import('./rich-text-setup');
    if (this.destroyed) return;
    this.editor = createRichTextEditor({
      element: this.mount().nativeElement,
      content: this.value,
      attributes: this.attributes(),
      onChange: (html) => {
        this.value = html;
        this.onChange(html);
      },
      onState: () => this.syncState(),
      onBlur: () => this.onTouched(),
      onLinkShortcut: () => this.openLink(),
    });
    // Sen emitir «update»: o editor serializaría o valor ao seu xeito (orde dos atributos) e o
    // formulario quedaría con cambios sen que ninguén escribise nada.
    this.editor.setEditable(!this.disabled(), false);
    this.ready.set(true);
  }

  ngOnDestroy(): void {
    this.editor?.destroy();
    this.editor = null;
  }

  writeValue(value: string | null): void {
    this.value = value ?? '';
    const editor = this.editor;
    if (editor && (editor.isEmpty ? '' : editor.getHTML()) !== this.value) {
      editor.commands.setContent(this.value, { emitUpdate: false });
      this.syncState();
    }
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
    this.editor?.setEditable(!disabled, false);
  }

  /** Foco na área (p. ex. despois de «Volver ao texto de Furar»). */
  focus(): void {
    this.editor?.commands.focus();
  }

  protected run(tool: RichTextTool, index: number): void {
    this.stop.set(index);
    const editor = this.editor;
    // Sen `disabled` (o foco quedaría no aire, 2.4.3): mentres carga ou bloqueado, o clic non fai nada.
    if (!editor || this.disabled()) return;
    const chain = editor.chain().focus();
    const commands: Record<Exclude<RichTextCommand, 'link'>, () => boolean> = {
      h2: () => chain.toggleHeading({ level: 2 }).run(),
      h3: () => chain.toggleHeading({ level: 3 }).run(),
      bold: () => chain.toggleBold().run(),
      italic: () => chain.toggleItalic().run(),
      bulletList: () => chain.toggleBulletList().run(),
      orderedList: () => chain.toggleOrderedList().run(),
    };
    if (tool.cmd === 'link') this.openLink();
    else commands[tool.cmd]();
  }

  protected onToolbarKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape' && !this.tipsOff()) {
      // Esc agocha o tooltip sen mover o foco (1.4.13); a seguinte xa pecha o que haxa fóra.
      this.tipsOff.set(true);
      event.stopPropagation();
      return;
    }
    const bar = (event.currentTarget as HTMLElement).closest('.f-rte__bar');
    const buttons = [...(bar?.querySelectorAll<HTMLButtonElement>('.f-rte__btn') ?? [])];
    const next = rovingIndex(event.key, buttons.indexOf(event.target as HTMLButtonElement), buttons.length, HORIZONTAL);
    if (next === null) return;
    event.preventDefault();
    this.stop.set(next);
    buttons[next]?.focus();
  }

  private openLink(): void {
    const href: unknown = this.editor?.getAttributes('link')['href'];
    if (this.editor) this.linkHref.set({ href: typeof href === 'string' ? href : '' });
  }

  /** Engade ou cambia a ligazón (`null` = cancelar) e volve á área. */
  protected applyLink(href: string | null): void {
    this.linkHref.set(null);
    const editor = this.editor;
    if (!editor) return;
    const chain = editor.chain().focus();
    if (href === null) {
      chain.run();
    } else if (editor.state.selection.empty && !editor.isActive('link')) {
      chain.insertContent({ type: 'text', text: href, marks: [{ type: 'link', attrs: { href } }] }).run();
    } else {
      chain.extendMarkRange('link').setLink({ href }).run();
    }
  }

  private syncState(): void {
    const editor = this.editor;
    if (!editor) return;
    this.state.set({
      h2: editor.isActive('heading', { level: 2 }),
      h3: editor.isActive('heading', { level: 3 }),
      bold: editor.isActive('bold'),
      italic: editor.isActive('italic'),
      bulletList: editor.isActive('bulletList'),
      orderedList: editor.isActive('orderedList'),
    });
  }
}
