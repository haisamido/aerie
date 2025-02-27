package gov.nasa.jpl.aerie.timeline

/**
 * Options for collecting a timeline.
 */
data class CollectOptions @JvmOverloads constructor(
    /** The bounds on which to evaluate the timeline. */
    @JvmField val bounds: Interval,

    /**
     * Whether to truncate objects that extend outside the bounds.
     *
     * Objects with no intersection with the bounds should never be included in the results.
     */
    @JvmField val truncateMarginal: Boolean = true
) {
  /** Creates a new options object with a [BoundsTransformer] applied. */
  fun transformBounds(boundsTransformer: BoundsTransformer) = CollectOptions(boundsTransformer(bounds), truncateMarginal)
}
