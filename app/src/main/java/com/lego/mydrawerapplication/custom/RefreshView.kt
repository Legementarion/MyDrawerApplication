package com.lego.mydrawerapplication.custom

import android.content.Context
import android.view.animation.Animation
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Transformation
import com.lego.mydrawerapplication.R

class RefreshView @JvmOverloads constructor(context: Context, val parent: PullToRefreshView) : View(context), Animatable {

    companion object {
        private const val ANIMATION_DURATION = 1000

        private val ACCELERATE_DECELERATE_INTERPOLATOR = AccelerateDecelerateInterpolator()

        // Multiply with this animation interpolator time
        private const val LOADING_ANIMATION_COEFFICIENT = 80
        private const val SLOW_DOWN_ANIMATION_COEFFICIENT = 6
    }

    private var mAnimation: Animation? = null

    private var mTop: Int = 0
    private var mScreenWidth: Int = 0

    private var mPercent = 0.0f

    private var isRefreshing = false
    private var mLoadingAnimationTime: Float = 0.toFloat()
    private var mLastAnimationTime: Float = 0.toFloat()

    private var mEndOfRefreshing: Boolean = false
    private var view: View? = null

    private enum class AnimationPart {
        FIRST,
        SECOND,
        THIRD,
        FOURTH
    }

    init {
        val layoutInflater = LayoutInflater.from(parent.context)
        view = layoutInflater.inflate(R.layout.view_refresh, parent, false)

        initiateDimens()
        setupAnimations()
    }

    private fun initiateDimens() {
        mScreenWidth = context.resources.displayMetrics.widthPixels
        mTop = -parent.getTotalDragDistance()
    }

    override fun offsetTopAndBottom(offset: Int) {
        mTop += offset
    }


    /**
     * Our animation depend on type of current work of refreshing.
     * We should to do different things when it's end of refreshing
     *
     * @param endOfRefreshing - we will check current state of refresh with this
     */
    fun setEndOfRefreshing(endOfRefreshing: Boolean) {
        mEndOfRefreshing = endOfRefreshing
    }

    /**
     * We need a special value for different part of animation
     *
     * @param part - needed part
     * @return - value for needed part
     */
    private fun getAnimationPartValue(part: AnimationPart): Float {
        return when (part) {
            RefreshView.AnimationPart.FIRST -> {
                mLoadingAnimationTime
            }
            RefreshView.AnimationPart.SECOND -> {
                getAnimationTimePart(AnimationPart.FOURTH) - (mLoadingAnimationTime - getAnimationTimePart(AnimationPart.FOURTH))
            }
            RefreshView.AnimationPart.THIRD -> {
                mLoadingAnimationTime - getAnimationTimePart(AnimationPart.SECOND)
            }
            RefreshView.AnimationPart.FOURTH -> {
                getAnimationTimePart(AnimationPart.THIRD) - (mLoadingAnimationTime - getAnimationTimePart(AnimationPart.FOURTH))
            }
        }
    }

    /**
     * On drawing we should check current part of animation
     *
     * @param part - needed part of animation
     * @return - return true if current part
     */
    private fun checkCurrentAnimationPart(part: AnimationPart): Boolean {
        return when (part) {
            RefreshView.AnimationPart.FIRST -> {
                mLoadingAnimationTime < getAnimationTimePart(AnimationPart.FOURTH)
            }
            RefreshView.AnimationPart.SECOND, RefreshView.AnimationPart.THIRD -> {
                mLoadingAnimationTime < getAnimationTimePart(part)
            }
            RefreshView.AnimationPart.FOURTH -> {
                mLoadingAnimationTime > getAnimationTimePart(AnimationPart.THIRD)
            }
        }
    }

    /**
     * Get part of animation duration
     *
     * @param part - needed part of time
     * @return - interval of time
     */
    private fun getAnimationTimePart(part: AnimationPart): Int {
        return when (part) {
            RefreshView.AnimationPart.SECOND -> {
                LOADING_ANIMATION_COEFFICIENT / 2
            }
            RefreshView.AnimationPart.THIRD -> {
                getAnimationTimePart(AnimationPart.FOURTH) * 3
            }
            RefreshView.AnimationPart.FOURTH -> {
                LOADING_ANIMATION_COEFFICIENT / 4
            }
            else -> 0
        }
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
        parent.startAnimation(mAnimation)
        mLastAnimationTime = 0f
    }

    override fun stop() {
        parent.clearAnimation()
        isRefreshing = false
        mEndOfRefreshing = false
        resetOriginals()
    }

    private fun setupAnimations() {
        mAnimation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                setLoadingAnimationTime(interpolatedTime)
            }
        }
        mAnimation?.repeatCount = Animation.INFINITE
        mAnimation?.repeatMode = Animation.REVERSE
        mAnimation?.interpolator = ACCELERATE_DECELERATE_INTERPOLATOR
        mAnimation?.duration = ANIMATION_DURATION.toLong()
    }

    private fun setLoadingAnimationTime(loadingAnimationTime: Float) {
        /**SLOW DOWN ANIMATION IN [.SLOW_DOWN_ANIMATION_COEFFICIENT] time  */
        mLoadingAnimationTime = LOADING_ANIMATION_COEFFICIENT * (loadingAnimationTime / SLOW_DOWN_ANIMATION_COEFFICIENT)
    }

}