// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.in.web;

import gal.furar.incident.adapter.in.web.dto.IncidentOriginValue;
import gal.furar.incident.adapter.in.web.dto.IncidentResponse;
import gal.furar.incident.adapter.in.web.dto.IncidentStatusValue;
import gal.furar.incident.adapter.in.web.dto.IncidentTypeValue;
import gal.furar.incident.application.port.in.IncidentView;
import gal.furar.incident.domain.Incident;
import gal.furar.incident.domain.IncidentError;
import gal.furar.incident.domain.IncidentStatus;
import gal.furar.incident.domain.IncidentType;
import gal.furar.shared.domain.DomainException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

/** DTO ↔ comando e vista ↔ DTO. Os ficheiros chegan ao caso de uso como bytes (A11). */
final class IncidentWebMapper {

  private IncidentWebMapper() {}

  static IncidentType type(IncidentTypeValue value) {
    return IncidentType.valueOf(value.name());
  }

  static IncidentStatus status(IncidentStatusValue value) {
    return IncidentStatus.valueOf(value.name());
  }

  static List<byte[]> bytes(@Nullable List<MultipartFile> files) {
    List<byte[]> result = new ArrayList<>();
    if (files == null) {
      return result;
    }
    for (MultipartFile file : files) {
      if (file.isEmpty()) {
        continue;
      }
      try {
        result.add(file.getBytes());
      } catch (IOException e) {
        throw new DomainException(IncidentError.PHOTO_UNREADABLE);
      }
    }
    return result;
  }

  static IncidentResponse toResponse(IncidentView view) {
    Incident i = view.incident();
    IncidentView.LinkedBooking b = view.booking();
    return new IncidentResponse(
        i.id().value(),
        i.facilityId(),
        view.facilityName(),
        IncidentTypeValue.valueOf(i.type().name()),
        IncidentStatusValue.valueOf(i.status().name()),
        IncidentOriginValue.valueOf(i.origin().name()),
        i.description(),
        new IncidentResponse.IncidentReporterResponse(
            i.reporter().name(), i.reporter().email(), i.anonymizedAt() != null),
        i.internalNote(),
        i.resolutionMessage(),
        i.photos().stream()
            .map(p -> new IncidentResponse.IncidentPhotoResponse(p.id(), p.width(), p.height()))
            .toList(),
        i.photosRemovedAt() != null,
        i.createdAt(),
        i.resolvedAt(),
        b == null
            ? null
            : new IncidentResponse.IncidentBookingResponse(
                b.id(), b.code(), b.start(), b.end(), b.useName()),
        i.version() == null ? 0 : i.version());
  }
}
