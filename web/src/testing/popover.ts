// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * jsdom non ten a API popover e o CDK cae entón ao contedor global das capas. Chrome si a ten: con
 * `cdkConnectedOverlayUsePopover="inline"` a capa vai no DOM xusto despois do botón, dentro do
 * formulario ou da fila. Isto pon a API (sen efecto) para que as specs vexan ese DOM; devolve a
 * función que a quita.
 */
export function withPopoverApi(): () => void {
  const proto = HTMLElement.prototype as unknown as Record<string, unknown>;
  const had = 'showPopover' in proto;
  if (!had) {
    // jsdom non deixa pór o foco dentro dun [popover] (non sabe que se amosou): quítase o atributo.
    proto['showPopover'] = function (this: HTMLElement) {
      this.removeAttribute('popover');
    };
    proto['hidePopover'] = function () {
      // Sen efecto: a capa xa se quita do DOM ao pechar.
    };
  }
  return () => {
    if (!had) {
      delete proto['showPopover'];
      delete proto['hidePopover'];
    }
  };
}
