/*
 * Copyright (C) 2020 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.projection.testing.simulation

import scala.collection.BufferedIterator
import scala.collection.mutable.ArrayBuffer

object IteratorExtensions {
  implicit class RichIterator[A](iterator: Iterator[A]) {
    def zipWithSeqNr: Iterator[(A, Int)] = {
      iterator.zipWithIndex.map { case (element, index) => (element, index + 1) }
    }
  }
}

final class MergedIterator[A](iterators: Seq[Iterator[A]])(implicit ordering: Ordering[A]) extends Iterator[A] {
  private var bufferedIterators: Seq[BufferedIterator[A]] = iterators.map(_.buffered)

  def hasNext: Boolean = {
    bufferedIterators = bufferedIterators.filter(_.hasNext)
    bufferedIterators.nonEmpty
  }

  def next(): A = {
    if (hasNext) bufferedIterators.minBy(_.head).next()
    else throw new NoSuchElementException("next on empty iterator")
  }
}

final class FlatMergedIterator[A](source: Iterator[Iterator[A]])(implicit ordering: Ordering[A]) extends Iterator[A] {
  private val bufferedSource = source.filter(_.hasNext).map(_.buffered).buffered

  private var iterators: List[BufferedIterator[A]] = Nil

  private def minIterator = iterators.minBy(_.head)

  private def merge(): Unit = {
    iterators = iterators.filter(_.hasNext) // prune empty iterators
    if (bufferedSource.hasNext && (iterators.isEmpty || ordering.lt(bufferedSource.head.head, minIterator.head))) {
      iterators ::= bufferedSource.next() // merge in next iterator
    }
  }

  def hasNext: Boolean = {
    merge()
    iterators.nonEmpty
  }

  def next(): A = {
    if (hasNext) minIterator.next()
    else throw new NoSuchElementException("next on empty iterator")
  }
}

final class GroupedIterator[A, B](iterator: Iterator[A], boundaries: Iterator[B], point: A => B)(implicit
    ordering: Ordering[B])
    extends Iterator[Seq[A]] {

  private val bufferedIterator = iterator.buffered

  override def hasNext: Boolean = bufferedIterator.hasNext

  override def next(): Seq[A] = {
    if (!hasNext) throw new NoSuchElementException("next on empty iterator")

    val group = ArrayBuffer.empty[A]
    val boundary = if (boundaries.hasNext) Some(boundaries.next()) else None

    while (bufferedIterator.hasNext && boundary.forall(ordering.lt(point(bufferedIterator.head), _))) {
      group += bufferedIterator.next()
    }

    group.toSeq
  }
}
