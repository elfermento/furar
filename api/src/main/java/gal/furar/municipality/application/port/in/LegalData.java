// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import org.jspecify.annotations.Nullable;

/**
 * Datos legais tal como chegan (alta, edición e importación); ver {@code LegalDetails}. Os modos
 * chegan polo nome ({@code FURAR}, {@code TEXT}, {@code URL}); sen modo, {@code FURAR}.
 *
 * @param dpoContact só ficheiros {@code furar-export} anteriores ao 2026-10-29: o DPD en texto
 *     libre, do que se tiran o correo e o teléfono se non veñen {@code dpoEmail} nin {@code
 *     dpoPhone}
 */
@SuppressWarnings("java:S107")
public record LegalData(
    @Nullable String ineCode,
    @Nullable String dir3Code,
    @Nullable String legalEntityName,
    @Nullable String eOfficeUrl,
    @Nullable String nif,
    @Nullable String address,
    @Nullable String generalEmail,
    @Nullable String dpoEmail,
    @Nullable String dpoPhone,
    @Nullable String processor,
    @Nullable String accessibilityEmail,
    @Nullable String accessibilityComplaintUrl,
    @Nullable String accessibilityUnit,
    @Nullable String privacyMode,
    @Nullable String privacyUrl,
    @Nullable String accessibilityMode,
    @Nullable String accessibilityUrl,
    @Nullable String legalNoticeMode,
    @Nullable String legalNoticeUrl,
    @Nullable String dpoContact) {}
