// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Persoal (docs/glosario.md; ADR-0027): a pertenza de cada persoa ao persoal dun concello (correo,
 * rol MANAGER ou STAFF, estado INVITED, ACTIVE ou DEACTIVATED), a invitación propia de Furar (token
 * dun só uso que caduca aos 7 días, do que só se garda o hash) e os seus límites de envío. Os roles
 * do persoal saen desta táboa en cada petición: o módulo implementa a SPI {@link
 * gal.furar.shared.security.StaffRoles}, que le o rol e rexistra o último acceso. SQL nativo con
 * RLS, sen JPA, como {@code booking}: a pertenza non entra en {@code furar-export} (ADR-0018).
 * Envía os correos coa API pública de {@code notification}. Separa a conta de traballo da persoal
 * (ADR-0027, revisión do 2026-10-30): pregunta a {@code booking.BookingQueries} se quen acepta ou
 * se reactiva xa reservou como veciña, e implementa a SPI {@code booking.StaffAccounts} para que
 * unha conta de traballo non reserve ({@code booking} non depende de {@code staff}: {@code staff →
 * notification → booking} faría un ciclo). Le o rexistro de actividade do concello e da plataforma
 * ({@code shared.audit.AuditTrail}) e ponlle nome a cada persoa. Dá á persoa da sesión os concellos
 * onde é persoal activo, co nome que lle pregunta a {@code municipality.MunicipalityQueries}, para
 * cambiar de concello desde o seu menú. API pública: {@link gal.furar.staff.StaffPermission} e
 * {@link gal.furar.staff.StaffQueries}.
 */
@ApplicationModule(
    displayName = "Staff",
    allowedDependencies = {"shared", "notification", "booking", "municipality"})
package gal.furar.staff;

import org.springframework.modulith.ApplicationModule;
