package gov.nasa.jpl.ammos.mpsa.aerie.banananation.generated.activities;

import gov.nasa.jpl.ammos.mpsa.aerie.banananation.BanananationResources;
import gov.nasa.jpl.ammos.mpsa.aerie.banananation.activities.BiteBananaActivity;
import gov.nasa.jpl.ammos.mpsa.aerie.banananation.generated.mappers.BiteBananaActivityMapper;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.framework.Context;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.framework.ThreadedTask;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.Task;
import gov.nasa.jpl.ammos.mpsa.aerie.merlin.protocol.TaskSpecType;
import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.serialization.SerializedValue;
import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.serialization.ValueSchema;
import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.utilities.DynamicCell;

import java.util.List;
import java.util.Map;

public class BiteBananaActivityType<$Schema> implements TaskSpecType<$Schema, BiteBananaActivity> {
  private final BiteBananaActivityMapper mapper = new BiteBananaActivityMapper();

  private final DynamicCell<Context<$Schema>> rootContext;
  private final BanananationResources<$Schema> container;

  public BiteBananaActivityType(
      final DynamicCell<Context<$Schema>> rootContext,
      final BanananationResources<$Schema> container)
  {
    this.rootContext = rootContext;
    this.container = container;
  }

  @Override
  public String getName() {
    return this.mapper.getName();
  }

  @Override
  public Map<String, ValueSchema> getParameters() {
    return this.mapper.getParameters();
  }

  @Override
  public BiteBananaActivity instantiateDefault() {
    return this.mapper.instantiateDefault();
  }

  @Override
  public BiteBananaActivity instantiate(final Map<String, SerializedValue> arguments)
  throws UnconstructableTaskSpecException
  {
    return this.mapper.instantiate(arguments);
  }

  @Override
  public Map<String, SerializedValue> getArguments(final BiteBananaActivity activity) {
    return this.mapper.getArguments(activity);
  }

  @Override
  public List<String> getValidationFailures(final BiteBananaActivity activity) {
    return this.mapper.getValidationFailures(activity);
  }

  @Override
  public <$Timeline extends $Schema> Task<$Timeline> createTask(final BiteBananaActivity activity) {
    return new ThreadedTask<>(
        this.rootContext,
        () -> activity
            .new EffectModel<$Schema>()
            .runWith(this.rootContext.get(), this.container));
  }
}