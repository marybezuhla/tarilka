package ua.tarilka.data

/** What a typed or restored measurement may be: one place for the screens and the backup. */
object Bounds {
    /** A weight record. Wide enough for any adult or teenager, narrow enough to catch a slip of the finger. */
    val WEIGHT_KG = 25.0..350.0

    /** The weight in the profile, used for the targets. */
    val PROFILE_WEIGHT_KG = 30.0..300.0

    /** A waist measurement. */
    val WAIST_CM = 40.0..250.0
}
