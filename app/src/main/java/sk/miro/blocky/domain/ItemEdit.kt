package sk.miro.blocky.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** Ručná zmena množstva položky – čistá logika (testovateľná bez databázy). */
object ItemEdit {
    data class Result(
        val quantity: Double, val priceCents: Long, val discountCents: Long,
        val origQuantity: Double?, val origPriceCents: Long?, val origDiscountCents: Long?
    )

    fun quantity(
        quantity: Double, priceCents: Long, discountCents: Long,
        origQuantity: Double?, origPriceCents: Long?, origDiscountCents: Long?, newQty: Double
    ): Result {
        val oq = origQuantity ?: quantity
        val op = origPriceCents ?: priceCents
        val od = origDiscountCents ?: discountCents
        if (oq == 0.0 || abs(newQty - oq) < 1e-9) return Result(oq, op, od, null, null, null)   // späť na pôvodné
        val q = newQty.coerceAtLeast(0.0)
        val ratio = q / oq
        return Result(q, (op * ratio).roundToLong(), (od * ratio).roundToLong(), oq, op, od)
    }
}
