package gov.nasa.jpl.aerie.merlin.server.services;

import gov.nasa.jpl.aerie.constraints.InputMismatchException;
import gov.nasa.jpl.aerie.constraints.model.ActivityInstance;
import gov.nasa.jpl.aerie.constraints.model.DiscreteProfile;
import gov.nasa.jpl.aerie.constraints.model.DiscreteProfilePiece;
import gov.nasa.jpl.aerie.constraints.model.LinearProfile;
import gov.nasa.jpl.aerie.constraints.model.LinearProfilePiece;
import gov.nasa.jpl.aerie.constraints.model.Violation;
import gov.nasa.jpl.aerie.constraints.time.Interval;
import gov.nasa.jpl.aerie.constraints.tree.Expression;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.merlin.server.ResultsProtocol;
import gov.nasa.jpl.aerie.merlin.server.exceptions.NoSuchPlanException;
import gov.nasa.jpl.aerie.merlin.server.models.Constraint;
import gov.nasa.jpl.aerie.merlin.server.models.PlanId;
import org.apache.commons.lang3.tuple.Pair;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GetSimulationResultsAction {
  public sealed interface Response {
    record Pending(long simulationDatasetId) implements Response {}
    record Incomplete(long simulationDatasetId) implements Response {}
    record Failed(long simulationDatasetId, String reason) implements Response {}
    record Complete(long simulationDatasetId) implements Response {}
  }

  private final PlanService planService;
  private final MissionModelService missionModelService;
  private final SimulationService simulationService;
  private final ConstraintsDSLCompilationService constraintsDSLCompilationService;

  public GetSimulationResultsAction(
      final PlanService planService,
      final MissionModelService missionModelService,
      final SimulationService simulationService,
      final ConstraintsDSLCompilationService constraintsDSLCompilationService
  ) {
    this.planService = Objects.requireNonNull(planService);
    this.missionModelService = Objects.requireNonNull(missionModelService);
    this.simulationService = Objects.requireNonNull(simulationService);
    this.constraintsDSLCompilationService = Objects.requireNonNull(constraintsDSLCompilationService);
  }

  public Response run(final PlanId planId) throws NoSuchPlanException, MissionModelService.NoSuchMissionModelException {
    final var revisionData = this.planService.getPlanRevisionData(planId);

    final var response = this.simulationService.getSimulationResults(planId, revisionData);

    if (response instanceof ResultsProtocol.State.Pending r) {
      return new Response.Pending(r.simulationDatasetId());
    } else if (response instanceof ResultsProtocol.State.Incomplete r) {
      return new Response.Incomplete(r.simulationDatasetId());
    } else if (response instanceof ResultsProtocol.State.Failed r) {
      return new Response.Failed(r.simulationDatasetId(), r.reason());
    } else if (response instanceof ResultsProtocol.State.Success r) {
      return new Response.Complete(r.simulationDatasetId());
    } else {
      throw new UnexpectedSubtypeError(ResultsProtocol.State.class, response);
    }
  }

  public Map<String, List<Pair<Duration, SerializedValue>>> getResourceSamples(final PlanId planId)
  throws NoSuchPlanException
  {
    final var revisionData = this.planService.getPlanRevisionData(planId);
    return this.simulationService
        .get(planId, revisionData)
        .map(r -> r.resourceSamples)
        .orElseGet(Collections::emptyMap);
  }

  public Map<String, List<Violation>> getViolations(final PlanId planId)
  throws NoSuchPlanException, MissionModelService.NoSuchMissionModelException
  {
    final var plan = this.planService.getPlan(planId);
    final var revisionData = this.planService.getPlanRevisionData(planId);

    final var constraintJsons = new HashMap<String, Constraint>();

    try {
      this.missionModelService.getConstraints(plan.missionModelId).forEach(
          (name, constraint) -> constraintJsons.put("model/" + name, constraint)
      );
      this.planService.getConstraintsForPlan(planId).forEach(
          (name, constraint) -> constraintJsons.put("plan/" + name, constraint)
      );
    } catch (final MissionModelService.NoSuchMissionModelException ex) {
      throw new RuntimeException("Assumption falsified -- mission model for existing plan does not exist");
    }

    final var results$ = this.simulationService.get(planId, revisionData);

    final var activities = new ArrayList<ActivityInstance>();
    final var simulatedActivities = results$
        .map(r -> r.simulatedActivities)
        .orElseGet(Collections::emptyMap);
    for (final var entry : simulatedActivities.entrySet()) {
      final var id = entry.getKey();
      final var activity = entry.getValue();

      final var activityOffset = Duration.of(
          plan.startTimestamp.toInstant().until(activity.start(), ChronoUnit.MICROS),
          Duration.MICROSECONDS);

      activities.add(new ActivityInstance(
          id.id(),
          activity.type(),
          activity.arguments(),
          Interval.between(activityOffset, activityOffset.plus(activity.duration()))));
    }
    final var _discreteProfiles = results$
        .map(r -> r.discreteProfiles)
        .orElseGet(Collections::emptyMap);
    final var discreteProfiles = new HashMap<String, DiscreteProfile>(_discreteProfiles.size());
    for (final var entry : _discreteProfiles.entrySet()) {
      final var pieces = new ArrayList<DiscreteProfilePiece>(entry.getValue().getRight().size());

      var elapsed = Duration.ZERO;
      for (final var piece : entry.getValue().getRight()) {
        final var extent = piece.getLeft();
        final var value = piece.getRight();

        pieces.add(new DiscreteProfilePiece(
            Interval.between(elapsed, elapsed.plus(extent)),
            value));

        elapsed = elapsed.plus(extent);
      }

      discreteProfiles.put(entry.getKey(), new DiscreteProfile(pieces));
    }
    final var _realProfiles = results$
        .map(r -> r.realProfiles)
        .orElseGet(Collections::emptyMap);
    final var realProfiles = new HashMap<String, LinearProfile>();
    for (final var entry : _realProfiles.entrySet()) {
      final var pieces = new ArrayList<LinearProfilePiece>(entry.getValue().getRight().size());

      var elapsed = Duration.ZERO;
      for (final var piece : entry.getValue().getRight()) {
        final var extent = piece.getLeft();
        final var value = piece.getRight();

        pieces.add(new LinearProfilePiece(
            Interval.between(elapsed, elapsed.plus(extent)),
            value.initial,
            value.rate));

        elapsed = elapsed.plus(extent);
      }

      realProfiles.put(entry.getKey(), new LinearProfile(pieces));
    }

    final var planDuration = Duration.of(
        plan.startTimestamp.toInstant().until(plan.endTimestamp.toInstant(), ChronoUnit.MICROS),
        Duration.MICROSECONDS);

    final var preparedResults = new gov.nasa.jpl.aerie.constraints.model.SimulationResults(
        Interval.between(Duration.ZERO, planDuration),
        activities,
        realProfiles,
        discreteProfiles);

    final var violations = new HashMap<String, List<Violation>>();
    for (final var entry : constraintJsons.entrySet()) {

      // Pipeline switch
      // To remove the old constraints pipeline, delete the `useNewConstraintPipeline` variable
      // and the else branch of this if statement.
      final var constraint = entry.getValue();
      final Expression<List<Violation>> expression;

      // TODO: cache these results
      final var constraintCompilationResult = constraintsDSLCompilationService.compileConstraintsDSL(
          plan.missionModelId,
          Optional.of(planId),
          constraint.definition()
      );

      if (constraintCompilationResult instanceof ConstraintsDSLCompilationService.ConstraintsDSLCompilationResult.Success success) {
        expression = success.constraintExpression();
      } else if (constraintCompilationResult instanceof ConstraintsDSLCompilationService.ConstraintsDSLCompilationResult.Error error) {
        throw new Error("Constraint compilation failed: " + error);
      } else {
        throw new Error("Unhandled variant of ConstraintsDSLCompilationResult: " + constraintCompilationResult);
      }

      final var violationEvents = new ArrayList<Violation>();
      try {
        violationEvents.addAll(expression.evaluate(preparedResults));
      } catch (final InputMismatchException ex) {
        // @TODO Need a better way to catch and propagate the exception to the
        // front end and to log the evaluation failure. This is captured in AERIE-1285.
      }


      if (violationEvents.isEmpty()) continue;

      /* TODO: constraint.evaluate returns an List<Violations> with a single empty unpopulated Violation
          which prevents the above condition being sufficient in all cases. A ticket AERIE-1230 has been
          created to account for refactoring and removing the need for this condition. */
      if (violationEvents.size() == 1 && violationEvents.get(0).violationWindows.isEmpty()) continue;

      final var names = new HashSet<String>();
      expression.extractResources(names);
      final var resourceNames = new ArrayList<>(names);
      final var violationEventsWithNames = new ArrayList<Violation>();
      violationEvents.forEach(violation -> violationEventsWithNames.add(new Violation(
          violation.activityInstanceIds,
          resourceNames,
          violation.violationWindows)));

      violations.put(entry.getKey(), violationEventsWithNames);
    }

    return violations;
  }
}
