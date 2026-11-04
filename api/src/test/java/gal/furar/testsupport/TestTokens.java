// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.RoleGrant;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tokens de proba asinados coa clave de {@link TestJwtConfig}. Os roles pídense como texto ({@code
 * naron:MANAGER}, {@code *:PLATFORM_ADMIN}) pero, como en Keycloak, ningún dá nada desde o token:
 * un rol de persoal dáse como pertenza ACTIVE na táboa para o {@code sub} do token (ADR-0027) e a
 * administración da plataforma, como fila de {@code platform_admin} (ADR-0028), ambos con {@link
 * StaffMemberships}. No claim {@code furar_roles} só queda o de veciño, que non dá nada. O concello
 * ten que existir antes de crear un token de persoal.
 */
public final class TestTokens {

  public static final String ISSUER = "http://localhost:8080/auth/realms/furar";
  public static final String AUDIENCE = "furar-api";

  /** O cliente da web ({@code azp}): o único do que a API acepta tokens fóra da demo (R5). */
  public static final String CLIENT = "furar-web";

  private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.MANAGER, Role.STAFF);

  private TestTokens() {}

  public static String manager(String slug) {
    return withRoles(slug + ":MANAGER");
  }

  public static String staff(String slug) {
    return withRoles(slug + ":STAFF");
  }

  public static String resident(String slug) {
    return withRoles(slug + ":RESIDENT");
  }

  public static String platformAdmin() {
    return withRoles("*:PLATFORM_ADMIN");
  }

  /**
   * Fai do {@code sub} administración da plataforma (fila de {@code platform_admin}, ADR-0028),
   * para os tokens que se arman á man ({@link #withoutRolesClaim}).
   */
  public static void grantPlatformAdmin(String subject) {
    StaffMemberships.platformAdmin(subject);
  }

  /**
   * Token cos roles indicados: os de persoal e o da plataforma pasan ás táboas ({@link
   * #staffToTable}).
   */
  public static String withRoles(String... roles) {
    return sign(
        staffToTable(claims(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300), roles)));
  }

  /**
   * Token con todos os roles no claim, tamén os de persoal ({@code naron:MANAGER}) e o da
   * plataforma ({@code *:PLATFORM_ADMIN}), e sen ningunha fila nas táboas: ningún rol do claim debe
   * dar nada (ADR-0027, ADR-0028).
   */
  public static String withRolesClaim(String... roles) {
    return sign(claims(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300), roles));
  }

  /**
   * Token cos roles indicados e os claims de {@code extra} por riba dos de sempre (por exemplo,
   * {@code azp} doutro cliente).
   */
  public static String withClaims(Map<String, ?> extra, String... roles) {
    JWTClaimsSet.Builder builder =
        new JWTClaimsSet.Builder(
            claims(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300), roles));
    extra.forEach(builder::claim);
    return sign(staffToTable(builder.build()));
  }

  /**
   * Persoa con conta e sen roles (ADR-0025, punto 3), co nome, o correo e o idioma que copia a
   * reserva. {@code emailVerified} falso: Keycloak aínda non verificou o correo.
   */
  public static String person(String subject, boolean emailVerified) {
    return person(subject, emailVerified, "gl");
  }

  /** Como {@link #person(String, boolean)}, co idioma de Keycloak ({@code locale}). */
  public static String person(String subject, boolean emailVerified, String locale) {
    return sign(
        new JWTClaimsSet.Builder(claims(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300)))
            .subject(subject)
            .claim("email_verified", emailVerified)
            .claim("email", subject + "@example.org")
            .claim("name", "Persoa " + subject)
            .claim("locale", locale)
            .build());
  }

  /**
   * Token do persoal sen o claim de roles (ADR-0027): o rol sae da táboa de pertenzas. Leva {@code
   * sub}, o correo verificado e os claims indicados (por exemplo {@code auth_time}).
   */
  public static String withoutRolesClaim(String subject, Map<String, ?> claims) {
    JWTClaimsSet.Builder builder =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(List.of(AUDIENCE))
            .subject(subject)
            .claim("azp", CLIENT)
            .issueTime(java.util.Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(java.util.Date.from(Instant.now().plusSeconds(300)))
            .claim("email_verified", true)
            .claim("email", subject + "@example.org");
    claims.forEach(builder::claim);
    return sign(builder.build());
  }

  /** Token válido en todo salvo o cliente: Keycloak emitiuno para outro cliente do realm. */
  public static String otherClient(String client) {
    return withClaims(Map.of("azp", client), "naron:MANAGER");
  }

  /** Token sen {@code azp}. */
  public static String withoutClient() {
    JWTClaimsSet all =
        claims(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300), "naron:MANAGER");
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
    all.getClaims().entrySet().stream()
        .filter(e -> !"azp".equals(e.getKey()))
        .forEach(e -> builder.claim(e.getKey(), e.getValue()));
    return sign(builder.build());
  }

  public static String expired() {
    return sign(
        claims(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(600), "naron:MANAGER"));
  }

  public static String wrongIssuer() {
    return sign(
        claims(
            "https://evil.example/auth/realms/furar",
            List.of(AUDIENCE),
            Instant.now().plusSeconds(300),
            "naron:MANAGER"));
  }

  public static String wrongAudience() {
    return sign(
        claims(ISSUER, List.of("account"), Instant.now().plusSeconds(300), "naron:MANAGER"));
  }

  /** Asinado cunha clave que a API non coñece. */
  public static String foreignSignature() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return sign(
          claims(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300), "naron:MANAGER"),
          generator.generateKeyPair().getPrivate());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static JWTClaimsSet claims(
      String issuer, List<String> audience, Instant expiresAt, String... roles) {
    return new JWTClaimsSet.Builder()
        .issuer(issuer)
        .audience(audience)
        .subject("test-" + UUID.randomUUID())
        .issueTime(java.util.Date.from(expiresAt.minusSeconds(600)))
        .expirationTime(java.util.Date.from(expiresAt))
        .claim("furar_roles", List.of(roles))
        .claim("azp", CLIENT)
        .build();
  }

  /**
   * Pasa os roles de persoal e o da plataforma do claim ás táboas: pertenza ACTIVE ou fila de
   * {@code platform_admin} para o {@code sub} do token. No claim queda o de veciño.
   */
  private static JWTClaimsSet staffToTable(JWTClaimsSet claims) {
    List<String> kept = new ArrayList<>();
    for (String role : rolesClaim(claims)) {
      Optional<RoleGrant> grant = RoleGrant.parse(role);
      if (grant.isPresent() && STAFF_ROLES.contains(grant.get().role())) {
        StaffMemberships.activate(claims.getSubject(), grant.get());
      } else if (grant.isPresent() && grant.get().role() == Role.PLATFORM_ADMIN) {
        StaffMemberships.platformAdmin(claims.getSubject());
      } else {
        kept.add(role);
      }
    }
    return new JWTClaimsSet.Builder(claims).claim("furar_roles", kept).build();
  }

  private static List<String> rolesClaim(JWTClaimsSet claims) {
    try {
      List<String> roles = claims.getStringListClaim("furar_roles");
      return roles == null ? List.of() : roles;
    } catch (ParseException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String sign(JWTClaimsSet claims) {
    return sign(claims, TestJwtConfig.KEYS.getPrivate());
  }

  private static String sign(JWTClaimsSet claims, PrivateKey key) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
