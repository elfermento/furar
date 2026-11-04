// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import gal.furar.FurarApplication;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

/**
 * Base de todos os IT: un contexto (cacheado), PostgreSQL 18.6 real con Flyway, tokens de proba e
 * API no porto aleatorio (ADR-0013). Cada test crea os seus concellos con slug único.
 */
@SpringBootTest(
    classes = FurarApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestJwtConfig.class, TestSupportConfig.class})
public abstract class AbstractIT {

  protected static final PostgreSQLContainer POSTGRES = PostgresContainers.shared();

  /**
   * Como en produción (SEC-02): a API conecta como {@code furar_app} (só DML) e Flyway migra como
   * {@code furar_owner}. Sen {@code @ServiceConnection}, que usaría o superusuario do contedor.
   */
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainers::appUser);
    registry.add("spring.datasource.password", () -> PostgresContainers.APP_PASSWORD);
    registry.add("spring.flyway.user", () -> PostgresContainers.OWNER_USER);
    registry.add("spring.flyway.password", () -> PostgresContainers.OWNER_PASSWORD);
  }

  @LocalServerPort protected int port;

  /**
   * Acceso directo como {@code furar_owner}, dono das táboas: sen RLS nin filtro de concello. Para
   * sementar e comprobar datos de varios concellos (ADR-0006). O rol da API é {@link #appJdbc}.
   */
  protected final JdbcClient jdbc = OWNER_JDBC;

  /** O {@code JdbcClient} da aplicación, co rol da API ({@code furar_app}). */
  @Autowired protected JdbcClient appJdbc;

  private static final JdbcClient OWNER_JDBC =
      JdbcClient.create(
          new org.springframework.jdbc.datasource.SimpleDriverDataSource(
              new org.postgresql.Driver(),
              POSTGRES.getJdbcUrl(),
              PostgresContainers.OWNER_USER,
              PostgresContainers.OWNER_PASSWORD));

  /** Se o rol da API respecta a RLS (falso só co rol de proba {@code furar_app_bypass}). */
  protected boolean rowLevelSecurityEnforced() {
    return !appJdbc
        .sql("select rolbypassrls from pg_roles where rolname = current_user")
        .query(Boolean.class)
        .single();
  }

  protected Api api() {
    return new Api(port);
  }

  /** Slug único para que os tests non se pisen. */
  protected static String uniqueSlug(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /** Crea un concello pola API, como administración da plataforma. */
  protected JsonNode createMunicipality(String slug) {
    Api.Response response =
        api()
            .post(
                "/api/v1/municipalities",
                Map.of(
                    "slug",
                    slug,
                    "name",
                    "Concello " + slug,
                    "languages",
                    java.util.List.of("gl", "es"),
                    "defaultLanguage",
                    "gl"))
            .token(TestTokens.platformAdmin())
            .send();
    if (response.status() != 201) {
      throw new IllegalStateException("Cannot create municipality: " + response);
    }
    return response.json();
  }

  /** Define un campo personalizado como xestor do concello. */
  protected JsonNode createField(String slug, Map<String, Object> definition) {
    Api.Response response =
        api()
            .post("/api/v1/municipalities/" + slug + "/custom-fields", definition)
            .token(TestTokens.manager(slug))
            .send();
    if (response.status() != 201) {
      throw new IllegalStateException("Cannot create field: " + response);
    }
    return response.json();
  }

  /**
   * Define un campo de texto directamente na base, sen pasar pola API nin polo filtro de concello.
   * Os IT de illamento sementan así os datos (ADR-0006): se o filtro falla, o test ten que caer nas
   * asercións de lectura e escritura, non antes nun 409 de unicidade ou nun 500 ao crear.
   */
  protected UUID insertField(UUID municipalityId, String code, String visibility) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into custom_field
              (id, municipality_id, code, type, label, personal_data, status, created_at,
               updated_at, version)
            values (:id, :municipality, :code, 'TEXT', jsonb_build_object('gl', :code, 'es', :code),
                    false, 'ACTIVE', now(), now(), 0)
            """)
        .param("id", id)
        .param("municipality", municipalityId)
        .param("code", code)
        .update();
    jdbc.sql(
            """
            insert into custom_field_usage
              (id, municipality_id, field_id, target, required, visibility, personal_data)
            values (:usage, :municipality, :id, 'MUNICIPALITY', false, :visibility, false)
            """)
        .param("usage", UUID.randomUUID())
        .param("municipality", municipalityId)
        .param("id", id)
        .param("visibility", visibility)
        .update();
    return id;
  }

  /**
   * Definición mínima dun campo para os tests, usada no concello. {@code required} en {@code extra}
   * vai ao uso do concello.
   */
  protected static Map<String, Object> field(
      String code, String type, String visibility, Map<String, Object> extra) {
    var map = new java.util.LinkedHashMap<String, Object>();
    map.put("code", code);
    map.put("type", type);
    map.put("label", Map.of("gl", "Etiqueta " + code, "es", "Etiqueta " + code));
    var rest = new java.util.LinkedHashMap<String, Object>(extra);
    Object required = rest.remove("required");
    map.put(
        "usages",
        java.util.List.of(
            Map.of(
                "target",
                "MUNICIPALITY",
                "visibility",
                visibility,
                "required",
                Boolean.TRUE.equals(required))));
    map.putAll(rest);
    return map;
  }

  protected static Map<String, Object> textField(String code, String visibility) {
    return field(code, "TEXT", visibility, Map.of());
  }

  /** Corpo de edición do concello cos valores de campos personalizados indicados. */
  protected static Map<String, Object> updateBody(String name, Map<String, Object> customFields) {
    return Map.of(
        "name",
        name,
        "languages",
        java.util.List.of("gl", "es"),
        "defaultLanguage",
        "gl",
        "theme",
        Map.of(
            "primary", "#0b4f8a",
            "onPrimary", "#ffffff",
            "accent", "#b3541e",
            "background", "#ffffff",
            "text", "#1a1a1a"),
        "customFields",
        customFields);
  }

  protected static UUID id(JsonNode node) {
    return UUID.fromString(node.get("id").asString());
  }
}
