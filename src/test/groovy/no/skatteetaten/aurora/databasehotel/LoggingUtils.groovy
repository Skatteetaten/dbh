package no.skatteetaten.aurora.databasehotel

import org.slf4j.LoggerFactory

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger

class LoggingUtils {

  static void setLogLevels() {

    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
    root.level = Level.INFO
    ((Logger) LoggerFactory.getLogger("no.skatteetaten")).level = Level.INFO
    ((Logger) LoggerFactory.getLogger("com.zaxxer.hikari")).level = Level.WARN
  }
}
