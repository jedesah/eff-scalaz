package org.atnos.eff

import Effects._
import scalaz._
import scala.annotation.implicitNotFound

/**
 * Member typeclass for effects belonging to a stack of effects R
 *
 * If T is a member of R then we can:
 *
 * - create a Union of effects from a single effect with "inject"
 * - extract an effect value from a union if there is such an effect in the stack
 */
@implicitNotFound("No instance found for Member[${T}, ${R}]. The ${T} effect is not part of the stack ${R} or it was not possible to determine the stack that would result from removing ${T} from ${R}")
trait Member[T[_], R] {
  type Out

  def inject[V](tv: T[V]): Union[R, V]

  def accept[V](union: Union[Out, V]): Union[R, V]

  def project[V](union: Union[R, V]): Union[Out, V] \/ T[V]

  def aux: Member.Aux[T, R, Out] =
    this
}

object Member extends MemberImplicits {

  @implicitNotFound("No instance found for Member[${T}, ${R}]. The ${T} effect is not part of the stack ${R} or it was not possible to determine the stack that would result from removing ${T} from ${R}")
  type Aux[T[_], R, U] = Member[T, R] { type Out = U }

  @implicitNotFound("No instance found for Member[${T}, ${R}]. The ${T} effect is not part of the stack ${R} or it was not possible to determine the stack that would result from removing ${T} from ${R}")
  type <=[T[_], R] = Member[T, R]

  def apply[T[_], R](implicit m: Member[T, R]): Member[T, R] =
    m

  def aux[T[_], R, U](implicit m: Member.Aux[T, R, U]): Member.Aux[T, R, U] =
    m

  def unaux[T[_], R, U](implicit m: Member.Aux[T, R, U]): Member[T, R] =
    m

  def ZeroMember[T[_], R]: Member.Aux[T, T |: R, R] = new Member[T, T |: R] {
    type Out = R

    def inject[V](effect: T[V]): Union[T |: R, V] =
      Union.now(effect)

    def accept[V](union: Union[Out, V]): Union[T |: R, V] =
      UnionNext(union)

    def project[V](union: Union[T |: R, V]): Union[R, V] \/ T[V] =
      union match {
        case UnionNow(x) => \/-(x)
        case UnionNext(u@UnionNow(x)) => -\/(UnionNow(x).asInstanceOf[Union[R, V]])
        case UnionNext(u@UnionNext(x)) => -\/(UnionNext(x).asInstanceOf[Union[R, V]])
      }
  }

  def SuccessorMember[T[_], O[_], R, U](implicit m: Member.Aux[T, R, U]): Member.Aux[T, O |: R, O |: U] = new Member[T, O |: R] {
    type Out = O |: U

    def inject[V](effect: T[V]) =
      Union.next(m.inject[V](effect))

    def accept[V](union: Union[Out, V]): Union[O |: R, V] =
      union match {
        case UnionNow(x)  => union.asInstanceOf[Union[O |: R, V]]
        case UnionNext(u) => UnionNext(m.accept(u.asInstanceOf[Union[U, V]]))
      }

    def project[V](union: Union[O |: R, V]): Union[Out, V] \/ T[V] =
      union match {
        case UnionNow(x) => \/.left(UnionNow(x).asInstanceOf[Union[Out, V]])
        case UnionNext(u) => m.project[V](u).leftMap(u1 => UnionNext(u1).asInstanceOf[Union[Out, V]])
      }
  }

}

trait MemberImplicits extends MemberImplicits1 {
  implicit def zero[T[_]]: Member.Aux[T, T |: NoEffect, NoEffect] =
    Member.ZeroMember[T, NoEffect]
}

trait MemberImplicits1 extends MemberImplicits2 {
  implicit def first[T[_], R]: Member.Aux[T, T |: R, R] =
    Member.ZeroMember[T, R]
}

trait MemberImplicits2 extends MemberImplicits3 {
  implicit def successor[T[_], O[_], R, U](implicit m: Member.Aux[T, R, U]): Member.Aux[T, O |: R, O |: U] =
    Member.SuccessorMember[T, O, R, U](m)
}

trait MemberImplicits3 {
  implicit def successor_[T[_], O[_], R](implicit m: Member[T, R]): Member[T, O |: R] =
    Member.SuccessorMember[T, O, R, m.Out](m)
}


