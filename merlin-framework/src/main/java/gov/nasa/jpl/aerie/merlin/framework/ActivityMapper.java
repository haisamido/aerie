package gov.nasa.jpl.aerie.merlin.framework;

import gov.nasa.jpl.aerie.merlin.protocol.driver.Topic;
import gov.nasa.jpl.aerie.merlin.protocol.model.DirectiveType;

public interface ActivityMapper<Model, Specification, Return> extends DirectiveType<Model, Specification, Return> {
  Topic<Specification> getInputTopic();
  Topic<Return> getOutputTopic();
}
