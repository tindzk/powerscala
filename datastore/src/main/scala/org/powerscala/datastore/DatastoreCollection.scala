package org.powerscala.datastore

import event.{DatastoreDelete, DatastorePersist}
import java.util
import org.powerscala.event.Listenable
import org.powerscala.reflect.EnhancedClass
import query.{Field, DatastoreQuery}
import org.powerscala.hierarchy.Child

/**
 * @author Matt Hicks <mhicks@powerscala.org>
 */
trait DatastoreCollection[T <: Identifiable] extends Iterable[T] with Listenable with Child {
  def name: String
  def session: DatastoreSession
  def parent = session

  private var ids = Set.empty[util.UUID]

  def isPersisted(id: util.UUID): Boolean = ids.contains(id)

  def isPersisted(ref: T): Boolean = isPersisted(ref.id)

  final def persist(refs: T*): Unit = {
    refs.foreach {
      case ref => {
        isPersisted(ref.id) match {
          case true => persistModified(ref)
          case false => persistNew(ref)
        }
        ids += ref.id
        fire(DatastorePersist(this, ref))
      }
    }
  }

  final def delete(refs: T*): Unit = {
    refs.foreach {
      case ref => {
        deleteInternal(ref)
        ids -= ref.id
        fire(DatastoreDelete(this, ref))
      }
    }
  }

  def byId(id: util.UUID): Option[T]

  def byExample(example: T) = {
    val ec = EnhancedClass(example.getClass)
    val method = ec.createMethod.getOrElse(throw new NullPointerException("%s is not a case class".format(example)))
    val companion = ec.companion.getOrElse(throw new NullPointerException("No companion found for %s".format(example)))
    val companionInstance = companion.instance.getOrElse(throw new NullPointerException("No companion instance found for %s".format(companion)))
    val defaults = method.args.collect {
      // Generate defaults excluding "id"
      case arg if (arg.name != "id") => arg.default(companionInstance) match {
        case None => arg.name -> arg.`type`.defaultForType // Default by the class type
        case Some(value) => arg.name -> value // Default argument for this case class
      }
    }.toMap
    var q = this.query
    ec.caseValues.foreach(cv => if (cv.name != "id" && defaults(cv.name) != cv[Any](example)) {
      val value = cv[Any](example)
      val field = Field[T, Any](cv.name)
      q = q.filter(field equal value)
    })
    q
  }

  def query = DatastoreQuery(collection = this)

  protected[datastore] def executeQuery(query: DatastoreQuery[T]): Iterator[T]

  protected def persistNew(ref: T): Unit

  protected def persistModified(ref: T): Unit

  protected def deleteInternal(ref: T): Unit

  override def toString() = "%s[%s](%s)".format(getClass.getSimpleName, manifest.erasure.getSimpleName, name)
}

object DatastoreCollection {
  def assignId(collection: DatastoreCollection[_], id: util.UUID) = collection.ids += id
}