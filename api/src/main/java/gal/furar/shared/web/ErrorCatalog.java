// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.domain.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Todos os {@link ErrorCode} de Furar ({@code enum} que o implementan baixo {@code gal.furar}).
 * Publícanse como {@code enum} no contrato (ADR-0008) e compróbase que non se repiten.
 */
@Component
public class ErrorCatalog {

  public static final String BASE_PACKAGE = "gal.furar";

  private final List<ErrorCode> codes;

  public ErrorCatalog() {
    this.codes = scan();
  }

  public List<ErrorCode> all() {
    return codes;
  }

  public List<String> codes() {
    return codes.stream().map(ErrorCode::code).toList();
  }

  /** Busca os {@code enum} que implementan {@link ErrorCode} e devólveos ordenados por código. */
  public static List<ErrorCode> scan() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
    List<ErrorCode> found = new ArrayList<>();
    for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
      Class<?> type =
          ClassUtils.resolveClassName(
              candidate.getBeanClassName(), ErrorCatalog.class.getClassLoader());
      if (type.isEnum()) {
        for (Object constant : type.getEnumConstants()) {
          found.add((ErrorCode) constant);
        }
      }
    }
    Map<String, ErrorCode> byCode = new HashMap<>();
    for (ErrorCode code : found) {
      ErrorCode previous = byCode.put(code.code(), code);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicated error code " + code.code() + " in " + previous + " and " + code);
      }
    }
    found.sort(Comparator.comparing(ErrorCode::code));
    return List.copyOf(found);
  }
}
