// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.customfields.CustomFieldValueStore;
import gal.furar.facility.application.port.out.FacilityCustomFields;
import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.JsonNode;

/**
 * Campos propios da instalación (D1-11c) con PostgreSQL real: destino {@code FACILITY} na tarxeta
 * Datos. O validador de {@code customfields} rexeita xunto ao campo; os valores gárdanse nas táboas
 * tipadas na mesma transacción da tarxeta; a ficha proxéctaos segundo quen le; gardar só campos
 * sobe a versión; e o almacén non garda valores para unha instalación doutro concello.
 */
class FacilityCustomFieldsIT extends AbstractIT {

  @Autowired FacilityCustomFields customFields;
  @Autowired CustomFieldValueStore store;
  @Autowired TransactionOperations transaction;

  String slug;
  UUID municipalityId;
  JsonNode facility;

  @BeforeEach
  void setUp() {
    slug = uniqueSlug("cfi");
    municipalityId = id(createMunicipality(slug));
    createField(slug, facilityField("web", "TEXT", "PUBLIC", false));
    createField(slug, facilityField("parking", "BOOLEAN", "PUBLIC", true));
    createField(slug, facilityField("internal_note", "TEXT", "STAFF", false));
    // Só do concello: na instalación é un campo descoñecido.
    createField(slug, textField("mayor_office", "PUBLIC"));
    Api.Response created =
        api()
            .post(
                FacilityIT.base(slug),
                Map.of("name", "Pista de atletismo", "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    facility = created.json();
    assertThat(facility.get("details").get("customFields").size()).isZero();
  }

  @Test
  void theValidatorRejectsNextToTheFieldAndNothingIsSaved() {
    Api.Response missing = putDetails(null, 0);
    assertThat(missing.status()).as(missing.toString()).isEqualTo(400);
    assertThat(missing.json().get("code").asString()).isEqualTo("validation.failed");
    assertThat(missing.json().get("errors").toString())
        .contains("\"field\":\"customFields.parking\"", "customfield.required");

    Api.Response invalid =
        putDetails(Map.of("parking", "si", "mayor_office", "x", "nonexistent", 1), 0);
    assertThat(invalid.status()).as(invalid.toString()).isEqualTo(400);
    assertThat(invalid.json().get("errors").toString())
        .contains("\"field\":\"customFields.parking\"", "customfield.invalid-type")
        .contains("\"field\":\"customFields.mayor_office\"", "customfield.unknown-field")
        .contains("\"field\":\"customFields.nonexistent\"", "customfield.unknown-field");

    assertThat(get(TestTokens.staff(slug)).etagVersion()).isZero();
    assertThat(storedRows()).isZero();
  }

  @Test
  void savesInTypedTablesAndTheRecordShowsTheValues() {
    Api.Response saved =
        putDetails(
            Map.of("web", "https://naron.gal/atletismo", "parking", true, "internal_note", "chave"),
            0);
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.etagVersion()).isEqualTo(1);
    JsonNode values = saved.json().get("details").get("customFields");
    assertThat(values.propertyNames()).containsExactlyInAnyOrder("web", "parking", "internal_note");
    assertThat(values.get("parking").asBoolean()).isTrue();

    JsonNode read = get(TestTokens.staff(slug)).json().get("details").get("customFields");
    assertThat(read.get("internal_note").asString()).isEqualTo("chave");
    assertThat(read.get("web").asString()).isEqualTo("https://naron.gal/atletismo");
    assertThat(storedRows()).isEqualTo(3);
    assertThat(
            jdbc.sql(
                    "select bool_value from custom_field_value v join custom_field f on f.id ="
                        + " v.field_id where v.target = 'FACILITY' and v.entity_id = :id and"
                        + " f.code = 'parking'")
                .param("id", id(facility))
                .query(Boolean.class)
                .single())
        .isTrue();

    // Sen un campo opcional, queda sen valor (a sección vai enteira).
    Api.Response fewer = putDetails(Map.of("parking", false), 1);
    assertThat(fewer.status()).as(fewer.toString()).isEqualTo(200);
    assertThat(fewer.json().get("details").get("customFields").propertyNames())
        .containsExactly("parking");
    assertThat(storedRows()).isEqualTo(1);
  }

  /** A ficha proxecta por visor: quen non é persoal non ve os campos {@code STAFF}. */
  @Test
  void theRecordProjectsTheValuesForWhoReads() {
    assertThat(putDetails(Map.of("parking", true, "internal_note", "chave"), 0).status())
        .isEqualTo(200);
    FacilityId id = new FacilityId(id(facility));
    TenantRef tenant = new TenantRef(municipalityId, slug);

    CustomFieldValues anonymous = TenantContext.runAs(tenant, () -> customFields.visible(id));
    assertThat(anonymous.asMap().keySet()).containsExactly("parking");

    // A xestión (xestor e persoal) ve todo.
    assertThat(
            get(TestTokens.manager(slug)).json().get("details").get("customFields").propertyNames())
        .containsExactlyInAnyOrder("parking", "internal_note");
  }

  @Test
  void savingOnlyTheCustomFieldsBumpsTheVersion() {
    assertThat(putDetails(Map.of("parking", true), 0).etagVersion()).isEqualTo(1);

    Api.Response onlyFields = putDetails(Map.of("parking", false), 1);
    assertThat(onlyFields.status()).as(onlyFields.toString()).isEqualTo(200);
    assertThat(onlyFields.etagVersion()).isEqualTo(2);
    assertThat(onlyFields.json().get("version").asLong()).isEqualTo(2);

    // Quen tiña a versión anterior xa non pode pisalos.
    Api.Response stale = putDetails(Map.of("parking", true), 1);
    assertThat(stale.status()).isEqualTo(412);
    JsonNode values = get(TestTokens.staff(slug)).json().get("details").get("customFields");
    assertThat(values.get("parking").asBoolean()).isFalse();
  }

  /**
   * {@code entity_id} non ten FK (ADR-0011): o destino comproba que a instalación é do concello.
   */
  @Test
  void theStoreDoesNotSaveValuesForAFacilityOfAnotherMunicipality() {
    String other = uniqueSlug("cfo");
    UUID otherId = id(createMunicipality(other));
    TenantRef tenant = new TenantRef(otherId, other);
    DomainException error =
        catchThrowableOfType(
            DomainException.class,
            () ->
                TenantContext.runAs(
                    tenant,
                    () ->
                        transaction.executeWithoutResult(
                            status ->
                                store.replace(
                                    CustomFieldTarget.FACILITY,
                                    id(facility),
                                    CustomFieldValues.empty()))));
    assertThat(error).isNotNull();
    assertThat(error.errorCode().code()).isEqualTo("common.not-found");
  }

  private Api.Response putDetails(Map<String, Object> values, long version) {
    Map<String, Object> body = new LinkedHashMap<>(FacilityDataIT.details(List.of()));
    if (values != null) {
      body.put("customFields", values);
    }
    return api()
        .put(FacilityIT.base(slug) + "/" + id(facility) + "/details", body)
        .ifMatch(version)
        .token(TestTokens.manager(slug))
        .send();
  }

  private Api.Response get(String token) {
    Api.Response response =
        api().get(FacilityIT.base(slug) + "/" + id(facility)).token(token).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response;
  }

  private long storedRows() {
    return jdbc.sql(
            "select count(*) from custom_field_value where target = 'FACILITY' and entity_id = :id")
        .param("id", id(facility))
        .query(Long.class)
        .single();
  }

  private static Map<String, Object> facilityField(
      String code, String type, String visibility, boolean required) {
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("code", code);
    field.put("type", type);
    field.put("label", Map.of("gl", "Etiqueta " + code, "es", "Etiqueta " + code));
    field.put(
        "usages",
        List.of(Map.of("target", "FACILITY", "visibility", visibility, "required", required)));
    return field;
  }
}
