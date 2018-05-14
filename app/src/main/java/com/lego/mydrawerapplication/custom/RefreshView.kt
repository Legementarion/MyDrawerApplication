package com.lego.mydrawerapplication.custom

import android.content.Context
import android.graphics.drawable.Animatable
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import com.lego.mydrawerapplication.R

class RefreshView @JvmOverloads constructor(context: Context,
                                            attributesSet: AttributeSet? = null,
                                            defStyleAttr: Int = 0) : ConstraintLayout(context, attributesSet, defStyleAttr), Animatable {

    companion object {
        private const val ANIMATION_DURATION = 1000

        private val ACCELERATE_DECELERATE_INTERPOLATOR = AccelerateDecelerateInterpolator()

        // Multiply with this animation interpolator time
        private const val LOADING_ANIMATION_COEFFICIENT = 80
        private const val SLOW_DOWN_ANIMATION_COEFFICIENT = 6
    }

    private var mAnimation: Animation? = null

    var mTop: Int = 0

    private var mPercent = 0.0f

    private var isRefreshing = false
    private var mLoadingAnimationTime: Float = 0.toFloat()
    private var mLastAnimationTime: Float = 0.toFloat()

    var parent: PullToRefreshView? = null


    init {
        inflate(context, R.layout.view_refresh, this)

        setupAnimations()
    }

    fun initiateDimens() {
        parent?.getTotalDragDistance()?.let { mTop -= it }
    }

    override fun offsetTopAndBottom(offset: Int) {
        mTop += offset
    }

    fun setPercent(percent: Float) {
        mPercent = percent
    }

    private fun resetOriginals() {
        setPercent(0f)
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun start() {
        mAnimation?.reset()
        isRefreshing = true
        parent?.startAnimation(mAnimation)
        mLastAnimationTime = 0f
    }

    override fun stop() {
        parent?.clearAnimation()
        isRefreshing = false
        resetOriginals()
    }

    private fun setupAnimations() {
        mAnimation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                setLoadingAnimationTime(interpolatedTime)
            }
        }
        mAnimation?.repeatCount = 0
        mAnimation?.repeatMode = Animation.REVERSE
        mAnimation?.interpolator = ACCELERATE_DECELERATE_INTERPOLATOR
        mAnimation?.duration = ANIMATION_DURATION.toLong()
    }

    private fun setLoadingAnimationTime(loadingAnimationTime: Float) {
        /**SLOW DOWN ANIMATION IN [.SLOW_DOWN_ANIMATION_COEFFICIENT] time  */
        mLoadingAnimationTime = LOADING_ANIMATION_COEFFICIENT * (loadingAnimationTime / SLOW_DOWN_ANIMATION_COEFFICIENT)
        invalidate()
    }

}