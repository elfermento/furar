// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.security.Actor;
import gal.furar.shared.security.NoPlatformAdmins;
import gal.furar.shared.security.NoStaffRoles;
import gal.furar.shared.security.PlatformAdmins;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.RoleGrant;
import gal.furar.shared.security.RolePermissions;
import gal.furar.shared.security.StaffRoles;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * ADR-0007, ADR-0027 e ADR-0028: o claim de roles non dá nada; os roles de persoal saen da táboa no
 * concello da petición e a administración da plataforma, da súa táboa, cada un unha vez por
 * petición.
 */
class JwtCurrentActorTest {

  private static final UUID NARON_ID = UUID.fromString("019a0b6e-0000-7000-8000-000000000001");
  private static final TenantRef NARON = new TenantRef(NARON_ID, "naron");

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  /** Táboa de proba: lembra cada chamada e responde o rol programado. */
  static final class RecordingStaffRoles implements StaffRoles {
    final List<@Nullable Instant> calls = new ArrayList<>();
    Optional<Role> role = Optional.empty();
    Runnable during = () -> {};

    @Override
    public Optional<Role> activeRole(
        String subject, TenantRef municipality, @Nullable Instant authTime) {
      calls.add(authTime);
      during.run();
      return role;
    }
  }

  /** Administración de proba: lembra cada chamada (sub e correo) e responde o programado. */
  static final class RecordingPlatformAdmins implements PlatformAdmins {
    final List<String> calls = new ArrayList<>();
    boolean admin;

    @Override
    public boolean isPlatformAdmin(String subject, @Nullable String verifiedEmail) {
      calls.add(subject + "|" + verifiedEmail);
      return admin;
    }
  }

  private static Actor actor(StaffRoles roles, Jwt jwt) {
    return actor(roles, new NoPlatformAdmins(), jwt);
  }

  private static Actor actor(StaffRoles roles, PlatformAdmins admins, Jwt jwt) {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    return new JwtCurrentActor(roles, admins).current().orElseThrow();
  }

  private static Jwt.Builder token() {
    return Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1");
  }

  static Stream<Arguments> emailVerifiedShapes() {
    Consumer<Jwt.Builder> absent = b -> {};
    return Stream.of(
        Arguments.of("absent", absent, false),
        Arguments.of("true", (Consumer<Jwt.Builder>) b -> b.claim("email_verified", true), true),
        Arguments.of("false", (Consumer<Jwt.Builder>) b -> b.claim("email_verified", false), false),
        Arguments.of(
            "string", (Consumer<Jwt.Builder>) b -> b.claim("email_verified", "true"), false));
  }

  /** ADR-0025, punto 3: só o booleano {@code true} conta como correo verificado. */
  @ParameterizedTest(name = "email_verified {0}")
  @MethodSource("emailVerifiedShapes")
  void onlyABooleanTrueVerifiesTheEmail(
      String name, Consumer<Jwt.Builder> claim, boolean verified) {
    Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("person-1");
    claim.accept(builder);
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(builder.build()));
    Actor actor =
        new JwtCurrentActor(new NoStaffRoles(), new NoPlatformAdmins()).current().orElseThrow();
    assertThat(actor.emailVerified()).isEqualTo(verified);
    assertThat(actor.grants()).isEmpty();
  }

  /** ADR-0025, punto 3: a reserva copia do token o nome, o correo e o idioma. */
  @Test
  void readsTheNameTheEmailAndTheLocaleOfThePerson() {
    Jwt full =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .subject("person-1")
            .claim("name", " Ana Rey ")
            .claim("given_name", "Ana")
            .claim("email", "ana@example.org")
            .claim("locale", "es")
            .build();
    Jwt split =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .subject("person-2")
            .claim("given_name", "Brais")
            .claim("family_name", "Lema")
            .claim("locale", 7)
            .build();
    Jwt bare = Jwt.withTokenValue("t").header("alg", "RS256").subject("person-3").build();

    assertThat(JwtCurrentActor.profile(full))
        .isEqualTo(new Actor.Profile("Ana Rey", "ana@example.org", "es"));
    assertThat(JwtCurrentActor.profile(split))
        .isEqualTo(new Actor.Profile("Brais Lema", null, null));
    assertThat(JwtCurrentActor.profile(bare)).isEqualTo(Actor.Profile.NONE);
  }

  /**
   * ADR-0027 e ADR-0028: ningún rol do claim dá nada, nin o de persoal ({@code naron:MANAGER},
   * {@code ferrol:STAFF}) nin o da plataforma ({@code *:PLATFORM_ADMIN}), que emitía o grupo de
   * Keycloak.
   */
  @Test
  void noRoleInTheClaimGivesAnything() {
    Jwt jwt =
        token()
            .claim(
                "furar_roles",
                List.of("naron:MANAGER", "ferrol:STAFF", "naron:RESIDENT", "*:PLATFORM_ADMIN"))
            .build();
    Actor actor = TenantContext.runAs(NARON, () -> actor(new NoStaffRoles(), jwt));
    assertThat(actor.grants()).isEmpty();
    assertThat(actor.hasRoleIn("naron")).isFalse();
    for (Role role : List.of(Role.MANAGER, Role.STAFF, Role.PLATFORM_ADMIN)) {
      assertThat(RolePermissions.of(role))
          .allSatisfy(p -> assertThat(actor.can(p, "naron")).as(p.code()).isFalse());
    }
  }

  /**
   * ADR-0028: a administración sae da súa táboa, tamén sen concello, unha vez por petición; o
   * correo só se pasa se está verificado (o primeiro acceso depende del).
   */
  @Test
  void thePlatformRoleComesFromItsTableOncePerRequest() {
    RecordingPlatformAdmins admins = new RecordingPlatformAdmins();
    admins.admin = true;
    Jwt verified =
        token().claim("email", "admin@example.org").claim("email_verified", true).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(verified));
    JwtCurrentActor current = new JwtCurrentActor(new NoStaffRoles(), admins);
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));

    assertThat(current.current().orElseThrow().grants())
        .containsExactly(RoleGrant.parse("*:PLATFORM_ADMIN").orElseThrow());
    assertThat(TenantContext.runAs(NARON, () -> current.current().orElseThrow().grants()))
        .containsExactly(RoleGrant.parse("*:PLATFORM_ADMIN").orElseThrow());
    assertThat(admins.calls).containsExactly("user-1|admin@example.org");

    admins.admin = false;
    Jwt unverified = token().claim("email", "admin@example.org").build();
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    assertThat(actor(new NoStaffRoles(), admins, unverified).grants()).isEmpty();
    assertThat(admins.calls).containsExactly("user-1|admin@example.org", "user-1|null");
  }

  /**
   * O rol da táboa no concello do contexto súmase e lévase o {@code auth_time} para o último
   * acceso; fóra de concello non se le a táboa.
   */
  @Test
  void theTableRoleCountsInTheMunicipalityOfTheRequest() {
    RecordingStaffRoles roles = new RecordingStaffRoles();
    roles.role = Optional.of(Role.MANAGER);
    Jwt jwt = token().claim("auth_time", 1_800_000_000L).build();

    assertThat(actor(roles, jwt).grants()).isEmpty();
    assertThat(roles.calls).isEmpty();

    Actor actor = TenantContext.runAs(NARON, () -> actor(roles, jwt));
    assertThat(actor.grants()).containsExactly(RoleGrant.parse("naron:MANAGER").orElseThrow());
    assertThat(roles.calls).containsExactly(Instant.ofEpochSecond(1_800_000_000L));
  }

  /** Unha lectura por petición e concello; outra petición volve ler (sen caché entre peticións). */
  @Test
  void readsTheTableOncePerRequest() {
    RecordingStaffRoles roles = new RecordingStaffRoles();
    roles.role = Optional.of(Role.MANAGER);
    Jwt jwt = token().build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    JwtCurrentActor current = new JwtCurrentActor(roles, new NoPlatformAdmins());

    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    TenantContext.runAs(
        NARON,
        () -> {
          current.current();
          current.current();
        });
    assertThat(roles.calls).hasSize(1);

    roles.role = Optional.empty();
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    Actor next = TenantContext.runAs(NARON, () -> current.current().orElseThrow());
    assertThat(next.grants()).isEmpty();
    assertThat(roles.calls).hasSize(2);
  }

  /**
   * O rexistro de actividade de {@code staff.sign-in} pregunta quen é a persoa mentres se le a
   * táboa: responde co token, sen volver ler.
   */
  @Test
  void askingWhileReadingTheTableDoesNotLoop() {
    RecordingStaffRoles roles = new RecordingStaffRoles();
    roles.role = Optional.of(Role.MANAGER);
    Jwt jwt = token().build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    JwtCurrentActor current = new JwtCurrentActor(roles, new NoPlatformAdmins());
    List<Actor> inner = new ArrayList<>();
    roles.during = () -> inner.add(current.current().orElseThrow());

    Actor outer = TenantContext.runAs(NARON, () -> current.current().orElseThrow());

    assertThat(roles.calls).hasSize(1);
    assertThat(inner).singleElement().satisfies(a -> assertThat(a.subject()).isEqualTo("user-1"));
    assertThat(outer.grants()).containsExactly(RoleGrant.parse("naron:MANAGER").orElseThrow());
  }
}
