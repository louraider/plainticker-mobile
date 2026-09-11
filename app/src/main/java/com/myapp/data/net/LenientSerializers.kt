package com.myapp.data.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Jupiter sends u64 amounts and some ratios as JSON strings ("5000000", "0.0000535") and
 * has moved fields between number and string across versions. These accept either form so
 * a typing change upstream degrades to nothing instead of a parse failure.
 */
object LenientLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val json = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val text = (json.decodeJsonElement() as? JsonPrimitive)?.contentOrNull?.trim()
            ?: throw SerializationException("expected a number or a numeric string")
        return text.toLongOrNull()
            ?: text.toDoubleOrNull()?.toLong()
            ?: throw SerializationException("not a number: $text")
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val json = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val text = (json.decodeJsonElement() as? JsonPrimitive)?.contentOrNull?.trim()
            ?: throw SerializationException("expected a number or a numeric string")
        return text.toDoubleOrNull() ?: throw SerializationException("not a number: $text")
    }

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}
