// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { CdkDialogContainer } from '@angular/cdk/dialog';
import { CdkPortalOutlet } from '@angular/cdk/portal';
import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Contedor das capas de diálogo: o mesmo do CDK (trampa e retorno do foco, `role`, `aria-modal`,
 * `aria-labelledby`) **sen os seus estilos**. O `cdk-dialog-container` do CDK trae `styles`, que
 * Angular mete nun `<style>` en liña e a CSP `style-src 'self'` bloquea (overlay/cdk-styles.ts);
 * herdando del quedan os enlaces do host e o aspecto vai en styles/components/dialog.css.
 */
@Component({
  selector: 'furar-dialog-container',
  imports: [CdkPortalOutlet],
  // O contido (furar-dialog) xa é OnPush; o contedor só pinta a ranura e os atributos do host.
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-dialog-container' },
  template: '<ng-template cdkPortalOutlet />',
})
export class DialogContainerComponent extends CdkDialogContainer {}
