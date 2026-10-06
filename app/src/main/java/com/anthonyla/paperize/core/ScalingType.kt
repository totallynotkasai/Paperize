package com.anthonyla.paperize.core

enum class ScalingType {
    /**
     * Fill the screen, cropping if necessary
     */
    FILL,

    /**
     * Fit the entire image, adding letterboxing/pillarboxing if necessary
     */
    FIT,

    STRETCH,

    /**
     * Display at original size
     */
    NONE;

    /** Whether this scaling can leave part of an image off screen, which live auto-pan reveals. */
    val canCutOff: Boolean get() = this == FILL || this == NONE

    companion object {
        fun fromString(value: String?): ScalingType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: FILL
        }
    }
}
