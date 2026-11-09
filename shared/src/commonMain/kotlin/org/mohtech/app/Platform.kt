package org.mohtech.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform