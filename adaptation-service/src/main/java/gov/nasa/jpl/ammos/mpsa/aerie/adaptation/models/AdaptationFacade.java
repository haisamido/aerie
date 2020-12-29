package gov.nasa.jpl.ammos.mpsa.aerie.adaptation.models;

import gov.nasa.jpl.ammos.mpsa.aerie.merlin.driver.SimulationDriver;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.driver.SimulationResults;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.Adaptation;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.Approximator;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.DiscreteApproximator;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.RealApproximator;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.TaskSpecType;
import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.constraints.ViolableConstraint;
import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.serialization.SerializedActivity;
import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.serialization.SerializedValue;
import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.serialization.ValueSchema;
import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.time.Duration;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AdaptationFacade<$Schema> {
  private final Adaptation<$Schema> adaptation;

  public AdaptationFacade(final Adaptation<$Schema> adaptation) throws AdaptationContractException {
    this.adaptation = adaptation;
  }

  public SimulationResults simulate(
      final Map<String, Pair<Duration, SerializedActivity>> schedule,
      final Duration simulationDuration,
      final Duration samplingPeriod,
      final Instant startTime
  ) throws SimulationDriver.TaskSpecInstantiationException
  {
    return SimulationDriver.simulate(this.adaptation, schedule, startTime, simulationDuration, samplingPeriod);
  }

  public List<ViolableConstraint> getConstraintTypes() throws AdaptationContractException {
    return Collections.emptyList();
  }

  public Map<String, ValueSchema> getStateSchemas() {
    final class SchemaGetter<T> implements Approximator.Visitor<T, ValueSchema> {
      @Override
      public ValueSchema real(final RealApproximator<T> approximator) {
        return ValueSchema.REAL;
      }

      @Override
      public ValueSchema discrete(final DiscreteApproximator<T> approximator) {
        return approximator.getSchema();
      }
    }

    final var schemas = new HashMap<String, ValueSchema>();

    for (final var family : this.adaptation.getResourceFamilies()) {
      final var schema = family.getSolver().getApproximator().match(new SchemaGetter<>());

      for (final var name : family.getResources().keySet()) {
        schemas.put(name, schema);
      }
    }

    return schemas;
  }

  public Map<String, ActivityType> getActivityTypes() throws AdaptationContractException {
    final var activityTypes = new HashMap<String, ActivityType>();
    this.adaptation.getTaskSpecificationTypes().forEach((name, specType) -> {
      activityTypes.put(name, new ActivityType(name, specType.getParameters(), getDefaultArguments(specType)));
    });
    return activityTypes;
  }

  public ActivityType getActivityType(final String typeName)
  throws NoSuchActivityTypeException, AdaptationContractException
  {
    final var specType = Optional
        .ofNullable(this.adaptation.getTaskSpecificationTypes().get(typeName))
        .orElseThrow(NoSuchActivityTypeException::new);

    return new ActivityType(typeName, specType.getParameters(), getDefaultArguments(specType));
  }

  public List<String> validateActivity(final String typeName, final Map<String, SerializedValue> arguments)
  throws NoSuchActivityTypeException, UnconstructableActivityInstanceException
  {
    final var specType = Optional
        .ofNullable(this.adaptation.getTaskSpecificationTypes().get(typeName))
        .orElseThrow(NoSuchActivityTypeException::new);

    return getValidationFailures(specType, arguments);
  }

  private <Specification> Map<String, SerializedValue> getDefaultArguments(final TaskSpecType<$Schema, Specification> specType) {
    return specType.getArguments(specType.instantiateDefault());
  }

  private <Specification> List<String> getValidationFailures(
      final TaskSpecType<$Schema, Specification> specType,
      final Map<String, SerializedValue> arguments)
  throws UnconstructableActivityInstanceException
  {
    try {
      return specType.getValidationFailures(specType.instantiate(arguments));
    } catch (final TaskSpecType.UnconstructableTaskSpecException e) {
      throw new UnconstructableActivityInstanceException(
          "Unknown failure when deserializing activity -- do the parameters match the schema?",
          e);
    }
  }

  public static class AdaptationContractException extends RuntimeException {
    public AdaptationContractException(final String message) {
      super(message);
    }

    public AdaptationContractException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  public static class NoSuchActivityTypeException extends Exception {}

  public static class UnconstructableActivityInstanceException extends Exception {
    public UnconstructableActivityInstanceException(final String message) {
      super(message);
    }

    public UnconstructableActivityInstanceException(final String message, final Throwable cause) {
      super(
          message,
          cause);
    }
  }
}