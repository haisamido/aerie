package gov.nasa.jpl.aerie.merlin.framework;

import gov.nasa.jpl.aerie.merlin.protocol.driver.Query;
import gov.nasa.jpl.aerie.merlin.protocol.driver.Scheduler;
import gov.nasa.jpl.aerie.merlin.protocol.model.Applicator;
import gov.nasa.jpl.aerie.merlin.protocol.model.EffectTrait;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.merlin.protocol.types.TaskStatus;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/* package-local */
final class ThreadedReactionContext<$Timeline> implements Context {
  private final ExecutorService executor;
  private final Scoped<Context> rootContext;
  private final TaskHandle<$Timeline> handle;
  private Scheduler<$Timeline> scheduler;

  public ThreadedReactionContext(
      final ExecutorService executor,
      final Scoped<Context> rootContext,
      final Scheduler<$Timeline> scheduler,
      final TaskHandle<$Timeline> handle)
  {
    this.executor = Objects.requireNonNull(executor);
    this.rootContext = Objects.requireNonNull(rootContext);
    this.scheduler = scheduler;
    this.handle = handle;
  }

  @Override
  public ContextType getContextType() {
    return ContextType.Reacting;
  }

  @Override
  public <CellType> CellType ask(final Query<?, ?, CellType> query) {
    // SAFETY: All objects accessible within a single adaptation instance have the same brand.
    @SuppressWarnings("unchecked")
    final var brandedQuery = (Query<? super $Timeline, ?, CellType>) query;

    return this.scheduler.get(brandedQuery);
  }

  @Override
  public <Event, Effect, CellType> Query<?, Event, CellType> allocate(
      final CellType initialState,
      final Applicator<Effect, CellType> applicator,
      final EffectTrait<Effect> trait,
      final Function<Event, Effect> projection)
  {
    throw new IllegalStateException("Cannot allocate during simulation");
  }

  @Override
  public <Event> void emit(final Event event, final Query<?, Event, ?> query) {
    // SAFETY: All objects accessible within a single adaptation instance have the same brand.
    @SuppressWarnings("unchecked")
    final var brandedQuery = (Query<? super $Timeline, Event, ?>) query;

    this.scheduler.emit(event, brandedQuery);
  }

  @Override
  public String spawn(final TaskFactory task) {
    return this.scheduler.spawn(task.create(this.executor));
  }

  @Override
  public String spawn(final String type, final Map<String, SerializedValue> arguments) {
    return this.scheduler.spawn(type, arguments);
  }

  @Override
  public String defer(final Duration duration, final TaskFactory task) {
    return this.scheduler.defer(duration, task.create(this.executor));
  }

  @Override
  public String defer(final Duration duration, final String type, final Map<String, SerializedValue> arguments) {
    return this.scheduler.defer(duration, type, arguments);
  }

  @Override
  public void delay(final Duration duration) {
    this.scheduler = null;  // Relinquish the current scheduler before yielding, in case an exception is thrown.
    this.scheduler = this.handle.yield(TaskStatus.delayed(duration));
  }

  @Override
  public void waitFor(final String id) {
    this.scheduler = null;  // Relinquish the current scheduler before yielding, in case an exception is thrown.
    this.scheduler = this.handle.yield(TaskStatus.awaiting(id));
  }

  @Override
  public void waitUntil(final Condition condition) {
    this.scheduler = null;  // Relinquish the current scheduler before yielding, in case an exception is thrown.
    this.scheduler = this.handle.yield(TaskStatus.awaiting((now, atLatest) -> {
      try (final var restore = this.rootContext.set(new QueryContext<>(now))) {
        return condition.nextSatisfied(true, Duration.ZERO, atLatest);
      }
    }));
  }
}