// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.municipality.MunicipalityPermission;
import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.Audiences;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.RoleGrant;
import gal.furar.shared.security.RolePermissions;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * D18: o fluxo «o xestor define un campo → valídase → gárdase → vese» e a visibilidade: o mesmo
 * concello lido como anónimo ou conta sen rol e como persoal devolve PUBLIC e todo; a audiencia
 * OWNER, coa API do módulo.
 *
 * <p>Escritura: quen escribe só pode escribir os campos que ve ({@code unknown-field}). Hoxe ningún
 * rol que edita o concello deixa de ver os campos {@code STAFF}, así que por HTTP non hai camiño
 * para ese caso; probámolo coa API pública real do módulo ({@link CustomFieldValidator}), coas
 * definicións en PostgreSQL e o concello no contexto, e un test garda que esa premisa siga sendo
 * certa. O primeiro módulo onde escriba alguén sen {@code STAFF_DATA_VIEW} (formulario de reserva)
 * proba o 400 por HTTP (docs/como-engadir-un-modulo.md).
 */
class CustomFieldsVisibilityIT extends AbstractIT {

  @Autowired CustomFieldValidator validator;
  @Autowired CustomFieldValueStore store;

  String slug;
  UUID municipalityId;

  @BeforeEach
  void setUp() {
    slug = uniqueSlug("vis");
    municipalityId = id(createMunicipality(slug));
    createField(slug, textField("web", "PUBLIC"));
    createField(slug, textField("contact_phone", "OWNER"));
    createField(slug, textField("internal_note", "STAFF"));
    Api.Response saved =
        api()
            .put(
                "/api/v1/municipalities/" + slug,
                updateBody(
                    "Visible",
                    Map.of(
                        "web", "https://example.gal",
                        "contact_phone", "981000000",
                        "internal_note", "só persoal")))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
  }

  @Test
  void anonymousSeesOnlyPublic() {
    JsonNode fields = read(null);
    assertThat(fields.propertyNames()).containsExactly("web");
  }

  /**
   * O claim {@code slug:RESIDENT} xa non se le (ADR-0028, revisión do 2026-11-03): non fai titular
   * do concello. A audiencia OWNER próbase coa API do módulo ({@link
   * #writerKeepsTheValuesItDoesNotSee}).
   */
  @Test
  void aResidentClaimDoesNotMakeAnOwner() {
    JsonNode fields = read(TestTokens.resident(slug));
    assertThat(fields.propertyNames()).containsExactly("web");
  }

  /** SEC-03: titular do concello = ter algún rol nel; unha conta rexistrada calquera non o é. */
  @Test
  void signedInWithoutARoleInThisMunicipalitySeesOnlyPublic() {
    assertThat(read(TestTokens.withRoles()).propertyNames()).containsExactly("web");
    assertThat(read(TestTokens.resident(uniqueSlug("outro"))).propertyNames())
        .containsExactly("web");
  }

  @Test
  void staffSeesEverything() {
    JsonNode fields = read(TestTokens.staff(slug));
    assertThat(fields.propertyNames())
        .containsExactlyInAnyOrder("web", "contact_phone", "internal_note");
    assertThat(fields.get("internal_note").asString()).isEqualTo("só persoal");
  }

  @Test
  void definitionsListFollowsVisibilityToo() {
    Api.Response anonymous =
        api().get("/api/v1/municipalities/" + slug + "/custom-fields?target=MUNICIPALITY").send();
    assertThat(anonymous.status()).isEqualTo(200);
    assertThat(anonymous.json().size()).isEqualTo(1);

    Api.Response staff =
        api()
            .get("/api/v1/municipalities/" + slug + "/custom-fields?target=MUNICIPALITY")
            .token(TestTokens.staff(slug))
            .send();
    assertThat(staff.json().size()).isEqualTo(3);

    Api.Response schema =
        api().get("/api/v1/municipalities/" + slug + "/custom-fields/MUNICIPALITY/schema").send();
    assertThat(schema.status()).isEqualTo(200);
    assertThat(schema.json().get("properties").propertyNames()).containsExactly("web");
  }

  @Test
  void invalidValueIsRejectedNextToTheField() {
    Api.Response response =
        api()
            .put(
                "/api/v1/municipalities/" + slug,
                updateBody("Visible", Map.of("web", 42, "unknown", "x")))
            .token(TestTokens.manager(slug))
            .ifMatch(1)
            .language("es")
            .send();
    assertThat(response.status()).isEqualTo(400);
    JsonNode body = response.json();
    assertThat(body.get("code").asString()).isEqualTo("validation.failed");
    assertThat(body.get("title").asString()).isEqualTo("Hay datos que revisar");
    assertThat(body.get("errors").toString())
        .contains("\"field\":\"customFields.web\"", "customfield.invalid-type")
        .contains("\"field\":\"customFields.unknown\"", "customfield.unknown-field");
  }

  @ParameterizedTest
  @EnumSource(
      value = Audience.class,
      names = {"PUBLIC", "OWNER"})
  void writerCannotWriteFieldsItDoesNotSee(Audience writer) {
    DomainException error =
        catchThrowableOfType(
            DomainException.class,
            () -> validateAs(writer, Map.of("web", "https://outra.gal", "internal_note", "x")));
    assertThat(error).as("writing a STAFF field as " + writer).isNotNull();
    assertThat(error.errorCode().code()).isEqualTo("validation.failed");
    assertThat(error.violations())
        .extracting(FieldViolation::field, FieldViolation::code)
        .contains(
            org.assertj.core.groups.Tuple.tuple(
                "customFields.internal_note", "customfield.unknown-field"));
  }

  @Test
  void writerKeepsTheValuesItDoesNotSee() {
    CustomFieldValues result =
        validateAs(Audience.OWNER, Map.of("web", "https://outra.gal", "contact_phone", "1"));
    assertThat(result.get("internal_note")).isPresent();
    assertThat(result.get("internal_note").get().toJson()).isEqualTo("só persoal");
  }

  /**
   * Premisa da cabeceira: se un rol edita o concello sen ver STAFF, falta o test por HTTP. A
   * plataforma ve STAFF na configuración do concello sen {@code STAFF_DATA_VIEW} ({@link
   * Audiences#forConfiguration}, ADR-0028).
   */
  @Test
  void everyRoleThatEditsAMunicipalityAlsoSeesStaffFields() {
    assertThat(Arrays.stream(Role.values()))
        .filteredOn(role -> RolePermissions.allows(role, MunicipalityPermission.UPDATE))
        .isNotEmpty()
        .allMatch(
            role ->
                Audiences.forConfiguration(
                        Optional.of(
                            new Actor(
                                "premise",
                                Set.of(
                                    new RoleGrant(
                                        RolePermissions.isGlobal(role) ? RoleGrant.ANY : slug,
                                        role)))),
                        slug)
                    == Audience.STAFF);
  }

  private CustomFieldValues validateAs(Audience writer, Map<String, Object> raw) {
    TenantRef tenant = new TenantRef(municipalityId, slug);
    return TenantContext.runAs(
        tenant,
        () -> {
          CustomFieldValues previous = store.load(CustomFieldTarget.MUNICIPALITY, municipalityId);
          return validator.validate(
              CustomFieldTarget.MUNICIPALITY, raw, previous, ValidationContext.of(writer));
        });
  }

  private JsonNode read(String token) {
    Api.Call call = api().get("/api/v1/municipalities/" + slug);
    if (token != null) {
      call.token(token);
    }
    Api.Response response = call.send();
    assertThat(response.status()).isEqualTo(200);
    return response.json().get("customFields");
  }
}
