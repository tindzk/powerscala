package org.powerscala.event

/**
 * @author Matt Hicks <matt@outr.com>
 */
trait Listenable {
  val listeners = new Listeners
}