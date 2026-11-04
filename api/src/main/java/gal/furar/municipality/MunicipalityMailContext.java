// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que precisa un correo do concello (plan do dominio 2, §1.2.15, D2-09; A19: só tipos da raíz e
 * de Java): nome, idiomas, cor principal, contacto do servizo de deportes e as notas de pago e
 * devolución que se mostran (as do concello ou as de Furar) e a ligazón a «Privacidade».
 *
 * @param languages códigos ({@code gl}, {@code es}) na orde do concello
 * @param primaryColor cor principal do tema, {@code #rrggbb}
 * @param onPrimaryColor cor do texto sobre a principal
 * @param privacyUrl a páxina de privacidade propia do concello, se a publica fóra de Furar (modo
 *     «Páxina propia»); nulo = a de Furar
 * @param privacyPublished falso se o concello non publica a páxina de privacidade (modo «Sen
 *     páxina»): o correo non leva a ligazón
 * @param paymentNotesByFacility cada instalación leva os seus textos de pago e devolución: {@code
 *     paymentNote} e {@code refundNote} son entón os de Furar, para as que non teñen
 */
public record MunicipalityMailContext(
    UUID id,
    String slug,
    String name,
    List<String> languages,
    String defaultLanguage,
    String primaryColor,
    String onPrimaryColor,
    Contact contact,
    Text paymentNote,
    Text refundNote,
    @Nullable String privacyUrl,
    boolean privacyPublished,
    boolean paymentNotesByFacility) {

  public MunicipalityMailContext {
    languages = List.copyOf(languages);
  }

  /** Concello coa páxina de privacidade publicada e os mesmos textos para todas as instalacións. */
  public MunicipalityMailContext(
      UUID id,
      String slug,
      String name,
      List<String> languages,
      String defaultLanguage,
      String primaryColor,
      String onPrimaryColor,
      Contact contact,
      Text paymentNote,
      Text refundNote,
      @Nullable String privacyUrl) {
    this(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        primaryColor,
        onPrimaryColor,
        contact,
        paymentNote,
        refundNote,
        privacyUrl,
        true,
        false);
  }

  /** Os textos de pago e devolución dunha instalación (os que teña) no canto dos do concello. */
  public MunicipalityMailContext withNotes(@Nullable Text payment, @Nullable Text refund) {
    return new MunicipalityMailContext(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        primaryColor,
        onPrimaryColor,
        contact,
        payment == null ? paymentNote : payment,
        refund == null ? refundNote : refund,
        privacyUrl,
        privacyPublished,
        paymentNotesByFacility);
  }

  /** Concello coa páxina de privacidade de Furar. */
  public MunicipalityMailContext(
      UUID id,
      String slug,
      String name,
      List<String> languages,
      String defaultLanguage,
      String primaryColor,
      String onPrimaryColor,
      Contact contact,
      Text paymentNote,
      Text refundNote) {
    this(
        id,
        slug,
        name,
        languages,
        defaultLanguage,
        primaryColor,
        onPrimaryColor,
        contact,
        paymentNote,
        refundNote,
        null,
        true,
        false);
  }

  /** Contacto do servizo de deportes; cada dato, se o hai. */
  public record Contact(
      @Nullable String name,
      @Nullable String address,
      @Nullable String phone,
      @Nullable String email) {}

  /** Texto en gl e es. */
  public record Text(String gl, String es) {}
}
