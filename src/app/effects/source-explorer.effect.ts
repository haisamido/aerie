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

import { Observable } from 'rxjs/Observable';

import { combineLatest } from 'rxjs/observable/combineLatest';
import { concat } from 'rxjs/observable/concat';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { of } from 'rxjs/observable/of';

import {
  catchError,
  concatMap,
  map,
  take,
  withLatestFrom,
} from 'rxjs/operators';

import { AppState } from './../../app/store';

import {
  CloseEvent,
  CollapseEvent,
  ExpandEvent,
  FetchInitialSources,
  LoadFromSource,
  OpenEvent,
  RemoveSourceEvent,
  SaveToSource,
  SourceExplorerActionTypes,
} from './../actions/source-explorer';

import * as sourceExplorerActions from './../actions/source-explorer';
import * as timelineActions from './../actions/timeline';

import * as fromSourceExplorer from './../reducers/source-explorer';
import * as fromTimeline from './../reducers/timeline';

import {
  getSourceIds,
  hasActivityByTypeBand,
  isAddTo,
  isOverlay,
  toCompositeBand,
  toRavenBandData,
  toRavenSources,
} from './../shared/util';

import {
  MpsServerGraphData,
  MpsServerSource,
  RavenCompositeBand,
  RavenSource,
  RavenSubBand,
} from './../shared/models';

@Injectable()
export class SourceExplorerEffects {
  @Effect()
  closeEvent$: Observable<Action> = this.actions$.pipe(
    ofType<CloseEvent>(SourceExplorerActionTypes.CloseEvent),
    withLatestFrom(this.store$),
    map(([action, state]) => ({ action, state })),
    concatMap(({ state, action }) => {
      return [
        new timelineActions.RemoveBandsOrPointsForSource(action.sourceId),
        new sourceExplorerActions.UpdateTreeSource(action.sourceId, {
          opened: false,
          subBandIds: {},
        }),
      ];
    }),
  );

  @Effect()
  collapseEvent$: Observable<Action> = this.actions$.pipe(
    ofType<CollapseEvent>(SourceExplorerActionTypes.CollapseEvent),
    map(action => new sourceExplorerActions.UpdateTreeSource(action.sourceId, { expanded: false })),
  );

  @Effect()
  expandEvent$: Observable<Action> = this.actions$.pipe(
    ofType<ExpandEvent>(SourceExplorerActionTypes.ExpandEvent),
    withLatestFrom(this.store$),
    map(([action, state]) => state.sourceExplorer.treeBySourceId[action.sourceId]),
    concatMap(source =>
      concat(
        ...this.expand(source),
        of(new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false })),
      ),
    ),
  );

  @Effect()
  fetchInitialSources$: Observable<Action> = this.actions$.pipe(
    ofType<FetchInitialSources>(SourceExplorerActionTypes.FetchInitialSources),
    withLatestFrom(this.store$),
    map(([action, state]) => state),
    concatMap((state: AppState) => [
      this.fetchNewSources(`${state.config.baseUrl}/${state.config.baseSourcesUrl}`, '/', true).pipe(
        map((sources: RavenSource[]) => new sourceExplorerActions.NewSources('/', sources) as Action),
      ),
      of(new sourceExplorerActions.UpdateSourceExplorer({
        fetchPending: false,
        initialSourcesLoaded: true,
      })),
    ]),
    concatMap(actions => actions),
  );

  @Effect()
  loadFromSource$: Observable<Action> = this.actions$.pipe(
    ofType<LoadFromSource>(SourceExplorerActionTypes.LoadFromSource),
    withLatestFrom(this.store$),
    map(([action, state]) => ({ action, state })),
    concatMap(({ action, state }) =>
      forkJoin([
        of(action),
        this.fetchSavedState(action.sourceUrl),
        this.fetchNewSources(`${state.config.baseUrl}/${state.config.baseSourcesUrl}`, '/', true),
      ]),
    ),
    map(([action, state, sources]) => ({ action, state, sources })),
    concatMap(({ action, state: { bands }, sources }) =>
      concat(...this.load(bands, sources)),
    ),
  );

  @Effect()
  openEvent$: Observable<Action> = this.actions$.pipe(
    ofType<OpenEvent>(SourceExplorerActionTypes.OpenEvent),
    withLatestFrom(this.store$),
    map(([action, state]) => ({ action, state })),
    concatMap(({ state, action }) =>
      forkJoin([
        of(state),
        of(action),
        this.fetchBands(state.sourceExplorer.treeBySourceId[action.sourceId].url, action.sourceId),
      ]),
    ),
    map(([state, action, bands]) => ({ state, action, bands })),
    concatMap(({ state, action, bands }) =>
      this.open(state, action, bands),
    ),
  );

  @Effect()
  removeSourceEvent$: Observable<Action> = this.actions$.pipe(
    ofType<RemoveSourceEvent>(SourceExplorerActionTypes.RemoveSourceEvent),
    withLatestFrom(this.store$),
    map(([action, state]) => ({ action, state })),
    concatMap(({ state, action }) => [
      this.removeSource(action.source.url, action.source.id),
      of(new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false })),
    ]),
    concatMap(actions => actions),
  );

  @Effect()
  saveToSource$: Observable<Action> = this.actions$.pipe(
    ofType<SaveToSource>(SourceExplorerActionTypes.SaveToSource),
    withLatestFrom(this.store$),
    map(([action, state]) => ({ action, state })),
    concatMap(({ state, action }) => [
      this.saveToSource(action.source.url, action.source.id, action.name, {
        name: `raven2-state-${action.name}`,
        state: {
          bands: state.timeline.bands.map(band => ({
            ...band,
            subBands: band.subBands.map(subBand => ({
              ...subBand,
              points: [],
            })),
          })),
        },
      }),
      of(new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false })),
    ]),
    concatMap(actions => actions),
  );

  constructor(
    private http: HttpClient,
    private actions$: Actions,
    private store$: Store<AppState>,
  ) {}

  /**
   * Helper. Returns a stream of actions that need to occur when expanding a source explorer source.
   */
  expand(source: RavenSource): Observable<Action>[] {
    const actions: Observable<Action>[] = [];

    if (source) {
      if (!source.childIds.length) {
        if (source.content.length > 0) {
          actions.push(
            of(new sourceExplorerActions.NewSources(source.id, toRavenSources(source.id, false, source.content))),
          );
        } else {
          actions.push(
            this.fetchNewSources(source.url, source.id, false).pipe(
              map((sources: RavenSource[]) => new sourceExplorerActions.NewSources(source.id, sources)),
            ),
          );
        }
      }

      actions.push(
        of(new sourceExplorerActions.UpdateTreeSource(source.id, { expanded: true })),
      );
    }

    return actions;
  }

  /**
   * Helper. Returns a stream of actions that need to occur when loading a state.
   */
  load(bands: RavenCompositeBand[], initialSources: RavenSource[]) {
    const { parentSourceIds } = getSourceIds(bands);

    return [
      of(new sourceExplorerActions.UpdateSourceExplorer({
        ...fromSourceExplorer.initialState,
        fetchPending: true,
      })),
      of(new timelineActions.UpdateTimeline({
        ...fromTimeline.initialState,
      })),
      of(new sourceExplorerActions.NewSources('/', initialSources)),
      ...parentSourceIds.map((sourceId: string) =>
        combineLatest(this.store$, state => state.sourceExplorer.treeBySourceId[sourceId]).pipe(
          take(1),
          concatMap(source =>
            concat(...this.expand(source)),
          ),
        ),
      ),
      of(new timelineActions.UpdateTimeline({
        bands,
      })),
      of(new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false })),
    ];
  }

  /**
   * Helper. Returns a stream of actions that need to occur when opening a source explorer source.
   */
  open(state: AppState, action: OpenEvent, bands: RavenSubBand[]): Action[] {
    const actions: Action[] = [
      new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: true }),
    ];

    bands.forEach((subBand: RavenSubBand) => {
      const activityByTypeBand = hasActivityByTypeBand(state.timeline.bands, subBand);

      if (activityByTypeBand) {
        actions.push(
          new sourceExplorerActions.SubBandIdAdd(action.sourceId, activityByTypeBand.subBandId),
          new timelineActions.AddPointsToSubBand(action.sourceId, activityByTypeBand.bandId, activityByTypeBand.subBandId, subBand.points),
        );
      } else if (isAddTo(state.timeline.bands, state.timeline.selectedBandId, state.timeline.selectedSubBandId, subBand.type)) {
        actions.push(
          new sourceExplorerActions.SubBandIdAdd(action.sourceId, state.timeline.selectedSubBandId),
          new timelineActions.AddPointsToSubBand(action.sourceId, state.timeline.selectedBandId, state.timeline.selectedSubBandId, subBand.points),
        );
      } else if (isOverlay(state.timeline.bands, state.timeline.selectedBandId)) {
        actions.push(
          new sourceExplorerActions.SubBandIdAdd(action.sourceId, subBand.id),
          new timelineActions.AddSubBand(action.sourceId, state.timeline.selectedBandId, subBand),
        );
      } else {
        actions.push(
          new sourceExplorerActions.SubBandIdAdd(action.sourceId, subBand.id),
          new timelineActions.AddBand(action.sourceId, toCompositeBand(action.sourceId, subBand)),
        );
      }
    });

    actions.push(
      new sourceExplorerActions.UpdateTreeSource(action.sourceId, { opened: true }),
      new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false }),
    );

    return actions;
  }

  /**
   * Fetch helper. Fetches graph data from MPS Server and maps it to Raven band data.
   */
  fetchBands(sourceUrl: string, sourceId: string) {
    return this.http.get<MpsServerGraphData>(sourceUrl).pipe(
      map((graphData: MpsServerGraphData) => toRavenBandData(sourceId, graphData)),
      catchError(e => {
        console.error('SourceExplorerEffects - fetchBands error: ', e);
        return of(new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false }));
      }),
    );
  }

  /**
   * Fetch helper. Fetches sources from MPS Server and maps them to Raven sources.
   */
  fetchNewSources(url: string, parentId: string, isServer: boolean) {
    return this.http.get<MpsServerSource[]>(url).pipe(
      map((mpsServerSources: MpsServerSource[]) => toRavenSources(parentId, isServer, mpsServerSources)),
      catchError(e => {
        console.error('SourceExplorerEffects - fetchNewSources error: ', e);
        return of(new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false }));
      }),
    );
  }

  /**
   * Fetch helper. Fetches saved state from MPS Server.
   */
  fetchSavedState(url: string) {
    return this.http.get(url).pipe(
      map(res => res[0].state),
      catchError(e => {
        console.error('SourceExplorerEffects - fetchSavedState error: ', e);
        return of(new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false }));
      }),
    );
  }

  /**
   * Fetch helper. Deletes a source from MPS Server.
   */
  removeSource(sourceUrl: string, sourceId: string) {
    // TODO: Make this better so we don't have to change the URL.
    const url = sourceUrl.replace(/(list_)?generic-mongodb/i, 'fs-mongodb');

    return this.http.delete(url, { responseType: 'text' }).pipe(
      map(() => new sourceExplorerActions.RemoveSource(sourceId)),
      catchError(e => {
        console.error('SourceExplorerEffects - removeSource error: ', e);
        return of(new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false }));
      }),
    );
  }

  /**
   * Helper. Save some data to an MPS Server source.
   *
   * TODO: Replace 'any' with a concrete type.
   */
  saveToSource(sourceUrl: string, sourceId: string, name: string, data: any) {
    return this.http.put(`${sourceUrl}/${name}`, data).pipe(
      concatMap(() =>
        this.fetchNewSources(sourceUrl, sourceId, false).pipe(
          map((sources: RavenSource[]) => new sourceExplorerActions.NewSources(sourceId, sources)),
        ),
      ),
      catchError(e => {
        console.error('SourceExplorerEffects - saveToSource error: ', e);
        return of(new sourceExplorerActions.UpdateSourceExplorer({ fetchPending: false }));
      }),
    );
  }
}
