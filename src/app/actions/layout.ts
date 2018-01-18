/**
 * Copyright 2018, by the California Institute of Technology. ALL RIGHTS RESERVED. United States Government Sponsorship acknowledged.
 * Any commercial use must be negotiated with the Office of Technology Transfer at the California Institute of Technology.
 * This software may be subject to U.S. export control laws and regulations.
 * By accepting this document, the user agrees to comply with all applicable U.S. export laws and regulations.
 * User has the responsibility to obtain export licenses, or other export authority as may be required
 * before exporting such information to foreign countries or providing access to foreign persons
 */

import { Action } from '@ngrx/store';

// Action Types.
export enum LayoutActionTypes {
  ToggleDetailsDrawer    = '[layout] toggle_details_drawer',
  ToggleLeftDrawer       = '[layout] toggle_left_drawer',
  ToggleSouthBandsDrawer = '[layout] toggle_south_bands_drawer',
}

// Actions.
export class ToggleDetailsDrawer implements Action {
  readonly type = LayoutActionTypes.ToggleDetailsDrawer;
}


export class ToggleLeftDrawer implements Action {
  readonly type = LayoutActionTypes.ToggleLeftDrawer;
}

export class ToggleSouthBandsDrawer implements Action {
  readonly type = LayoutActionTypes.ToggleSouthBandsDrawer;
}

// Union type of all actions.
export type LayoutAction =
  ToggleDetailsDrawer |
  ToggleLeftDrawer |
  ToggleSouthBandsDrawer;
