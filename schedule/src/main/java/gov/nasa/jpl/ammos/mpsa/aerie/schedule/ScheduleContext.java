package gov.nasa.jpl.ammos.mpsa.aerie.schedule;

import gov.nasa.jpl.ammos.mpsa.aerie.schemas.Plan;
import gov.nasa.jpl.ammos.mpsa.aerie.schemas.Schedule;

public class ScheduleContext {

  private Strategy strategy;
  private String planId;

  public ScheduleContext(Strategy strategy, String planId) {

    this.strategy = strategy;
    this.planId = planId;
  }

  public Schedule execute() {
    Plan plan = this.getPlan(this.planId);
    return this.strategy.execute(plan);
  }

  public Plan getPlan(String planId) {
    // call the Plan service (Camel route)
    return null;
  }
}