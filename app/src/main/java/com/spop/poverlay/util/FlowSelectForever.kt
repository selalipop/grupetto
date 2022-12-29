package com.spop.poverlay.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.select

suspend fun <T> CoroutineScope.selectForever(selectBuilder: SelectBuilder<T>.()->Unit){
    while (isActive){
        select(selectBuilder)
    }
}