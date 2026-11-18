// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy, provideRouter } from '@angular/router';
import type { Translation, TranslocoLoader } from '@jsverse/transloco';
import { provideTransloco } from '@jsverse/transloco';
import type { Observable } from 'rxjs';
import { Subject } from 'rxjs';
import es from '../../../i18n/es.json';
import gl from '../../../i18n/gl.json';
import { LanguageService } from '../i18n/language.service';
import { TranslatedTitleStrategy } from './translated-title.strategy';

@Component({ changeDetection: ChangeDetectionStrategy.OnPush, template: '' })
class PageComponent {}

/** Os textos chegan cando a proba o di, coma o chunk de cada idioma no arranque. */
class PendingLoader implements TranslocoLoader {
  readonly pending: Record<string, Subject<Translation>> = { gl: new Subject(), es: new Subject() };

  getTranslation(lang: string): Observable<Translation> {
    return this.pending[lang];
  }

  deliver(lang: 'gl' | 'es'): void {
    this.pending[lang].next(lang === 'gl' ? gl : es);
    this.pending[lang].complete();
  }
}

describe('TranslatedTitleStrategy', () => {
  const loader = new PendingLoader();

  beforeEach(() => {
    localStorage.clear();
    loader.pending['gl'] = new Subject();
    loader.pending['es'] = new Subject();
    TestBed.configureTestingModule({
      providers: [
        provideTransloco({
          config: { availableLangs: ['gl', 'es'], defaultLang: 'gl', fallbackLang: 'gl', prodMode: true },
          loader: class {
            getTranslation = (lang: string) => loader.getTranslation(lang);
          },
        }),
        provideRouter([{ path: '', component: PageComponent, title: 'titles.home' }]),
        { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
      ],
    });
    TestBed.inject(Title).setTitle('Reservas deportivas');
  });

  async function start(lang: 'gl' | 'es'): Promise<{ title: Title; loaded: Promise<void> }> {
    const loaded = TestBed.inject(LanguageService).use(lang);
    await TestBed.inject(Router).navigateByUrl('/');
    TestBed.tick();
    return { title: TestBed.inject(Title), loaded };
  }

  it('sen os textos cargados queda o título de index.html, nunca a clave', async () => {
    const { title, loaded } = await start('gl');
    expect(title.getTitle()).toBe('Reservas deportivas');
    loader.deliver('gl');
    await loaded;
    expect(title.getTitle()).toBe('Inicio · Reservas deportivas');
  });

  it('noutro idioma ca o por defecto, tamén: o título chega cos seus textos', async () => {
    const { title, loaded } = await start('es');
    expect(title.getTitle()).toBe('Reservas deportivas');
    loader.deliver('es');
    await loaded;
    expect(title.getTitle()).toBe(`${es.titles.home} · ${es.app.name}`);
    expect(title.getTitle()).not.toContain('app.name');
  });
});
