package com.lego.mydrawerapplication.custom

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.Transformation
import com.lego.mydrawerapplication.R

class PullToRefreshView(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {

    companion object {
        private const val DRAG_MAX_DISTANCE = 120
        private const val DRAG_RATE = .5f
        private const val DECELERATE_INTERPOLATION_FACTOR = 2f

        const val MAX_OFFSET_ANIMATION_DURATION = 700
        const val RESTORE_ANIMATION_DURATION = 2350

        private const val INVALID_POINTER = -1
    }

    private var mTarget: View? = null
    private var mDecelerateInterpolator: Interpolator
    private var mTouchSlop: Int
    private var mTotalDragDistance: Int
    private var mRefreshView: RefreshView
    private var mCurrentDragPercent: Float = 0.toFloat()
    private var mCurrentOffsetTop: Int = 0
    private var mRefreshing: Boolean = false
    private var mActivePointerId: Int = 0
    private var mIsBeingDragged: Boolean = false
    private var mInitialMotionY: Float = 0.toFloat()
    private var mFrom: Int = 0
    private var mFromDragPercent: Float = 0.toFloat()
    private var mNotify: Boolean = false
    private var mListener: OnRefreshListener? = null

    init {
        mDecelerateInterpolator = DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR)
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val density = context.resources.displayMetrics.density
        mTotalDragDistance = Math.round(DRAG_MAX_DISTANCE.toFloat() * density)

        mRefreshView = RefreshView(getContext())
        mRefreshView.id = R.id.refresher
        mRefreshView.parent = this
        val linLayoutParam = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mRefreshView.layoutParams = linLayoutParam

        addView(mRefreshView, 0)
        setWillNotDraw(false)
        isChildrenDrawingOrderEnabled = true
    }

    fun getTotalDragDistance(): Int {
        return mTotalDragDistance
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var newWidthMeasureSpec = widthMeasureSpec
        var newHeightMeasureSpec = heightMeasureSpec
        super.onMeasure(newWidthMeasureSpec, newHeightMeasureSpec)

        ensureTarget()
        if (mTarget == null)
            return

        newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(measuredWidth - paddingRight - paddingLeft, MeasureSpec.EXACTLY)
        newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(measuredHeight - paddingTop - paddingBottom, MeasureSpec.EXACTLY)
        mTarget?.measure(newWidthMeasureSpec, newHeightMeasureSpec)
    }

    private fun ensureTarget() {
        if (mTarget != null)
            return
        if (childCount > 0) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child !== mRefreshView)
                    mTarget = child
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || canChildScrollUp() || mRefreshing) {
            return false
        }

        val action = ev.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                setTargetOffsetTop(0)
                mActivePointerId = ev.getPointerId(0)
                mIsBeingDragged = false
                val initialMotionY = getMotionEventY(ev, mActivePointerId)
                if (initialMotionY == -1f) {
                    return false
                }
                mInitialMotionY = initialMotionY
            }
            MotionEvent.ACTION_MOVE -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }
                val y = getMotionEventY(ev, mActivePointerId)
                if (y == -1f) {
                    return false
                }
                val yDiff = y - mInitialMotionY
                if (yDiff > mTouchSlop && !mIsBeingDragged) {
                    mIsBeingDragged = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }

        return mIsBeingDragged
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {

        if (!mIsBeingDragged) {
            return super.onTouchEvent(ev)
        }

        val action = ev.actionMasked

        when (action) {
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex < 0) {
                    return false
                }

                val y = ev.getY(pointerIndex)
                val yDiff = y - mInitialMotionY
                val scrollTop = yDiff * DRAG_RATE
                mCurrentDragPercent = scrollTop / mTotalDragDistance
                if (mCurrentDragPercent < 0) {
                    return false
                }
                val boundedDragPercent = Math.min(1f, Math.abs(mCurrentDragPercent))
                val extraOS = Math.abs(scrollTop) - mTotalDragDistance
                val slingshotDist = mTotalDragDistance.toFloat()
                val tensionSlingshotPercent = Math.max(0f,
                        Math.min(extraOS, slingshotDist * 2) / slingshotDist)
                val tensionPercent = (tensionSlingshotPercent / 4 - Math.pow(
                        (tensionSlingshotPercent / 4).toDouble(), 2.0)).toFloat() * 2f
                val extraMove = slingshotDist * tensionPercent / 2
                val targetY = (slingshotDist * boundedDragPercent + extraMove).toInt()

                mRefreshView.setPercent(mCurrentDragPercent)
                setTargetOffsetTop(targetY - mCurrentOffsetTop)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                mActivePointerId = ev.getPointerId(index)
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }
                val pointerIndex = ev.findPointerIndex(mActivePointerId)
                val y = ev.getY(pointerIndex)
                val overScrollTop = (y - mInitialMotionY) * DRAG_RATE
                mIsBeingDragged = false
                if (overScrollTop > mTotalDragDistance) {
                    setRefreshing(true, true)
                } else {
                    mRefreshing = false
                    animateOffsetToPosition(mAnimateToStartPosition)
                }
                mActivePointerId = INVALID_POINTER
                return false
            }
        }

        return true
    }

    private fun animateOffsetToPosition(animation: Animation) {
        mFrom = mCurrentOffsetTop
        mFromDragPercent = mCurrentDragPercent
        val animationDuration = Math.abs(MAX_OFFSET_ANIMATION_DURATION * mFromDragPercent).toLong()

        animation.reset()
        animation.duration = animationDuration
        animation.interpolator = mDecelerateInterpolator
        animation.setAnimationListener(mToStartListener)
        mRefreshView.clearAnimation()
        mRefreshView.startAnimation(animation)
    }

    private fun animateOffsetToCorrectPosition() {
        mFrom = mCurrentOffsetTop
        mFromDragPercent = mCurrentDragPercent

        mAnimateToCorrectPosition.reset()
        mAnimateToCorrectPosition.duration = RESTORE_ANIMATION_DURATION.toLong()
        mAnimateToCorrectPosition.interpolator = mDecelerateInterpolator
        mRefreshView.clearAnimation()
        mRefreshView.startAnimation(mAnimateToCorrectPosition)

        if (mRefreshing) {
            mRefreshView.start()
            if (mNotify) {
                mListener?.onRefresh()
            }
        } else {
            mRefreshView.stop()
            animateOffsetToPosition(mAnimateToStartPosition)
        }
        mTarget?.top?.let { mCurrentOffsetTop = it }
    }

    private val mAnimateToStartPosition = object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            moveToStart(interpolatedTime)
        }
    }

    private val mAnimateToEndPosition = object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            moveToEnd(interpolatedTime)
        }
    }

    private val mAnimateToCorrectPosition = object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val targetTop: Int
            val endTarget = mTotalDragDistance
            targetTop = mFrom + ((endTarget - mFrom) * interpolatedTime).toInt()
            val offset = mTarget?.top?.let { targetTop - it }

            mCurrentDragPercent = mFromDragPercent - (mFromDragPercent - 1.0f) * interpolatedTime
            mRefreshView.setPercent(mCurrentDragPercent)

            offset?.let { setTargetOffsetTop(it) }
        }
    }

    private fun moveToStart(interpolatedTime: Float) {
        val targetTop = mFrom - (mFrom * interpolatedTime).toInt()
        val targetPercent = mFromDragPercent * (1.0f - interpolatedTime)
        val offset = mTarget?.top?.let { targetTop - it }

        mCurrentDragPercent = targetPercent
        mRefreshView.setPercent(mCurrentDragPercent)
        offset?.let { setTargetOffsetTop(it) }
    }

    private fun moveToEnd(interpolatedTime: Float) {
        val targetTop = mFrom - (mFrom * interpolatedTime).toInt()
        val targetPercent = mFromDragPercent * (1.0f + interpolatedTime)
        val offset = mTarget?.top?.let { targetTop - it }

        mCurrentDragPercent = targetPercent
        mRefreshView.setPercent(mCurrentDragPercent)
        offset?.let { setTargetOffsetTop(it) }
    }

    fun setRefreshing(refreshing: Boolean) {
        if (mRefreshing != refreshing) {
            setRefreshing(refreshing, false /* notify */)
        }
    }

    private fun setRefreshing(refreshing: Boolean, notify: Boolean) {
        if (mRefreshing != refreshing) {
            mNotify = notify
            ensureTarget()
            mRefreshing = refreshing
            if (mRefreshing) {
                mRefreshView.setPercent(1f)
                animateOffsetToCorrectPosition()
            } else {
                mRefreshView.setEndOfRefreshing(true)
                animateOffsetToPosition(mAnimateToEndPosition)
            }
        }
    }

    private val mToStartListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {}

        override fun onAnimationRepeat(animation: Animation) {}

        override fun onAnimationEnd(animation: Animation) {
            mRefreshView.stop()
            mTarget?.top?.let { mCurrentOffsetTop = it }
        }
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mActivePointerId = ev.getPointerId(newPointerIndex)
        }
    }

    private fun getMotionEventY(ev: MotionEvent, activePointerId: Int): Float {
        val index = ev.findPointerIndex(activePointerId)
        return if (index < 0) {
            -1f
        } else ev.getY(index)
    }

    private fun setTargetOffsetTop(offset: Int) {
        mTarget?.offsetTopAndBottom(offset)
        mRefreshView.offsetTopAndBottom(offset)
        mTarget?.top?.let { mCurrentOffsetTop = it }
    }

    private fun canChildScrollUp(): Boolean {
        return mTarget?.canScrollVertically(-1) ?: false
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        ensureTarget()
        if (mTarget == null)
            return

        val height = measuredHeight
        val width = measuredWidth
        val left = paddingLeft
        val top = paddingTop
        val right = paddingRight
        val bottom = paddingBottom

        mTarget?.layout(left, top + mCurrentOffsetTop, left + width - right, top + height - bottom + mCurrentOffsetTop)
        mRefreshView.layout(left, top, left + width - right, top + height - bottom)
    }

    fun setOnRefreshListener(listener: OnRefreshListener) {
        mListener = listener
    }

    interface OnRefreshListener {
        fun onRefresh()
    }

}