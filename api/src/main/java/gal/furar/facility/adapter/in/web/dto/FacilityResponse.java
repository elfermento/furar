// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Ficha de xestión. {@code missing}: o que falta para deixar de estar en borrador ({@code SPORTS},
 * {@code USES}, {@code SCHEDULE}); baleiro = publicable. {@code amenities} na orde do catálogo.
 * {@code images}: as que se ven, na orde da galería (a primeira é a portada); se non ten ningunha
 * gardada, só o debuxo suxerido, que vai tamén en {@code suggestedImage} (o que volve se se quitan
 * todas). {@code deletable}: nunca tivo reservas, así que se pode eliminar (se non, só dar de
 * baixa). {@code paymentNotes}: pago e devolución desta instalación (só se usan co ámbito «Distinto
 * por instalación» do concello). {@code address}: nulo nun borrador que aínda non o ten. {@code
 * published}: xa se publicou co «Publicar» do asistente (as anteriores ao asistente, todas); sen
 * publicar, a veciñanza non a ve aínda que non lle falte nada. As seccións seguintes (usos…)
 * engádense aquí.
 */
public record FacilityResponse(
    UUID id,
    String name,
    FacilityStatusValue status,
    @Nullable AddressResponse address,
    boolean published,
    FacilityDetailsResponse details,
    @Nullable FacilityDescriptionResponse description,
    @Nullable HouseRulesResponse houseRules,
    FacilityPaymentNotesResponse paymentNotes,
    List<AmenityValue> amenities,
    List<ImageResponse> images,
    @Nullable ImageResponse suggestedImage,
    List<RequirementValue> missing,
    boolean deletable,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
