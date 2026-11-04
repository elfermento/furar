// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code municipality}. É a única entidade sen {@code @TenantId}: é a táboa dos concellos
 * ({@code TenancyAllowlist}).
 */
@Entity
@Table(name = "municipality")
public class MunicipalityEntity implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private String slug;

  @Column(nullable = false)
  private String name;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> languages;

  @Column(name = "default_language", nullable = false)
  private String defaultLanguage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, String> theme;

  // Datos legais (ADR-0017 punto 8), opcionais.
  @Column(name = "ine_code")
  private @Nullable String ineCode;

  @Column(name = "dir3_code")
  private @Nullable String dir3Code;

  @Column(name = "legal_entity_name")
  private @Nullable String legalEntityName;

  @Column(name = "e_office_url")
  private @Nullable String eOfficeUrl;

  @Column(name = "nif")
  private @Nullable String nif;

  @Column(name = "address")
  private @Nullable String address;

  @Column(name = "general_email")
  private @Nullable String generalEmail;

  @Column(name = "dpo_email")
  private @Nullable String dpoEmail;

  @Column(name = "dpo_phone")
  private @Nullable String dpoPhone;

  @Column(name = "processor")
  private @Nullable String processor;

  @Column(name = "accessibility_email")
  private @Nullable String accessibilityEmail;

  @Column(name = "accessibility_complaint_url")
  private @Nullable String accessibilityComplaintUrl;

  @Column(name = "accessibility_unit")
  private @Nullable String accessibilityUnit;

  // Páxinas legais (ADR-0017 punto 8): modo, páxina propia e texto propio de cada unha.
  @Column(name = "privacy_mode", nullable = false)
  private String privacyMode = "FURAR";

  @Column(name = "privacy_url")
  private @Nullable String privacyUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "privacy_text")
  private @Nullable Map<String, String> privacyText;

  @Column(name = "accessibility_mode", nullable = false)
  private String accessibilityMode = "FURAR";

  @Column(name = "accessibility_url")
  private @Nullable String accessibilityUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "accessibility_text")
  private @Nullable Map<String, String> accessibilityText;

  @Column(name = "legal_notice_mode", nullable = false)
  private String legalNoticeMode = "FURAR";

  @Column(name = "legal_notice_url")
  private @Nullable String legalNoticeUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "legal_notice_text")
  private @Nullable Map<String, String> legalNoticeText;

  // Contacto do servizo de deportes e notas de pago (D2-02), opcionais.
  @Column(name = "sports_service_name")
  private @Nullable String sportsServiceName;

  @Column(name = "sports_address")
  private @Nullable String sportsAddress;

  @Column(name = "sports_phone")
  private @Nullable String sportsPhone;

  @Column(name = "sports_email")
  private @Nullable String sportsEmail;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payment_note")
  private @Nullable Map<String, String> paymentNote;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "refund_note")
  private @Nullable Map<String, String> refundNote;

  @Column(name = "payment_notes_scope", nullable = false)
  private String paymentNotesScope = "ALL";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private @Nullable Long version;

  protected MunicipalityEntity() {}

  MunicipalityEntity(UUID id, String slug, Instant createdAt) {
    this.id = id;
    this.slug = slug;
    this.createdAt = createdAt;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return version == null;
  }

  String slug() {
    return slug;
  }

  String name() {
    return name;
  }

  List<String> languages() {
    return languages;
  }

  String defaultLanguage() {
    return defaultLanguage;
  }

  Map<String, String> theme() {
    return theme;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }

  @Nullable Long version() {
    return version;
  }

  @Nullable String ineCode() {
    return ineCode;
  }

  @Nullable String dir3Code() {
    return dir3Code;
  }

  @Nullable String legalEntityName() {
    return legalEntityName;
  }

  @Nullable String eOfficeUrl() {
    return eOfficeUrl;
  }

  @Nullable String nif() {
    return nif;
  }

  @Nullable String address() {
    return address;
  }

  @Nullable String generalEmail() {
    return generalEmail;
  }

  @Nullable String dpoEmail() {
    return dpoEmail;
  }

  @Nullable String dpoPhone() {
    return dpoPhone;
  }

  @Nullable String processor() {
    return processor;
  }

  @Nullable String accessibilityEmail() {
    return accessibilityEmail;
  }

  @Nullable String accessibilityComplaintUrl() {
    return accessibilityComplaintUrl;
  }

  @Nullable String accessibilityUnit() {
    return accessibilityUnit;
  }

  String privacyMode() {
    return privacyMode;
  }

  @Nullable String privacyUrl() {
    return privacyUrl;
  }

  @Nullable Map<String, String> privacyText() {
    return privacyText;
  }

  String accessibilityMode() {
    return accessibilityMode;
  }

  @Nullable String accessibilityUrl() {
    return accessibilityUrl;
  }

  @Nullable Map<String, String> accessibilityText() {
    return accessibilityText;
  }

  String legalNoticeMode() {
    return legalNoticeMode;
  }

  @Nullable String legalNoticeUrl() {
    return legalNoticeUrl;
  }

  @Nullable Map<String, String> legalNoticeText() {
    return legalNoticeText;
  }

  @Nullable String sportsServiceName() {
    return sportsServiceName;
  }

  @Nullable String sportsAddress() {
    return sportsAddress;
  }

  @Nullable String sportsPhone() {
    return sportsPhone;
  }

  @Nullable String sportsEmail() {
    return sportsEmail;
  }

  @Nullable Map<String, String> paymentNote() {
    return paymentNote;
  }

  @Nullable Map<String, String> refundNote() {
    return refundNote;
  }

  String paymentNotesScope() {
    return paymentNotesScope;
  }

  void applySportsContact(
      @Nullable String name,
      @Nullable String address,
      @Nullable String phone,
      @Nullable String email) {
    this.sportsServiceName = name;
    this.sportsAddress = address;
    this.sportsPhone = phone;
    this.sportsEmail = email;
  }

  void applyPaymentNotes(
      String scope,
      @Nullable Map<String, String> paymentNote,
      @Nullable Map<String, String> refundNote) {
    this.paymentNotesScope = scope;
    this.paymentNote = paymentNote;
    this.refundNote = refundNote;
  }

  /** Datos legais (ADR-0017 punto 8), na orde de {@code LegalDetails}. */
  @SuppressWarnings("java:S107")
  void applyLegal(
      @Nullable String ineCode,
      @Nullable String dir3Code,
      @Nullable String legalEntityName,
      @Nullable String eOfficeUrl,
      @Nullable String nif,
      @Nullable String address,
      @Nullable String generalEmail,
      @Nullable String dpoEmail,
      @Nullable String dpoPhone,
      @Nullable String processor,
      @Nullable String accessibilityEmail,
      @Nullable String accessibilityComplaintUrl,
      @Nullable String accessibilityUnit) {
    this.ineCode = ineCode;
    this.dir3Code = dir3Code;
    this.legalEntityName = legalEntityName;
    this.eOfficeUrl = eOfficeUrl;
    this.nif = nif;
    this.address = address;
    this.generalEmail = generalEmail;
    this.dpoEmail = dpoEmail;
    this.dpoPhone = dpoPhone;
    this.processor = processor;
    this.accessibilityEmail = accessibilityEmail;
    this.accessibilityComplaintUrl = accessibilityComplaintUrl;
    this.accessibilityUnit = accessibilityUnit;
  }

  /** Modo, páxina propia e texto propio de «Privacidade». */
  void applyPrivacyPage(String mode, @Nullable String url, @Nullable Map<String, String> text) {
    this.privacyMode = mode;
    this.privacyUrl = url;
    this.privacyText = text;
  }

  /** Modo, páxina propia e texto propio de «Accesibilidade». */
  void applyAccessibilityPage(
      String mode, @Nullable String url, @Nullable Map<String, String> text) {
    this.accessibilityMode = mode;
    this.accessibilityUrl = url;
    this.accessibilityText = text;
  }

  /** Modo, páxina propia e texto propio de «Aviso legal». */
  void applyLegalNoticePage(String mode, @Nullable String url, @Nullable Map<String, String> text) {
    this.legalNoticeMode = mode;
    this.legalNoticeUrl = url;
    this.legalNoticeText = text;
  }

  void apply(
      String name,
      List<String> languages,
      String defaultLanguage,
      Map<String, String> theme,
      Instant updatedAt) {
    this.name = name;
    this.languages = languages;
    this.defaultLanguage = defaultLanguage;
    this.theme = theme;
    this.updatedAt = updatedAt;
  }
}
