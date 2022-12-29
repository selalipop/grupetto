package com.spop.poverlay.sensor

/**
 * A special class of exception that represents a problem with a Peloton sensor, such as timing out
 *
 * Throw this instead of a normal exception in cases where a Sensor has a recoverable error
 */
class RecoverableSensorException(message: String?, cause: Throwable? = null) :
    Exception(message, cause)