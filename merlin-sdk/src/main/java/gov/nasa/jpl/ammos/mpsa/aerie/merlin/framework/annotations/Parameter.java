package gov.nasa.jpl.ammos.mpsa.aerie.merlin.framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// A parameter to a task specification.
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface Parameter {
}
