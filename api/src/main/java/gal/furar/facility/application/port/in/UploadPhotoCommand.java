// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

/**
 * Subir unha foto á biblioteca do concello: os bytes tal como chegan (aínda sen comprobar). Queda
 * pendente ata que o «Gardar» dunha instalación a usa. Non se copia o array: o adaptador web non o
 * volve tocar.
 */
public record UploadPhotoCommand(byte[] content) {}
