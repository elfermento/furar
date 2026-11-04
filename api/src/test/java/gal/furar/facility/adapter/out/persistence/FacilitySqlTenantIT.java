// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link FacilitySql} só ve e bloquea instalacións do seu concello,
 * coa RLS da transacción e co concello como parámetro. Tamén a orde coa colación ICU do idioma e o
 * filtro das que se ven no público.
 */
class FacilitySqlTenantIT extends AbstractTenantIT {

  @Autowired FacilitySql subject;
  @Autowired TransactionTemplate tx;

  UUID insertFacility(UUID municipality, String name) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into facility
              (id, municipality_id, name, street, postal_code, locality, ine_code, latitude,
               longitude, created_at, updated_at, version)
            values (:id, :m, :name, 'Rúa do Alto', '15570', 'Narón', '15054', 43.5, -8.2,
                    now(), now(), 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("name", name)
        .update();
    return id;
  }

  @Override
  protected Object seed(UUID municipality) {
    return insertFacility(municipality, "Instalación " + UUID.randomUUID());
  }

  /** O que a páxina lista e o que se deixa bloquear, como ese concello. */
  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    List<UUID> all = jdbc.sql("select id from facility").query(UUID.class).list();
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                status -> {
                  List<Object> seen =
                      new ArrayList<>(subject.pageIds(municipality, Language.GL, 1000, 0));
                  for (Language language : Language.values()) {
                    for (boolean online : List.of(true, false)) {
                      subject.publishedPageIds(municipality, language, online, 1000, 0).stream()
                          .map(id -> "public:" + id)
                          .forEach(seen::add);
                    }
                  }
                  for (UUID id : all) {
                    if (subject.lock(municipality, id)) {
                      seen.add("lock:" + id);
                    }
                  }
                  return seen;
                }));
  }

  @Test
  void locksOnlyItsOwnFacilities() {
    UUID a = id(createMunicipality(uniqueSlug("lock-a")));
    UUID b = id(createMunicipality(uniqueSlug("lock-b")));
    UUID mine = insertFacility(a, "Miña");
    UUID theirs = insertFacility(b, "Súa");
    Collection<Object> seen = visibleTo(a);
    assertThat(seen).contains("lock:" + mine).doesNotContain("lock:" + theirs, theirs);
  }

  /** ADR-0022, punto 17: o filtro do público vai na base, coa mesma orde e paxinación. */
  @Test
  void publishedPagesLeaveOutDraftsWithdrawnAndOfflineInTheDatabase() {
    UUID m = id(createMunicipality(uniqueSlug("publ")));
    UUID nadal = insertFacility(m, "Nadal");
    UUID agora = insertFacility(m, "Ágora");
    UUID draft = insertFacility(m, "Borrador");
    UUID withdrawn = insertFacility(m, "Baixa");
    UUID offline = insertFacility(m, "Campo");
    jdbc.sql("update facility set published = false where id = :id").param("id", draft).update();
    jdbc.sql("update facility set status = 'WITHDRAWN' where id = :id")
        .param("id", withdrawn)
        .update();
    jdbc.sql("update facility set online_booking = false where id = :id")
        .param("id", offline)
        .update();
    for (Language language : Language.values()) {
      List<UUID> all =
          TenantContext.runAs(
              TenantRef.of(m),
              () -> tx.execute(s -> subject.publishedPageIds(m, language, false, 10, 0)));
      assertThat(all).as("%s", language).containsExactly(agora, offline, nadal);
      List<UUID> online =
          TenantContext.runAs(
              TenantRef.of(m),
              () -> tx.execute(s -> subject.publishedPageIds(m, language, true, 10, 0)));
      assertThat(online).as("%s", language).containsExactly(agora, nadal);
    }
    List<UUID> second =
        TenantContext.runAs(
            TenantRef.of(m),
            () -> tx.execute(s -> subject.publishedPageIds(m, Language.GL, false, 1, 1)));
    assertThat(second).containsExactly(offline);
  }

  @Test
  void ordersByTheCollationOfTheLanguage() {
    UUID m = id(createMunicipality(uniqueSlug("orde")));
    UUID nadal = insertFacility(m, "Nadal");
    UUID nu = insertFacility(m, "Ñu");
    UUID oasis = insertFacility(m, "Oasis");
    UUID agora = insertFacility(m, "Ágora");
    UUID zona = insertFacility(m, "zona");
    UUID campo = insertFacility(m, "Campo");
    for (Language language : Language.values()) {
      List<UUID> page =
          TenantContext.runAs(
              TenantRef.of(m), () -> tx.execute(s -> subject.pageIds(m, language, 10, 0)));
      assertThat(page).as("%s", language).containsExactly(agora, campo, nadal, nu, oasis, zona);
    }
    List<UUID> second =
        TenantContext.runAs(
            TenantRef.of(m), () -> tx.execute(s -> subject.pageIds(m, Language.ES, 2, 2)));
    assertThat(second).containsExactly(nadal, nu);
  }
}
