/**
 * Copyright 2018, by the California Institute of Technology. ALL RIGHTS RESERVED. United States Government Sponsorship acknowledged.
 * Any commercial use must be negotiated with the Office of Technology Transfer at the California Institute of Technology.
 * This software may be subject to U.S. export control laws and regulations.
 * By accepting this document, the user agrees to comply with all applicable U.S. export laws and regulations.
 * User has the responsibility to obtain export licenses, or other export authority as may be required
 * before exporting such information to foreign countries or providing access to foreign persons
 */

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Actions, Effect, ofType } from '@ngrx/effects';
import { Action, Store } from '@ngrx/store';
import { concat, Observable, of } from 'rxjs';
import { RavenAppState } from '../raven-store';

import {
  catchError,
  concatMap,
  map,
  switchMap,
  withLatestFrom,
} from 'rxjs/operators';

import {
  FetchPefEntries,
  SituationalAwarenessActionTypes,
} from '../actions/situational-awareness.actions';

import {
  toCompositeBand,
  toRavenBandData,
  toRavenPefEntries,
} from '../../shared/util';

import {
  MpsServerGraphData,
  MpsServerSituationalAwarenessPefEntry,
  RavenDefaultBandSettings,
} from '../../shared/models';

import * as situationalAwarenessActions from '../actions/situational-awareness.actions';
import * as timelineActions from '../actions/timeline.actions';
import * as fromSituationalAwareness from '../reducers/situational-awareness.reducer';

@Injectable()
export class SituationalAwarenessEffects {
  @Effect()
  changeSituationalAwareness$: Observable<Action> = this.actions$.pipe(
    ofType<situationalAwarenessActions.ChangeSituationalAwareness>(
      SituationalAwarenessActionTypes.ChangeSituationalAwareness,
    ),
    withLatestFrom(this.store$),
    map(([action, state]) => ({ action, state })),
    concatMap(({ state, action }) =>
      concat(
        action.situAware
          ? this.getPefEntriesAsState(
              action.url,
              state.config.raven.defaultBandSettings,
            )
          : this.removePefEntriesBand(),
        of(
          new situationalAwarenessActions.UpdateSituationalAwarenessSettings({
            situationalAware: action.situAware,
          }),
        ),
        action.situAware
          ? of(
              new timelineActions.UpdateViewTimeRange(
                this.getInitialPageStartEndTime(
                  state.raven.situationalAwareness,
                ),
              ),
            )
          : [],
        of(
          new situationalAwarenessActions.UpdateSituationalAwarenessSettings({
            fetchPending: false,
          }),
        ),
      ),
    ),
  );

  @Effect()
  fetchPefEntries$: Observable<Action> = this.actions$.pipe(
    ofType<FetchPefEntries>(SituationalAwarenessActionTypes.FetchPefEntries),
    concatMap(action => this.getPefEntries(action.url)),
    catchError((e: Error) => {
      console.error(
        'SituationalAwarenessEffects - fetchSituationalAwareness$: ',
        e,
      );
      return of(
        new situationalAwarenessActions.UpdateSituationalAwarenessSettings({
          pefEntries: [],
        }),
      );
    }),
  );

  /**
   * Helper. Fetches Pef entries for situational awareness.
   */
  getPefEntries(url: string) {
    return this.http.get(url).pipe(
      map((mpsServerPefEntries: MpsServerSituationalAwarenessPefEntry[]) =>
        toRavenPefEntries(mpsServerPefEntries),
      ),
      switchMap(pefEntries =>
        of(
          new situationalAwarenessActions.UpdateSituationalAwarenessSettings({
            pefEntries,
          }),
        ),
      ),
    );
  }

  /**
   * Helper. Fetches Pef entries for situational awareness as a state timeline.
   */
  getPefEntriesAsState(
    url: string,
    defaultBandSettings: RavenDefaultBandSettings,
  ) {
    return this.http.get(url + 'asState=true&format=TMS').pipe(
      map((graphData: MpsServerGraphData) =>
        toRavenBandData(
          'situAwarePef',
          'situAwarePef',
          graphData,
          defaultBandSettings,
          null,
          {},
        ),
      ),
      switchMap(subBands =>
        of(
          new timelineActions.AddBand(
            'situAwarePef',
            toCompositeBand(subBands[0]),
          ),
        ),
      ),
    );
  }

  /**
   * Helper. Returns start and end time range for the initial page.
   * pageDuration is defaulted to 1 day.
   */
  getInitialPageStartEndTime(
    situationalAwareness: fromSituationalAwareness.SituationalAwarenessState,
  ) {
    let start = 0;
    let pageDuration = 24 * 60 * 60;
    if (situationalAwareness.useNow) {
      start = situationalAwareness.nowMinus
        ? new Date().getTime() / 1000 - situationalAwareness.nowMinus
        : new Date().getTime() / 1000;
      if (
        situationalAwareness.nowMinus &&
        situationalAwareness.nowPlus &&
        situationalAwareness.nowMinus + situationalAwareness.nowPlus !== 0
      ) {
        pageDuration =
          situationalAwareness.nowMinus + situationalAwareness.nowPlus;
      }
    } else {
      start = situationalAwareness.startTime
        ? situationalAwareness.startTime
        : new Date().getTime() / 1000;
      if (
        situationalAwareness.pageDuration &&
        situationalAwareness.pageDuration !== 0
      ) {
        pageDuration = situationalAwareness.pageDuration;
      }
    }
    return { start, end: start + pageDuration };
  }

  /**
   * Helper. Returns action to remove situAwarePef band.
   */
  removePefEntriesBand() {
    return of(new timelineActions.RemoveBandsOrPointsForSource('situAwarePef'));
  }

  constructor(
    private http: HttpClient,
    private actions$: Actions,
    private store$: Store<RavenAppState>,
  ) {}
}
