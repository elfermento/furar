// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ShellComponent } from './core/layout/shell.component';

@Component({
  selector: 'furar-root',
  imports: [ShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<furar-shell />',
})
export class App {}
