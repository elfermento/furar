// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef, ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { BilingualFieldComponent } from '../bilingual-field/bilingual-field.component';
import { InputDirective } from '../input/input.directive';
import { LongTextDirective } from './long-text.directive';

@Component({
  imports: [BilingualFieldComponent, InputDirective, LongTextDirective, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form>
      <label for="name">Nome do servizo <span class="sr-only">(obrigatorio)</span></label>
      <input id="name" furarInput furarLongText maxlength="120" [formControl]="name" />
      <furar-bilingual-field fieldId="note" multiline longText label="Como se paga" [gl]="gl" [es]="es" />
    </form>
  `,
})
class HostComponent {
  readonly name = new FormControl('Servizo Municipal de Deportes', { nonNullable: true });
  readonly gl = new FormControl('Págase con tarxeta.', { nonNullable: true });
  readonly es = new FormControl('Se paga con tarjeta.', { nonNullable: true });
}

describe('[furarLongText]', () => {
  let narrow = true;
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    narrow = true;
    window.matchMedia = ((query: string) => ({ matches: narrow, media: query })) as unknown as typeof window.matchMedia;
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  }

  const stable = () => TestBed.inject(ApplicationRef).whenStable();
  const sheet = () => document.querySelector<HTMLElement>('[role="dialog"]');
  const area = () => document.querySelector<HTMLTextAreaElement>('[data-long-text-input]')!;
  const confirm = () => document.querySelector<HTMLButtonElement>('[data-long-text-confirm]')!;

  function type(text: string): void {
    area().value = text;
    area().dispatchEvent(new Event('input'));
  }

  it('no móbil o campo non se escribe no sitio: tocalo abre a folla co texto enteiro e o rótulo', async () => {
    const fixture = await render();
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#name')!;
    expect(input.readOnly).toBe(true);
    input.click();
    await stable();
    expect(sheet()?.querySelector('h2')?.textContent?.trim()).toBe('Nome do servizo');
    expect(area().value).toBe('Servizo Municipal de Deportes');
    expect(area().getAttribute('aria-label')).toBe('Nome do servizo');
    expect(area().maxLength).toBe(120);
    expect(document.querySelector('.f-dialog-pane--sheet')).not.toBeNull();
    await expectNoAxeViolations(sheet()!);
  });

  it('«Confirmar» leva o texto ao control (sen saltos de liña nun campo dunha liña) e o foco volve ao campo', async () => {
    const fixture = await render();
    const host = fixture.componentInstance;
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#name')!;
    input.focus();
    input.click();
    await stable();
    type('Servizo de Deportes\nde Narón');
    confirm().click();
    await stable();
    await fixture.whenStable();
    expect(host.name.value).toBe('Servizo de Deportes de Narón');
    expect(host.name.dirty).toBe(true);
    expect(sheet()).toBeNull();
    expect(document.activeElement).toBe(input);
  });

  it('Pechar descarta o escrito', async () => {
    const fixture = await render();
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#name')!;
    input.click();
    await stable();
    type('Outro');
    sheet()!.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
    await stable();
    expect(fixture.componentInstance.name.value).toBe('Servizo Municipal de Deportes');
    expect(fixture.componentInstance.name.dirty).toBe(false);
  });

  it('bilingüe: GL/ES na folla, cada idioma ao seu control', async () => {
    const fixture = await render();
    const host = fixture.componentInstance;
    const note = (fixture.nativeElement as HTMLElement).querySelector<HTMLTextAreaElement>('#note')!;
    expect(note.readOnly).toBe(true);
    note.click();
    await stable();
    expect(area().value).toBe('Págase con tarxeta.');
    expect(area().getAttribute('lang')).toBe('gl');
    type('Págase na oficina.');
    sheet()!.querySelector<HTMLButtonElement>('[data-lang="es"]')!.click();
    await stable();
    expect(area().value).toBe('Se paga con tarjeta.');
    expect(area().getAttribute('lang')).toBe('es');
    type('Se paga en la oficina.\nDe lunes a viernes.');
    confirm().click();
    await stable();
    await fixture.whenStable();
    expect(host.gl.value).toBe('Págase na oficina.');
    expect(host.es.value).toBe('Se paga en la oficina.\nDe lunes a viernes.');
    // O campo queda no idioma que se vía ao confirmar.
    expect(note.getAttribute('lang')).toBe('es');
  });

  it('no escritorio, o campo de sempre', async () => {
    narrow = false;
    const fixture = await render();
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#name')!;
    expect(input.readOnly).toBe(false);
    input.click();
    await stable();
    expect(sheet()).toBeNull();
  });
});
