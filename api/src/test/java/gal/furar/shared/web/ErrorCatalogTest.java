// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.domain.ErrorCode;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * D16 (ADR-0008): todo {@link ErrorCode} ten título e detalle en {@code messages_gl} e {@code
 * messages_es} ou nos {@code messages/<modulo>_gl/es} do seu módulo (ADR-0005 ARQ3-2); non hai
 * textos de códigos que xa non existen; os códigos seguen o formato.
 */
class ErrorCatalogTest {

  @Test
  void everyCodeIsTranslatedInBothLanguages() throws IOException {
    List<ErrorCode> codes = ErrorCatalog.scan();
    assertThat(codes).hasSizeGreaterThan(30);
    for (String language : List.of("gl", "es")) {
      Properties messages = new Properties();
      for (String basename : WebConfig.messageBasenames()) {
        messages.putAll(load(basename + "_" + language + ".properties"));
      }
      List<String> missing = new ArrayList<>();
      for (ErrorCode code : codes) {
        for (String part : List.of("title", "detail")) {
          String key = "problem." + code.code() + "." + part;
          if (messages.getProperty(key, "").isBlank()) {
            missing.add(key);
          }
        }
      }
      assertThat(missing).as("missing in messages_%s", language).isEmpty();
      Set<String> known =
          codes.stream().map(ErrorCode::code).collect(Collectors.toUnmodifiableSet());
      List<String> orphans =
          messages.stringPropertyNames().stream()
              .map(k -> k.replaceFirst("^problem\\.", "").replaceFirst("\\.(title|detail)$", ""))
              .filter(k -> !known.contains(k))
              .distinct()
              .toList();
      assertThat(orphans).as("texts without ErrorCode in messages_%s", language).isEmpty();
    }
  }

  @Test
  void codesFollowTheFormatAndHaveHttpStatus() {
    for (ErrorCode code : ErrorCatalog.scan()) {
      assertThat(code.code()).matches("^[a-z]+\\.[a-z]+(-[a-z]+)*$");
      assertThat(code.status()).isBetween(400, 599);
    }
    assertThat(new ErrorCatalog().codes()).isSorted().doesNotHaveDuplicates();
  }

  @Test
  void moduleBundlesAreDiscovered() throws IOException {
    assertThat(WebConfig.messageBasenames())
        .startsWith("messages")
        .contains("messages/customfields", "messages/municipality");
  }

  private static Properties load(String name) throws IOException {
    Properties properties = new Properties();
    try (var in = ErrorCatalogTest.class.getClassLoader().getResourceAsStream(name)) {
      assertThat(in).as(name).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
