// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D14 (ADR-0006): desde o concello A non se ve nin se cambia nada de B, nin por repositorio ({@code
 * findById}, {@code getReferenceById}, JPQL, Criteria, {@code update}/{@code delete} en bloque,
 * paxinación) nin por HTTP (404, non 403, para non revelar que existe). Os datos dos dous concellos
 * sementanse por JDBC: se o filtro falla (entidade sen {@code @TenantId}, resolver que non filtra),
 * caen as asercións, non o {@code setUp} (scripts/probar-violacion.sh i e j).
 */
class TenantIsolationIT extends AbstractIT {

  @Autowired CustomFieldJpaRepository jpa;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate tx;

  String slugA;
  String slugB;
  TenantRef tenantA;
  UUID fieldB;

  @BeforeEach
  void setUp() {
    slugA = uniqueSlug("iso-a");
    slugB = uniqueSlug("iso-b");
    tenantA = new TenantRef(id(createMunicipality(slugA)), slugA);
    UUID municipalityB = id(createMunicipality(slugB));
    // Por JDBC, sen validación nin filtro: unha fuga ten que fallar nas asercións (D28 i).
    insertField(tenantA.id(), "shared_code", "STAFF");
    insertField(tenantA.id(), "only_a", "STAFF");
    fieldB = insertField(municipalityB, "shared_code", "STAFF");
    insertField(municipalityB, "only_b", "STAFF");
  }

  @Test
  void findByIdAndReferenceDoNotCrossTenants() {
    inA(
        () -> {
          assertThat(jpa.findById(fieldB)).isEmpty();
          assertThatThrownBy(() -> jpa.getReferenceById(fieldB).code())
              .isInstanceOf(EntityNotFoundException.class);
          assertThat(em.find(CustomFieldEntity.class, fieldB)).isNull();
        });
  }

  @Test
  void jpqlCriteriaAndPagingOnlySeeTheCurrentTenant() {
    inA(
        () -> {
          List<String> codes =
              em.createQuery("select d.code from CustomFieldEntity d", String.class)
                  .getResultList();
          assertThat(codes).containsExactlyInAnyOrder("shared_code", "only_a");

          CriteriaBuilder cb = em.getCriteriaBuilder();
          CriteriaQuery<Long> count = cb.createQuery(Long.class);
          count.select(cb.count(count.from(CustomFieldEntity.class)));
          assertThat(em.createQuery(count).getSingleResult()).isEqualTo(2L);

          var page = jpa.findAll(PageRequest.of(0, 10));
          assertThat(page.getTotalElements()).isEqualTo(2);
          assertThat(jpa.findAllByOrderByCodeAsc()).hasSize(2);
        });
  }

  @Test
  void bulkUpdateAndDeleteOnlyTouchTheCurrentTenant() {
    inA(
        () -> {
          int updated =
              em.createQuery("update CustomFieldEntity d set d.maxLength = 99").executeUpdate();
          assertThat(updated).isEqualTo(2);
          int deleted =
              em.createQuery("delete from CustomFieldUsageEntity u where u.target = 'MUNICIPALITY'")
                  .executeUpdate();
          assertThat(deleted).isEqualTo(2);
        });
    Integer bUntouched =
        jdbc.sql(
                """
                select count(*) from custom_field f
                join custom_field_usage u on u.field_id = f.id
                where f.id = :id and f.max_length is null
                """)
            .param("id", fieldB)
            .query(Integer.class)
            .single();
    assertThat(bUntouched).as("B untouched by A's bulk update/delete").isEqualTo(1);
  }

  @Test
  void httpFromAnotherMunicipalityIsNotFound() {
    Api.Response read =
        api()
            .get("/api/v1/municipalities/" + slugA + "/custom-fields/" + fieldB)
            .token(TestTokens.manager(slugA))
            .send();
    assertThat(read.status()).isEqualTo(404);
    assertThat(read.json().get("code").asString()).isEqualTo("customfield.definition-not-found");

    Api.Response archive =
        api()
            .post(
                "/api/v1/municipalities/" + slugA + "/custom-fields/" + fieldB + "/deactivate",
                null)
            .token(TestTokens.manager(slugA))
            .ifMatch(0)
            .send();
    assertThat(archive.status()).isEqualTo(404);

    // Eliminar un campo doutro concello: tampouco o atopa (e non borra nada).
    Api.Response delete =
        api()
            .delete("/api/v1/municipalities/" + slugA + "/custom-fields/" + fieldB)
            .token(TestTokens.manager(slugA))
            .ifMatch(0)
            .send();
    assertThat(delete.status()).isEqualTo(404);

    Api.Response update =
        api()
            .put(
                "/api/v1/municipalities/" + slugA + "/custom-fields/" + fieldB,
                Map.of(
                    "label",
                    Map.of("gl", "x", "es", "x"),
                    "usages",
                    List.of(Map.of("target", "MUNICIPALITY", "visibility", "PUBLIC"))))
            .token(TestTokens.manager(slugA))
            .ifMatch(0)
            .send();
    assertThat(update.status()).isEqualTo(404);
    Integer untouched =
        jdbc.sql(
                """
                select count(*) from custom_field_usage
                where field_id = :id and visibility = 'STAFF'
                """)
            .param("id", fieldB)
            .query(Integer.class)
            .single();
    assertThat(untouched).isEqualTo(1);
  }

  private void inA(Runnable work) {
    TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(status -> work.run()));
  }
}
