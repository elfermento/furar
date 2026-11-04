// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * A matriz rol → permisos (ADR-0007), montada cos {@code enum} {@link PermissionKey} de cada módulo
 * (ADR-0005 ARQ3-2): un módulo novo trae os seus permisos e a súa fila de roles sen tocar este
 * ficheiro. {@code PLATFORM_ADMIN} só ten os que o listan de forma explícita (mínimo privilexio:
 * non ve reservas nin datos da veciñanza). Dous permisos co mesmo código impiden arrincar. {@code
 * RolePermissionsTest} xera os seus casos desde aquí.
 */
public final class RolePermissions {

  private static final String BASE_PACKAGE = "gal.furar";

  private static final Map<String, PermissionKey> BY_CODE = scan();
  private static final Map<Role, Set<PermissionKey>> MATRIX = matrix(BY_CODE.values());

  private RolePermissions() {}

  /** Todos os permisos rexistrados, por código. */
  public static List<PermissionKey> all() {
    return List.copyOf(BY_CODE.values());
  }

  public static Optional<PermissionKey> byCode(String code) {
    return Optional.ofNullable(BY_CODE.get(code));
  }

  public static Set<PermissionKey> of(Role role) {
    return Set.copyOf(MATRIX.get(role));
  }

  public static boolean allows(Role role, PermissionKey permission) {
    return MATRIX.get(role).contains(permission);
  }

  /** Os roles que só teñen sentido globais ({@code *:ROL}). */
  public static boolean isGlobal(Role role) {
    return role == Role.PLATFORM_ADMIN;
  }

  private static Map<Role, Set<PermissionKey>> matrix(Iterable<PermissionKey> permissions) {
    Map<Role, Set<PermissionKey>> matrix = new EnumMap<>(Role.class);
    for (Role role : Role.values()) {
      matrix.put(role, new LinkedHashSet<>());
    }
    for (PermissionKey p : permissions) {
      for (Role role : p.roles()) {
        matrix.get(role).add(p);
      }
    }
    matrix.replaceAll((role, set) -> Set.copyOf(set));
    return matrix;
  }

  /** Busca os {@code enum} que implementan {@link PermissionKey} en {@code gal.furar}. */
  private static Map<String, PermissionKey> scan() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(PermissionKey.class));
    List<PermissionKey> found = new ArrayList<>();
    for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
      Class<?> type =
          ClassUtils.resolveClassName(
              candidate.getBeanClassName(), RolePermissions.class.getClassLoader());
      if (!type.isEnum()) {
        throw new IllegalStateException(type.getName() + ": a PermissionKey must be an enum");
      }
      for (Object constant : type.getEnumConstants()) {
        found.add((PermissionKey) constant);
      }
    }
    found.sort(Comparator.comparing(PermissionKey::code));
    Map<String, PermissionKey> byCode = new LinkedHashMap<>();
    for (PermissionKey p : found) {
      PermissionKey previous = byCode.put(p.code(), p);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicated permission code " + p.code() + " in " + previous + " and " + p);
      }
    }
    return byCode;
  }
}
