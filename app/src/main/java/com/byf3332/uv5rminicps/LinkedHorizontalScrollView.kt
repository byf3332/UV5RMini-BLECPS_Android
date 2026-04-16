package com.byf3332.uv5rminicps

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView

class LinkedHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {
    private var peer: HorizontalScrollView? = null
    private var internalSync = false

    fun setPeer(other: HorizontalScrollView) {
        peer = other
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (internalSync) return
        val target = peer ?: return
        if (target.scrollX == l && target.scrollY == t) return
        internalSync = true
        target.scrollTo(l, t)
        internalSync = false
    }
}
