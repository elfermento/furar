// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { LanguageService } from '../../core/i18n/language.service';
import type { Language } from '../../core/i18n/languages';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { furarLegalHtml } from '../../core/municipality/furar-legal-html';
import type { LegalPageKey } from '../../core/municipality/legal-pages';
import { LEGAL_PAGES, LEGAL_PAGE_FIELDS } from '../../core/municipality/legal-pages';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import type { LegalForm, LegalTextsForm } from './legal-form';
import { legalValues } from './legal-form';
import { LegalPreviewDialogComponent } from './legal-preview-dialog.component';
import type { PrivacyNoticeResponse } from './municipality.store';
import { MunicipalityStore } from './municipality.store';

const TITLES: Readonly<Record<LegalPageKey, string>> = {
  privacy: 'privacy.title',
  accessibility: 'accessibility.title',
  legalNotice: 'legalNotice.title',
};

/**
 * O que fan os botóns de «Datos legais» (maqueta 33): o texto de Furar cos datos do formulario
 * (vista previa e punto de partida do «Texto propio», nos dous idiomas), a vista previa nun
 * diálogo e «Volver ao texto de Furar» con confirmación. A primeira vez que unha páxina pasa a
 * «Texto propio» sen texto, arranca co de Furar.
 */
@Injectable()
export class LegalPagesActions {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly store = inject(MunicipalityStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);

  private legal: LegalForm | null = null;
  private texts: LegalTextsForm | null = null;
  private notice: Promise<PrivacyNoticeResponse | null> | null = null;

  /** Os formularios cos que traballa; devolve como soltalos. */
  attach(legal: LegalForm, texts: LegalTextsForm): () => void {
    this.legal = legal;
    this.texts = texts;
    const subscriptions = LEGAL_PAGES.map((page) =>
      legal.controls[LEGAL_PAGE_FIELDS[page].mode].valueChanges.subscribe((mode) => {
        if (mode === 'TEXT') void this.startText(page);
      }),
    );
    return () => subscriptions.forEach((s) => s.unsubscribe());
  }

  /** O texto que escribiría Furar para a páxina, cos datos do formulario e no idioma pedido. */
  async generated(page: LegalPageKey, lang: Language): Promise<string> {
    const legal = this.legal;
    if (!legal) return '';
    await firstValueFrom(this.transloco.load(lang));
    const notice = page === 'privacy' ? await this.privacyNotice() : null;
    return furarLegalHtml(
      page,
      { name: this.municipality.name() ?? '', lang, legal: legalValues(legal), notice },
      (key, params) => this.transloco.translate(key, params, lang),
    );
  }

  /** Vista previa: o texto propio do idioma (se o hai) ou o de Furar. */
  async preview(page: LegalPageKey, lang: Language = this.language.lang()): Promise<void> {
    const own = this.legal?.controls[LEGAL_PAGE_FIELDS[page].mode].value === 'TEXT';
    const html = (own ? this.texts?.controls[page].controls[lang].value : '') || (await this.generated(page, lang));
    await firstValueFrom(this.transloco.load(lang));
    const title = this.transloco.translate(TITLES[page], {}, lang);
    this.dialogs.open(LegalPreviewDialogComponent, { data: { title, html, lang } });
  }

  /** «Volver ao texto de Furar»: pregunta e substitúe o texto nos dous idiomas. */
  async confirmReset(page: LegalPageKey): Promise<boolean> {
    const t = (key: string) => this.transloco.translate(key);
    const confirmed = await this.dialogs.confirm({
      heading: t('settings.legal.resetTitle'),
      message: t('settings.legal.resetMessage'),
      confirmLabel: t('settings.legal.reset'),
    });
    if (confirmed) await this.fill(page);
    return confirmed;
  }

  private async startText(page: LegalPageKey): Promise<void> {
    const text = this.texts?.controls[page].getRawValue();
    if (text && !text.gl.trim() && !text.es.trim()) await this.fill(page);
  }

  private async fill(page: LegalPageKey): Promise<void> {
    const group = this.texts?.controls[page];
    if (!group) return;
    const [gl, es] = await Promise.all([this.generated(page, 'gl'), this.generated(page, 'es')]);
    group.setValue({ gl, es });
    group.markAsDirty();
  }

  /** Os prazos do rexistro para «Privacidade», unha vez; sen eles, os prazos por defecto. */
  private privacyNotice(): Promise<PrivacyNoticeResponse | null> {
    this.notice ??= this.store.privacyNotice().catch(() => null);
    return this.notice;
  }
}
