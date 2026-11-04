// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { LeavableTab } from '../../../core/unsaved-changes/leave-tab.guard';
import { leaveTabGuard } from '../../../core/unsaved-changes/leave-tab.guard';
import { MunicipalitySectionPage } from './municipality-section.page';
import { tabForm } from './tab-form';

/** Unha pestana cun formulario de proba: o seu valor contra o gardado dá o punto. */
@Component({
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (tab.form(); as control) {
    <label for="field">{{ 'x' }}</label
    ><input id="field" [formControl]="control" />
  }`,
})
class FormTab implements LeavableTab {
  protected readonly tab = tabForm<FormControl<string>>('general');
  readonly dirty = this.tab.dirty;
  readonly tabName = computed(() => 'Xeral');
  readonly save = vi.fn(async () => {
    this.tab.set(new FormControl(this.tab.form()?.value ?? '', { nonNullable: true }));
    return true;
  });
  readonly discard = vi.fn(() => this.tab.set(new FormControl('Narón', { nonNullable: true })));

  constructor() {
    this.tab.set(new FormControl('Narón', { nonNullable: true }));
  }
}

@Component({ template: `<p>{{ 'outra' }}</p>` })
class OtherTab {}

const BASE = '/naron/xestion/concello';

describe('Sección «Concello»', () => {
  async function render(legal = {}, url = `${BASE}/xeral`) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(),
        provideRouter([
          {
            path: 'naron/xestion/concello',
            component: MunicipalitySectionPage,
            children: [
              { path: 'xeral', component: FormTab, canDeactivate: [leaveTabGuard] },
              { path: 'legal/:page', component: OtherTab },
              { path: ':tab', component: OtherTab },
            ],
          },
        ]),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality({ legal }));
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement as HTMLElement;
    const tab = (key: string) => el.querySelector<HTMLAnchorElement>(`[data-section-tab="${key}"]`)!;
    const type = async (value: string) => {
      const input = el.querySelector<HTMLInputElement>('#field')!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await harness.fixture.whenStable();
    };
    return { harness, el, tab, type };
  }

  const complete = {
    legalEntityName: 'Concello de Narón',
    nif: 'P1505400J',
    address: 'Praza de Galicia, 1',
    generalEmail: 'concello@naron.gal',
    dpoEmail: 'dpd@naron.gal',
  };

  it('sen título á vista, as sete pestanas con URL e aria-current na escollida; axe', async () => {
    const { el, tab } = await render(complete);
    expect(el.querySelector('h1')?.classList).toContain('sr-only');
    expect(el.querySelector('h1')?.textContent).toBe('Concello');
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe('Concello');
    const links = [...el.querySelectorAll<HTMLAnchorElement>('[data-section-tab]')];
    expect(links.map((a) => [a.textContent?.trim(), a.getAttribute('href')])).toEqual([
      ['Xeral', `${BASE}/xeral`],
      ['Aparencia', `${BASE}/aparencia`],
      ['Servizo', `${BASE}/servizo`],
      ['Legal', `${BASE}/legal`],
      ['Persoal', `${BASE}/persoal`],
      ['Campos personalizados', `${BASE}/campos`],
      ['Rexistro', `${BASE}/rexistro`],
    ]);
    expect(tab('general').getAttribute('aria-current')).toBe('page');
    expect(tab('legal').querySelector('[data-legal-warning]')).toBeNull();
    await expectNoAxeViolations(el);
  });

  it('«Legal» leva un triángulo se faltan datos ou unha páxina está «Sen páxina»', async () => {
    const { tab } = await render({ ...complete, nif: '' });
    expect(tab('legal').querySelector('[data-legal-warning]')).not.toBeNull();
    expect(tab('legal').textContent).toContain(', faltan datos');
    TestBed.inject(CurrentMunicipality).set(municipality({ legal: { ...complete, accessibilityMode: 'NONE' } }));
    await vi.waitFor(() => expect(tab('legal').textContent).toContain(', falta unha páxina legal'));
  });

  it('un cambio pon o punto; volver ao valor de antes quítao', async () => {
    const { tab, type } = await render(complete);
    expect(tab('general').querySelector('[data-dirty]')).toBeNull();
    await type('Narón (novo)');
    expect(tab('general').querySelector('[data-dirty]')).not.toBeNull();
    expect(tab('general').textContent).toContain(', con cambios sen gardar');
    await type('Narón');
    expect(tab('general').querySelector('[data-dirty]')).toBeNull();
  });

  it('saír cun cambio pregunta: Cancelar queda, Descartar sae sen gardar e Gardar garda e sae', async () => {
    const { harness, type } = await render(complete);
    const router = TestBed.inject(Router);
    const tabComponent = () => harness.fixture.debugElement.query(By.directive(FormTab)).componentInstance as FormTab;
    const choose = async (choice: string) => {
      const dialog = await vi.waitFor(() => {
        const found = document.querySelector<HTMLElement>('[role="alertdialog"]');
        expect(found?.textContent).toContain('Gardar os cambios de Xeral?');
        return found!;
      });
      expect(dialog.textContent).toContain('Se os descartas, volven os datos gardados.');
      await vi.waitFor(() => expect(document.activeElement?.getAttribute('data-choice')).toBe('save'));
      dialog.querySelector<HTMLButtonElement>(`[data-choice="${choice}"]`)!.click();
    };

    await type('Outro nome');
    const cancelled = router.navigateByUrl(`${BASE}/aparencia`);
    await choose('cancel');
    expect(await cancelled).toBe(false);
    expect(router.url).toBe(`${BASE}/xeral`);

    const form = tabComponent();
    const discarded = router.navigateByUrl(`${BASE}/aparencia`);
    await choose('discard');
    expect(await discarded).toBe(true);
    expect(form.discard).toHaveBeenCalled();
    expect(form.save).not.toHaveBeenCalled();

    await router.navigateByUrl(`${BASE}/xeral`);
    await harness.fixture.whenStable();
    await type('Outro nome');
    const saving = tabComponent();
    const saved = router.navigateByUrl(`${BASE}/servizo`);
    await choose('save');
    expect(await saved).toBe(true);
    expect(saving.save).toHaveBeenCalled();
  });

  it('sen cambios, sae sen preguntar', async () => {
    await render(complete);
    expect(await TestBed.inject(Router).navigateByUrl(`${BASE}/legal/privacidade`)).toBe(true);
    expect(document.querySelector('[role="alertdialog"]')).toBeNull();
  });
});

describe('leaveTabGuard: dentro da mesma pestana non pregunta', () => {
  it('as páxinas legais de «Legal» comparten pestana', async () => {
    const dirty = signal(true);
    const tab: LeavableTab = {
      dirty,
      tabName: signal('Legal'),
      save: vi.fn(async () => true),
      discard: vi.fn(),
    };
    const route = {
      url: [{ path: 'legal' }, { path: 'privacidade' }],
      pathFromRoot: [
        { url: [] },
        { url: [{ path: 'naron' }, { path: 'xestion' }, { path: 'concello' }] },
        { url: [{ path: 'legal' }, { path: 'privacidade' }] },
      ],
    } as never;
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const run = (url: string) =>
      TestBed.runInInjectionContext(() => leaveTabGuard(tab, route, { url: '' } as never, { url } as never));
    expect(await run('/naron/xestion/concello/legal/aviso-legal')).toBe(true);
    expect(tab.save).not.toHaveBeenCalled();
    expect(document.querySelector('[role="alertdialog"]')).toBeNull();
    dirty.set(false);
    expect(await run('/naron/xestion/concello/xeral')).toBe(true);
  });
});
