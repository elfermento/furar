// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Ficha pública dunha instalación visible: só o que ve a veciñanza. {@code images}: as que se ven,
 * en orde (nunca baleira: se non ten, o debuxo suxerido). {@code openingHours}: hoxe e os seis días
 * seguintes. {@code closures}: peches de hoxe aos sesenta días seguintes (excepcións pechadas,
 * festivos que pechan e bloqueos), en orde. {@code houseRules}: normas da instalación. {@code
 * bookable}: activa e con reserva por internet (sen el, a ficha non leva «Reservar»). O contacto do
 * servizo e a nota de pago son do concello ({@code GET …/sports-contact} e {@code
 * …/payment-notes}). Sen estado interno, borrador, RIDEGA, campos propios nin versión.
 */
public record PublicFacilityDetailResponse(
    UUID id,
    String name,
    PublicLocationResponse address,
    @Nullable FacilityDescriptionResponse description,
    @Nullable HouseRulesResponse houseRules,
    boolean temporarilyClosed,
    boolean bookable,
    List<ImageResponse> images,
    List<PublicUseResponse> uses,
    List<AmenityValue> amenities,
    @Nullable String phone,
    @Nullable String email,
    List<PublicOpeningDayResponse> openingHours,
    List<PublicClosureResponse> closures) {}
