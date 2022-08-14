package com.github.capntrips.kernelflasher.common.types.room.updates

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateSerializer : KSerializer<Date> {
    override val descriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeString(formatter.format(value))
    override fun deserialize(decoder: Decoder): Date = formatter.parse(decoder.decodeString())!!
}

object UpdateSerializer : JsonTransformingSerializer<Update>(Update.serializer()) {
    override fun transformSerialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return buildJsonObject {
            put("kernel", buildJsonObject {
                put("name", element["kernelName"]!!)
                put("version", element["kernelVersion"]!!)
                put("link", element["kernelLink"]!!)
                put("changelog_url", element["kernelChangelogUrl"]!!)
                put("date", element["kernelDate"]!!)
                put("sha1", element["kernelSha1"]!!)
            })
            if (element["supportLink"] != null) {
                put("support", buildJsonObject {
                    put("link", element["supportLink"]!!)
                })
            }
        }
    }
    override fun transformDeserialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        val kernel = element["kernel"]
        val support = element["support"]
        require(kernel is JsonObject)
        require(support is JsonObject?)
        return buildJsonObject {
            put("kernelName", kernel["name"]!!)
            put("kernelVersion", kernel["version"]!!)
            put("kernelLink", kernel["link"]!!)
            put("kernelChangelogUrl", kernel["changelog_url"]!!)
            put("kernelDate", kernel["date"]!!)
            put("kernelSha1", kernel["sha1"]!!)
            if (support != null && support["link"] != null) {
                put("supportLink", support["link"]!!)
            }
        }
    }
}

@Entity
@Serializable
data class Update(
    @PrimaryKey
    @Transient
    val id: Int? = null,
    @ColumnInfo(name = "update_uri")
    @Transient
    var updateUri: String? = null,
    @ColumnInfo(name = "kernel_name")
    var kernelName: String,
    @ColumnInfo(name = "kernel_version")
    var kernelVersion: String,
    @ColumnInfo(name = "kernel_link")
    var kernelLink: String,
    @ColumnInfo(name = "kernel_changelog_url")
    var kernelChangelogUrl: String,
    @ColumnInfo(name = "kernel_date")
    @Serializable(DateSerializer::class)
    var kernelDate: Date,
    @ColumnInfo(name = "kernel_sha1")
    var kernelSha1: String,
    @ColumnInfo(name = "support_link")
    var supportLink: String?,
    @ColumnInfo(name = "last_updated")
    @Transient
    var lastUpdated: Date? = null,
)
