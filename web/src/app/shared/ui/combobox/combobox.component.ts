// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { NgTemplateOutlet } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  effect,
  forwardRef,
  inject,
  input,
  numberAttribute,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';
import type { ComboboxKeyTarget } from './combobox-keyboard';
import { comboboxKeydown } from './combobox-keyboard';
import type { ComboboxItem, ComboboxOption, ComboboxSource } from './combobox-search';
import {
  ComboboxSearch,
  comboboxAnnouncement,
  comboboxGroups,
  comboboxItems,
  comboboxPositions,
  matchParts,
  offersCreate,
} from './combobox-search';

export type { ComboboxOption, ComboboxPhase, ComboboxSource } from './combobox-search';

/** `field`: campo de formulario (furarInput). `pill`: sen marco, dentro dunha barra de busca de quen chama. */
export type ComboboxLook = 'field' | 'pill';

let nextId = 0;

/**
 * Campo con suxestións asíncronas (plan do dominio 1, D1-05d): enderezos, deportes. Patrón
 * combobox do APG con lista e sen autocompletado no texto: o foco queda sempre no `input` e a
 * opción activa márcase con `aria-activedescendant`. Busca cando o texto ten `minLength`
 * caracteres e despois de `debounce` ms sen escribir (300 por defecto); a busca anterior
 * cancélase. Mentres, «Buscando…»; sen nada, «Sen resultados»; se falla, o erro (e `failed`,
 * para que a feature ofreza encher a man). Cada resposta anúnciase nun `role="status"` co
 * número de suxestións.
 *
 * Valor (ControlValueAccessor): o `value` da suxestión escollida, ou `null`. Escribir despois de
 * escoller borra o valor: o texto solto non é un valor. Con `allowCreate`, a última opción é
 * «Crear «texto»» e emite `create` co texto; a feature decide que crear e, se quere, fixa o valor.
 *
 * Teclado (APG): frecha abaixo/arriba abren e moven (dan a volta); Alt+abaixo abre sen mover e
 * Alt+arriba pecha; Intro escolle a activa; Esc pecha e, xa pechado, borra o texto; Tab pecha sen
 * escoller. O rótulo visible é de quen chama (`<label for="inputId">`, p. ex. `furar-field`).
 *
 * Listas ricas: se todas as suxestións levan `group`, sae unha lista con grupos (APG, `role="group"`
 * co seu título); `image` ou `icon` poñen unha miniatura decorativa; con `highlight`, a parte do
 * texto que coincide coa busca (sen acentos nin maiúsculas) vai marcada. Con `look="pill"` o campo
 * vai sen marco e a capa ancórase en `panelOrigin` (a barra enteira).
 */
@Component({
  selector: 'furar-combobox',
  imports: [CdkConnectedOverlay, CdkOverlayOrigin, IconComponent, NgTemplateOutlet, TranslocoPipe],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ComboboxComponent), multi: true }],
  host: { class: 'f-combobox', '[attr.data-phase]': 'phase()', '[attr.data-look]': 'look()' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './combobox.component.html',
})
export class ComboboxComponent<T = unknown> implements ControlValueAccessor {
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly field = viewChild.required<ElementRef<HTMLInputElement>>('field');

  /** Fonte das suxestións; recibe o texto sen espazos nos extremos. */
  readonly source = input.required<ComboboxSource<T>>();
  /** Nome da lista de suxestións e do anuncio («Enderezo»); o rótulo visible vai fóra. */
  readonly label = input.required<string>();
  /** Id do `input`, para o `<label for>` de quen chama. */
  readonly inputId = input(`furar-combobox-${nextId}`);
  /** Caracteres mínimos para buscar (enderezos: 3). */
  readonly minLength = input(1, { transform: numberAttribute });
  /** Espera sen escribir antes de buscar, en ms. */
  readonly debounce = input(300, { transform: numberAttribute });
  /** Ofrece ao final «Crear «texto»» (emite `create`), salvo se unha suxestión xa se chama así. */
  readonly allowCreate = input(false, { transform: booleanAttribute });
  /** Verbo da opción de crear, xa traducido («Crear deporte»); por defecto «Crear». */
  readonly createLabel = input('');
  /** Texto do erro, xa traducido; por defecto «Non se puido buscar». */
  readonly errorText = input('');
  /** Exemplo de que escribir, xa traducido. Nunca substitúe o rótulo. */
  readonly placeholder = input('');
  /** Texto do campo para un valor que chega do formulario. */
  readonly displayWith = input<(value: T) => string>((value) => String(value));
  /** Tras escoller, baleira o campo (para engadir varios, coma os deportes). */
  readonly clearOnSelect = input(false, { transform: booleanAttribute });
  /** Marca o campo como inválido; o texto do erro é de quen chama. */
  readonly invalid = input(false, { transform: booleanAttribute });
  /** Id do erro ou da axuda que describe o campo. */
  readonly describedBy = input<string | null>(null);
  /** Marca nas suxestións a parte que coincide coa busca. */
  readonly highlight = input(false, { transform: booleanAttribute });
  /** Aspecto do campo: de formulario (por defecto) ou sen marco dentro dunha barra. */
  readonly look = input<ComboboxLook>('field');
  /** Elemento ao que se ancora a capa (e do que colle o ancho); por defecto, o campo. */
  readonly panelOrigin = input<HTMLElement | null>(null);

  /** Suxestión escollida (tamén con `clearOnSelect`, que non deixa valor). */
  readonly selected = output<ComboboxOption<T>>();
  /** Escolleuse «Crear»: o texto escrito, sen espazos nos extremos. */
  readonly create = output<string>();
  /** A fonte fallou (p. ex. 503 do buscador de enderezos). */
  readonly failed = output<unknown>();

  protected readonly listId = `furar-combobox-${nextId++}-list`;
  protected readonly positions = computed(() => comboboxPositions(this.look() === 'pill' ? 12 : 4));
  protected readonly text = signal('');
  protected readonly open = signal(false);
  protected readonly active = signal(-1);
  protected readonly panelWidth = signal(0);
  private readonly value = signal<T | null>(null);
  protected readonly isDisabled = signal(false);
  private readonly search = new ComboboxSearch<T>({
    source: () => this.source(),
    delay: () => this.debounce(),
    failed: (error) => this.failed.emit(error),
  });
  protected readonly phase = this.search.phase.asReadonly();
  protected readonly options = this.search.options.asReadonly();
  private readonly searched = this.search.searched;

  protected readonly query = computed(() => this.text().trim());

  private readonly canCreate = computed(() => offersCreate(this.allowCreate(), this.searched(), this.options()));
  protected readonly items = computed(() =>
    this.phase() === 'ready' ? comboboxItems(this.listId, this.options(), this.canCreate()) : [],
  );
  protected readonly groups = computed(() => comboboxGroups(this.listId, this.items()));
  protected readonly activeId = computed(() => (this.open() ? (this.items()[this.active()]?.id ?? null) : null));

  protected readonly createText = computed(() => {
    this.lang();
    const action = this.createLabel() || this.transloco.translate('ui.combobox.create');
    return this.transloco.translate('ui.combobox.createWith', { action, query: this.searched() });
  });

  /** Anuncio do `role="status"`; ler o idioma fai que cambie en quente. */
  protected readonly announcement = computed(() => {
    this.lang();
    return comboboxAnnouncement((key, params) => this.transloco.translate(key, params), {
      phase: this.phase(),
      count: this.options().length,
      errorText: this.errorText(),
      createText: this.canCreate() ? this.createText() : null,
    });
  });

  private readonly lang = toSignal(this.transloco.langChanges$);
  private readonly keys: ComboboxKeyTarget = {
    isOpen: () => this.open(),
    hasText: () => this.text() !== '',
    count: () => this.items().length,
    active: () => this.active(),
    reopen: () => this.reopen(),
    close: () => this.close(),
    clear: () => this.clear(),
    setActive: (index) => this.setActive(index),
    chooseActive: () => {
      const item = this.items()[this.active()];
      if (item) {
        this.choose(item);
      }
      return !!item;
    },
  };
  private onChange: (value: T | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.search.destroy());
    // Se a fonte cambia co panel aberto (os datos chegaron despois de escribir), repítese a busca:
    // senón quedaría «Sen resultados» ata teclear outra vez.
    effect(() => {
      this.source();
      untracked(() => this.open() && this.find());
    });
  }

  writeValue(value: T | null): void {
    const v = value ?? null;
    this.value.set(v);
    this.text.set(v === null ? '' : this.displayWith()(v));
    this.reset();
  }

  registerOnChange(fn: (value: T | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.isDisabled.set(disabled);
    if (disabled) {
      this.reset();
    }
  }

  /** Leva o foco ao campo (p. ex. ao abrir un diálogo ou tras un erro). */
  focus(): void {
    this.field().nativeElement.focus();
  }

  protected onInput(event: Event): void {
    this.text.set((event.target as HTMLInputElement).value);
    this.setValue(null);
    this.find();
  }

  protected onKeydown(event: KeyboardEvent): void {
    comboboxKeydown(event, this.keys);
  }

  protected onBlur(): void {
    this.close();
    this.onTouched();
  }

  /**
   * Rato: premer non quita o foco do campo (preventDefault no mousedown) e soltar sobre unha
   * opción escóllea. Vai no mouseup porque o click nun `li` pediría teclado propio (o teclado
   * está no campo, co descendente activo).
   */
  protected onPanelMouseup(event: MouseEvent): void {
    const index = (event.target as HTMLElement).closest<HTMLElement>('[data-index]')?.dataset['index'];
    const item = index === undefined ? undefined : this.items()[Number(index)];
    if (item && event.button === 0) {
      this.choose(item);
    }
  }

  protected close(): void {
    this.open.set(false);
    this.active.set(-1);
  }

  /** Busca co texto actual: abre, mostra «Buscando…» e pide á fonte tras a espera. */
  private find(): void {
    const query = this.query();
    this.active.set(-1);
    if (query.length < this.minLength()) {
      this.reset();
      return;
    }
    this.search.start(query);
    this.openPanel();
  }

  /** Volve abrir coa resposta que xa hai, ou busca se o texto cambiou ou a busca fallou. */
  private reopen(): void {
    if (this.query().length < this.minLength()) {
      return;
    }
    if (this.phase() === 'ready' && this.searched() === this.query()) {
      this.openPanel();
    } else {
      this.find();
    }
  }

  /** Anacos da etiqueta, coa coincidencia marcada se se pide `highlight`. */
  protected parts(label: string) {
    return this.highlight() ? matchParts(label, this.searched()) : [{ text: label, match: false }];
  }

  private openPanel(): void {
    this.panelWidth.set((this.panelOrigin() ?? this.field().nativeElement).offsetWidth || 0);
    this.open.set(true);
  }

  private setActive(index: number): void {
    this.active.set(index);
    const id = this.items()[index]?.id;
    afterNextRender(() => (id ? document.getElementById(id)?.scrollIntoView({ block: 'nearest' }) : undefined), {
      injector: this.injector,
    });
  }

  private choose(item: ComboboxItem<T>): void {
    if (item.kind === 'create') {
      const query = this.searched();
      this.close();
      this.create.emit(query);
      return;
    }
    const { option } = item;
    this.selected.emit(option);
    if (this.clearOnSelect()) {
      this.text.set('');
    } else {
      this.text.set(option.label);
      this.setValue(option.value);
    }
    this.reset();
  }

  private clear(): void {
    this.text.set('');
    this.setValue(null);
    this.reset();
  }

  /** Cambia o valor e avisa ao formulario só se cambia. */
  private setValue(value: T | null): void {
    if (value !== this.value()) {
      this.value.set(value);
      this.onChange(value);
    }
  }

  /** Pecha e esquece a busca (a resposta que chegue tarde xa non se mostra). */
  private reset(): void {
    this.search.reset();
    this.close();
  }
}
