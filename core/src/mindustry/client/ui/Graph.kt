package mindustry.client.ui

import arc.graphics.Color
import arc.graphics.g2d.Lines
import arc.scene.Element

class Graph(vararg val series: Series) : Element() {
    private var range = 0.0..1.0

    override fun draw() {
        super.draw()
        Lines.stroke(2f, Color.white)
        Lines.rect(x, y, width, height)
    }

    data class Series(val name: String, val color: Color, val data: Datapoint.DatapointArray)

    @JvmInline
    /** don't question */
    value class Datapoint private constructor(private val data: ULong) {
        constructor(x: Float, y: Float) :
                this((java.lang.Float.floatToIntBits(x).toULong() shl 31) or
                        java.lang.Float.floatToIntBits(y).toULong())

        val x get() = java.lang.Float.intBitsToFloat((data shr 31).toInt())
        val y get() = java.lang.Float.intBitsToFloat(((data shl 31) shr 31).toInt())

        companion object {
            infix fun Float.dpt(other: Float) = Datapoint(this, other)
        }

        @JvmInline
        value class DatapointArray private constructor(private val array: ULongArray) : Collection<Datapoint> {

            constructor(items: Collection<Datapoint>) : this(items.map { it.data }.toULongArray())

            override val size: Int
                get() = array.size

            override fun contains(element: Datapoint): Boolean {
                return array.contains(element.data)
            }

            override fun containsAll(elements: Collection<Datapoint>): Boolean {
                return array.containsAll(elements.map { it.data })
            }

            override fun isEmpty(): Boolean {
                return array.isEmpty()
            }

            override fun iterator(): Iterator<Datapoint> {
                return object : Iterator<Datapoint> {
                    private var i = 0
                    override fun hasNext() = i < array.size

                    override fun next() = Datapoint(array[i++])

                }
            }
        }
    }
}
