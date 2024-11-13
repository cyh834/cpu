package cpu.frontend.decoder

trait DecodeAttribute[T]{
    val value:       T
}

trait Uop
object UopDC extends Uop
trait UopDecodeAttribute[T <: Uop] extends DecodeAttribute[T]