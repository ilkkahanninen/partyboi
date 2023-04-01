package org.jumalauta.partyboi

import com.comcast.ip4s.{Host, Port}
import com.typesafe.config.{Config, ConfigFactory}

object Settings {
  def load: Settings = new Settings(ConfigFactory.load())
}

class Settings(config: Config) {
  val server = new ServerSettings(config.getConfig("server"))
  val db = new DatabaseSettings(config.getConfig("database"))
}

class ServerSettings(config: Config) {
  val host: Host = Host.fromString(config.getString("host")).get
  val port: Port = Port.fromString(config.getString("port")).get
}
class DatabaseSettings(config: Config) {
  val host: String = config.getString("host")
  val port: Int = config.getInt("port")
  val user: String = config.getString("user")
  val database: String = config.getString("database")
  val password: String = config.getString("password")

  def url: String = s"jdbc:postgresql://$host:$port/$database"
}
