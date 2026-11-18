// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { MunicipalityDirectory } from '../../../core/municipality/municipality-directory';
import { AllMunicipalitiesLinkComponent } from './all-municipalities-link.component';

@Component({
  imports: [AllMunicipalitiesLinkComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<main>
    <furar-all-municipalities-link />
    <h1>Instalacións</h1>
  </main>`,
})
class TownPage {}

@Component({ template: '<h1>Portada común</h1>', changeDetection: ChangeDetectionStrategy.OnPush })
class CommonHome {}

const DIRECTORY = '/api/v1/municipalities?page=0&size=100';

function page(slugs: string[]) {
  return {
    items: slugs.map((slug, i) => ({ id: String(i), slug, name: slug })),
    page: 0,
    size: 100,
    totalItems: slugs.length,
  };
}

describe('«‹ Todos os concellos»', () => {
  afterEach(() => backend().verify());

  async function render(loaded?: string[]) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(),
        provideRouter([
          { path: '', component: CommonHome },
          { path: 'naron', component: TownPage },
        ]),
      ],
    });
    if (loaded) {
      const read = TestBed.inject(MunicipalityDirectory).fetch();
      (await expectRequest(DIRECTORY)).flush(page(loaded));
      await read;
    }
    const harness = await RouterTestingHarness.create('/naron');
    const link = () => harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a[data-all-municipalities]');
    return { harness, link };
  }

  it('con varios concellos, enlace real á portada común, enriba do título; pasa axe', async () => {
    const { harness, link } = await render();
    (await expectRequest(DIRECTORY)).flush(page(['naron', 'ferrol']));
    await TestBed.inject(MunicipalityDirectory).list();
    harness.detectChanges();

    const a = link()!;
    expect(a.tagName).toBe('A');
    expect(a.getAttribute('href')).toBe('/');
    expect(a.getAttribute('aria-label')).toBe('Todos os concellos');
    expect(a.closest('furar-all-municipalities-link')?.nextElementSibling?.tagName).toBe('H1');
    await expectNoAxeViolations(harness.routeNativeElement!);

    a.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('cun só concello, sen enlace', async () => {
    const { harness, link } = await render();
    (await expectRequest(DIRECTORY)).flush(page(['naron']));
    await TestBed.inject(MunicipalityDirectory).list();
    harness.detectChanges();
    expect(link()).toBeNull();
  });

  it('se a raíz xa leu a lista, non a pide outra vez', async () => {
    const { harness, link } = await render(['naron', 'ferrol']);
    expect(link()).not.toBeNull();
    expect(harness.routeNativeElement!.querySelector('h1')).not.toBeNull();
  });

  it('se a lista non se pode ler, sen enlace', async () => {
    const { harness, link } = await render();
    (await expectRequest(DIRECTORY)).flush({}, { status: 500, statusText: 'x' });
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(link()).toBeNull();
  });
});
