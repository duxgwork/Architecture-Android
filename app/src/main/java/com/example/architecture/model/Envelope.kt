package com.example.architecture.model

import org.json.JSONObject

/**
 * Topic 枚举：定义所有消息主题
 */
enum class Topic(val value: String) {
    CHAT("chat"),
    NOTIFICATION("notification"),
    COLLABORATION("collaboration"),
    PRESENCE("presence");

    companion object {
        fun fromString(value: String): Topic? = values().find { it.value == value }
    }
}

/**
 * 信封模型：统一包装所有 WebSocket 消息
 * 底层是一条连接，但业务侧通过 topic 路由到各自的 Stream
 */
data class Envelope(
    val topic: Topic,
    val payload: JSONObject
) {
    fun toJson(): String = JSONObject().apply {
        put("topic", topic.value)
        put("payload", payload)
    }.toString()

    companion object {
        fun fromJson(json: String): Envelope? {
            return try {
                val obj = JSONObject(json)
                val topicStr = obj.getString("topic")
                val topic = Topic.fromString(topicStr) ?: return null
                val payload = obj.optJSONObject("payload") ?: JSONObject()
                Envelope(topic, payload)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 聊天消息模型
 */
data class ChatMessage(
    val from: String,
    val content: String,
    val timestamp: Long
) {
    companion object {
        fun fromPayload(payload: JSONObject): ChatMessage {
            return ChatMessage(
                from = payload.optString("from", "unknown"),
                content = payload.optString("content", ""),
                timestamp = payload.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

/**
 * 通知消息模型
 */
data class NotificationMessage(
    val title: String,
    val body: String,
    val type: String
) {
    companion object {
        fun fromPayload(payload: JSONObject): NotificationMessage {
            return NotificationMessage(
                title = payload.optString("title", ""),
                body = payload.optString("body", ""),
                type = payload.optString("type", "info")
            )
        }
    }
}

/**
 * 协同编辑事件模型
 */
data class CollabEvent(
    val userId: String,
    val action: String,
    val docId: String
) {
    companion object {
        fun fromPayload(payload: JSONObject): CollabEvent {
            return CollabEvent(
                userId = payload.optString("userId", ""),
                action = payload.optString("action", ""),
                docId = payload.optString("docId", "")
            )
        }
    }
}

/**
 * 在线状态模型
 */
data class PresenceUpdate(
    val userId: String,
    val status: String
) {
    companion object {
        fun fromPayload(payload: JSONObject): PresenceUpdate {
            return PresenceUpdate(
                userId = payload.optString("userId", ""),
                status = payload.optString("status", "offline")
            )
        }
    }
}
