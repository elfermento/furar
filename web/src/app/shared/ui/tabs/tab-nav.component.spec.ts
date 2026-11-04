// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterLink, RouterLinkActive, provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { TabLinkDirective, TabNavComponent } from './tab-nav.component';
import { TabStripDirective } from './tab-strip.directive';

@Component({ template: '' })
class PageComponent {}

@Component({
  imports: [TabNavComponent, TabLinkDirective, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-tab-nav label="Concello">
      @for (tab of tabs; track tab) {
        <a furarTabLink [routerLink]="['/s', tab]" routerLinkActive ariaCurrentWhenActive="page">{{ tab }}</a>
      }
    </furar-tab-nav>
  `,
})
class HostComponent {
  readonly tabs = ['xeral', 'aparencia', 'servizo', 'legal'];
}

describe('furar-tab-nav: pestanas que son páxinas', () => {
  async function render(url = '/s/aparencia') {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 's/:tab', component: PageComponent }])] });
    await TestBed.inject(Router).navigateByUrl(url);
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, links: () => [...el.querySelectorAll<HTMLAnchorElement>('a')] };
  }

  it('nav con nome, ligazóns coa liña e aria-current na da páxina á vista; axe', async () => {
    const { el, links, fixture } = await render();
    const nav = el.querySelector('nav')!;
    expect(nav.getAttribute('aria-label')).toBe('Concello');
    expect(el.querySelector('furar-tab-nav')?.getAttribute('data-look')).toBe('line');
    expect(links().every((a) => a.classList.contains('f-tabs__tab'))).toBe(true);
    expect(links().map((a) => a.getAttribute('aria-current'))).toEqual([null, 'page', null, null]);
    expect(el.querySelector('[role="tablist"], [role="tab"]')).toBeNull();
    await TestBed.inject(Router).navigateByUrl('/s/legal');
    await fixture.whenStable();
    expect(links().map((a) => a.getAttribute('aria-current'))).toEqual([null, null, null, 'page']);
    await expectNoAxeViolations(el);
  });

  it('leva á vista a pestana activa ao cargar e ao cambiar de páxina', async () => {
    const reveal = vi.spyOn(TabStripDirective.prototype, 'reveal');
    const { links, fixture } = await render();
    await vi.waitFor(() => expect(reveal).toHaveBeenLastCalledWith(links()[1]));
    await TestBed.inject(Router).navigateByUrl('/s/legal');
    await fixture.whenStable();
    await vi.waitFor(() => expect(reveal).toHaveBeenLastCalledWith(links()[3]));
    reveal.mockRestore();
  });

  it('as frechas moven o foco entre as pestanas dando a volta, sen navegar; Inicio e Fin', async () => {
    const { links } = await render();
    const press = (key: string) => {
      const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
      document.activeElement?.dispatchEvent(event);
      return event;
    };
    links()[1]!.focus();
    expect(press('ArrowRight').defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(links()[2]);
    press('End');
    expect(document.activeElement).toBe(links()[3]);
    press('ArrowRight');
    expect(document.activeElement).toBe(links()[0]);
    press('ArrowLeft');
    expect(document.activeElement).toBe(links()[3]);
    press('Home');
    expect(document.activeElement).toBe(links()[0]);
    expect(press('a').defaultPrevented).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/s/aparencia');
  });
});

describe('furarTabStrip: unha fila que se despraza dentro da franxa', () => {
  @Component({
    imports: [TabStripDirective],
    template: `<div furarTabStrip #strip="furarTabStrip"><a id="a">a</a><a id="b">b</a></div>`,
  })
  class StripHost {}

  function strip(scrollWidth: number, clientWidth: number, scrollLeft = 0) {
    const fixture = TestBed.createComponent(StripHost);
    fixture.detectChanges();
    const element = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('[furarTabStrip]')!;
    Object.defineProperty(element, 'scrollWidth', { value: scrollWidth });
    Object.defineProperty(element, 'clientWidth', { value: clientWidth });
    Object.defineProperty(element, 'scrollLeft', { value: scrollLeft, writable: true });
    const directive = fixture.debugElement.children[0]!.injector.get(TabStripDirective);
    return { element, directive };
  }

  it('marca con data-fade os bordos con máis pestanas; se caben, ningún', () => {
    const fits = strip(300, 300);
    fits.directive.update();
    expect(fits.element.hasAttribute('data-fade')).toBe(false);

    const start = strip(600, 300, 0);
    start.directive.update();
    expect(start.element.dataset['fade']).toBe('r');

    const middle = strip(600, 300, 150);
    middle.directive.update();
    expect(middle.element.dataset['fade']).toBe('l r');

    const end = strip(600, 300, 300);
    end.directive.update();
    expect(end.element.dataset['fade']).toBe('l');
  });

  it('reveal() centra a pestana activa só se a franxa se despraza', () => {
    const { element, directive } = strip(600, 300, 0);
    const tab = element.querySelector<HTMLElement>('#b')!;
    Object.defineProperty(tab, 'offsetLeft', { value: 400 });
    Object.defineProperty(tab, 'offsetWidth', { value: 100 });
    directive.reveal(tab);
    expect(element.scrollLeft).toBe(300);
  });
});
