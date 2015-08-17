package com.lightinthebox.android.view;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;

import com.lightinthebox.android.R;

/**
 * ListView,下拉刷新、上拉加载更多，github公共控件
 * 
 * @author lqh 2013-03-18
 */
public class IOSListView extends ListView implements OnScrollListener {

    private float mLastY = -1; 

    /** 用于下拉后回滚. */
    private Scroller mScroller;

    /** 滚动. */
    private OnScrollListener mScrollListener;

    /** 调用者实现该接口完成上拉及下拉功能回调. */
    private IOSListViewListener mListViewListener;

    /** header，显示下拉刷新. */
    private IOSListViewHeader mHeaderView;

    /** 用来计算header的高度，onTouch中会依赖于该高度判断是否触发下拉刷新. */
    private RelativeLayout mHeaderViewContent;

    private int mHeaderViewHeight;

    /** 类似于IOS，显示最后一次更新时间. */
    private TextView mHeaderTimeView;

    /** 是否启用下拉刷新功能，默认开启. */
    private boolean mEnablePullRefresh = true;

    /** 当前刷新状态. */
    private boolean mPullRefreshing = false;

    /** footer view，用于显示上拉加载更多. */
    private IOSListViewFooter mFooterView;

    /** 是否启用上拉加载更多功能，默认开启. */
    private boolean mEnablePullLoad = true;

    /** 当前加载更多状态. */
    private boolean mPullLoading;

    /** 用于控制只能添加一个footer view. */
    private boolean mIsFooterReady = false;

    /** 是否预加载更多，启用后，不用滑动到底部自动会加载更多. */
    private boolean mIsPreLoad = true;

    /** 用于检测是否处于底部. */
    private int mTotalItemCount;

    /** 用于解决广告条水平滚动与ListView竖直滚动冲突. */
    private View mInterceptView;

    private int mScrollBack;

    private final static int SCROLLBACK_HEADER = 0;

    private final static int SCROLLBACK_FOOTER = 1;

    private final static int SCROLL_DURATION = 400;

    /** 触发上拉加载更多的阀值. */
    private final static int PULL_LOAD_MORE_DELTA = 50;

    /** 通过这个参数实现类似IOS的阻力效果. */
    private final static float OFFSET_RADIO = 2.2f;

    public IOSListView(Context context) {
        super(context);
        initWithContext(context);
    }

    public IOSListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initWithContext(context);
    }

    public IOSListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initWithContext(context);
    }

    private void initWithContext(Context context) {

        mScroller = new Scroller(context, new DecelerateInterpolator());

        super.setOnScrollListener(this);

        // header view，下拉刷新
        mHeaderView = new IOSListViewHeader(context);
        mHeaderViewContent = (RelativeLayout) mHeaderView
                .findViewById(R.id.ios_listview_header_content);
        mHeaderTimeView = (TextView) mHeaderView
                .findViewById(R.id.ios_listview_header_time);
        addHeaderView(mHeaderView);
        mHeaderViewContent.setVisibility(mEnablePullRefresh ? View.VISIBLE
                : View.INVISIBLE);

        // footer vier，下拉加载更多
        mFooterView = new IOSListViewFooter(context);
        mFooterView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startLoadMore();
            }
        });
        if (!mEnablePullLoad) {
            mFooterView.hide();
        }

        // 计算header view的高度
        mHeaderView.getViewTreeObserver().addOnGlobalLayoutListener(
                new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mHeaderViewHeight = mHeaderViewContent.getHeight();
                        getViewTreeObserver()
                                .removeGlobalOnLayoutListener(this);
                    }
                });
    }

    @Override
    public void setAdapter(ListAdapter adapter) {

        // 解决多次调用setAdapter方法，设置多个footer view问题
        if (!mIsFooterReady) {
            mIsFooterReady = true;
            addFooterView(mFooterView);
        }

        super.setAdapter(adapter);
    }

    /**
     * 启用或禁用下拉刷新功能
     * 
     * @param enable
     */
    public void setPullRefreshEnable(boolean enable) {
        mEnablePullRefresh = enable;
        mHeaderViewContent.setVisibility(mEnablePullRefresh ? View.VISIBLE
                : View.INVISIBLE);
    }

    /**
     * 是否启用预加载
     * 
     * @param enable
     */
    public void setPreLoadEnable(boolean enable) {
        this.mIsPreLoad = enable;
    }

    /**
     * 启用或禁用上拉加载更多功能
     * 
     * @param enable
     */
    public void setPullLoadEnable(boolean enable) {

        mEnablePullLoad = enable;

        if (!mEnablePullLoad) {
            mFooterView.hide();
        }

        else {
            mPullLoading = false;
            mFooterView.show();
            mFooterView.setState(IOSListViewFooter.STATE_NORMAL);
        }
    }

    /**
     * 刷新后调用，复位header view
     */
    public void stopRefresh() {
        if (mPullRefreshing) {
            mPullRefreshing = false;
            resetHeaderHeight();
        }
    }

    /**
     * 下拉加载更多后调用，复位footer view
     */
    public void stopLoadMore() {
        if (mPullLoading) {
            mPullLoading = false;
            mFooterView.setState(IOSListViewFooter.STATE_NORMAL);
            mFooterView.setVisibility(View.GONE);
        }
    }

    /**
     * 设置最后一次更新时间
     * 
     * @param time
     */
    public void setRefreshTime(String time) {
        mHeaderTimeView.setText(time);
        findViewById(R.id.ios_listview_header_timeview).setVisibility(
                View.VISIBLE);
    }

    /**
     * 没有更多数据时，隐藏上拉刷新显示区域及调用逻辑
     */
    public void setNoMoreData() {
        setPullLoadEnable(false);
    }

    /**
     * 用户下推的过程中，改变header view的可视高度
     * 
     * @param delta
     */
    private void updateHeaderHeight(float delta) {

        mHeaderView.setVisiableHeight((int) delta
                + mHeaderView.getVisiableHeight());

        // 未处于刷新状态，更新箭头
        if (mEnablePullRefresh && !mPullRefreshing) {
            if (mHeaderView.getVisiableHeight() > mHeaderViewHeight) {
                mHeaderView.setState(IOSListViewHeader.STATE_READY);
            } else {
                mHeaderView.setState(IOSListViewHeader.STATE_NORMAL);
            }
        }

        /**
         * 用户下拉回推时，不断修改header的高度，但这时候滚动条指示器的位置还是按老的高度计算的，
         * 需要强制调用一下setSelection(0)将ListView滚动到顶部。
         */
        setSelection(0);
    }

    /**
     * 复位header view高度
     */
    private void resetHeaderHeight() {

        int height = mHeaderView.getVisiableHeight();
        if (height == 0) {
            return;
        }

        // 处于刷新状态或hader view没有完全显示什么也不做
        if (mPullRefreshing && height <= mHeaderViewHeight) {
            return;
        }

        // 回滚header view
        int finalHeight = 0;
        if (mPullRefreshing && height > mHeaderViewHeight) {
            finalHeight = mHeaderViewHeight;
        }

        mScrollBack = SCROLLBACK_HEADER;
        mScroller.startScroll(0, height, 0, finalHeight - height,
                SCROLL_DURATION);

        invalidate();
    }

    /**
     * 更新footer view高度
     * 
     * @param delta
     */
    private void updateFooterHeight(float delta) {
        int height = mFooterView.getBottomMargin() + (int) delta;
        if (mEnablePullLoad && !mPullLoading) {
            if (height > PULL_LOAD_MORE_DELTA) {
                mFooterView.setState(IOSListViewFooter.STATE_READY);
            } else {
                mFooterView.setState(IOSListViewFooter.STATE_NORMAL);
            }
        }
        mFooterView.setBottomMargin(height);
    }

    /**
     * 复位footer view高度
     */
    private void resetFooterHeight() {
        int bottomMargin = mFooterView.getBottomMargin();
        if (bottomMargin > 0) {
            mScrollBack = SCROLLBACK_FOOTER;
            mScroller.startScroll(0, bottomMargin, 0, -bottomMargin,
                    SCROLL_DURATION);
            invalidate();
        }
    }

    /**
     * 回调加载更多接口
     */
    private void startLoadMore() {
        mPullLoading = true;
        mFooterView.setState(IOSListViewFooter.STATE_LOADING);

        if (mFooterView.getVisibility() == View.VISIBLE) {

        } else {
            Animation animation = AnimationUtils.loadAnimation(getContext(),
                    R.anim.rotate);
            mFooterView.findViewById(R.id.ios_listview_footer_progressbar)
                    .startAnimation(animation);
            mFooterView.setVisibility(View.VISIBLE);
        }
        if (mListViewListener != null) {
            mListViewListener.onLoadMore();
        }
    }

    /**
     * 用于解决广告的水平滚动与ListView竖直滚动的冲突问题
     */
    public void setIntereptView(View interceptView) {
        this.mInterceptView = interceptView;
    }

    /**
     * InterceptView上的事件不传递给ListView
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        if (mInterceptView != null) {
            Rect rect = new Rect();
            mInterceptView.getHitRect(rect);

            if ((rect.contains((int) ev.getX(), (int) ev.getY()))) {
                return false;
            }
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        if (mLastY == -1) {
            mLastY = ev.getRawY();
        }

        switch (ev.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mLastY = ev.getRawY();
            break;
        case MotionEvent.ACTION_MOVE:
            final float deltaY = ev.getRawY() - mLastY;
            mLastY = ev.getRawY();

            if (mEnablePullRefresh && getFirstVisiblePosition() == 0
                    && (mHeaderView.getVisiableHeight() > 0 || deltaY > 0)) {
                updateHeaderHeight(deltaY / OFFSET_RADIO);
            }

            else if (mEnablePullLoad
                    && getLastVisiblePosition() == mTotalItemCount - 1
                    && (mFooterView.getBottomMargin() > 0 || deltaY < 0)
                    && getFirstVisiblePosition() > 0) {
                updateFooterHeight(-deltaY / OFFSET_RADIO);
            }
            break;
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:

            // reset
            mLastY = -1;

            // 下拉刷新
            if (getFirstVisiblePosition() == 0) {
                if (mEnablePullRefresh
                        && mHeaderView.getVisiableHeight() > mHeaderViewHeight) {
                    mPullRefreshing = true;
                    mHeaderView.setState(IOSListViewHeader.STATE_REFRESHING);
                    if (mListViewListener != null) {
                        mListViewListener.onRefresh();
                    }
                }
                resetHeaderHeight();
            }

            // 上拉加载更多
            else if (getLastVisiblePosition() == mTotalItemCount - 1
                    && getFirstVisiblePosition() > 0) {
                if (mEnablePullLoad
                        && mFooterView.getBottomMargin() > PULL_LOAD_MORE_DELTA) {
                    startLoadMore();
                }
                resetFooterHeight();
            }

            break;
        }

        return super.onTouchEvent(ev);
    }

    @Override
    public void computeScroll() {

        if (mScroller.computeScrollOffset()) {
            if (mScrollBack == SCROLLBACK_HEADER) {
                mHeaderView.setVisiableHeight(mScroller.getCurrY());
            } else {
                mFooterView.setBottomMargin(mScroller.getCurrY());
            }
            postInvalidate();
        }

        super.computeScroll();
    }

    @Override
    public void setOnScrollListener(OnScrollListener l) {
        mScrollListener = l;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (mScrollListener != null) {
            mScrollListener.onScrollStateChanged(view, scrollState);
        }

        // 滑动时预加载下一屏
        int lastIndex = getLastVisiblePosition();
        if (lastIndex > mTotalItemCount - 2 && getFirstVisiblePosition() > 0
                && !mPullLoading && mIsPreLoad && mEnablePullLoad) {
            startLoadMore();
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {

        mTotalItemCount = totalItemCount;
        if (mScrollListener != null) {
            mScrollListener.onScroll(view, firstVisibleItem, visibleItemCount,
                    totalItemCount);
        }

        // 如果当前少于一屏展示的话，则不显示加载更多
        if (mFooterView != null && mEnablePullLoad) {
            if (firstVisibleItem == 0) {
                mFooterView.hide();
            } else {
                mFooterView.show();
            }
        }
    }

    public void setIOSListViewListener(IOSListViewListener l) {
        mListViewListener = l;
    }

    /**
     * 实现该接口完成下拉刷新及上拉加载更多
     */
    public interface IOSListViewListener {

        /**
         * 刷新
         */
        public void onRefresh();

        /**
         * 加载更多
         */
        public void onLoadMore();
    }

    /**
     * 头部显示下拉刷新
     */
    public class IOSListViewHeader extends LinearLayout {

        private LinearLayout mContainer;

        private ImageView mArrowImageView;

        private ProgressBar mProgressBar;

        private TextView mHintTextView;

        private int mState = STATE_NORMAL;

        private Animation mRotateUpAnim;

        private Animation mRotateDownAnim;

        private final int ROTATE_ANIM_DURATION = 180;

        public final static int STATE_NORMAL = 0;

        public final static int STATE_READY = 1;

        public final static int STATE_REFRESHING = 2;

        public IOSListViewHeader(Context context) {
            super(context);
            initView(context);
        }

        /**
         * @param context
         * @param attrs
         */
        public IOSListViewHeader(Context context, AttributeSet attrs) {
            super(context, attrs);
            initView(context);
        }

        /**
         * 初始情况，设置下拉刷新view高度为0
         * 
         * @param context
         */
        private void initView(Context context) {

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, 0);
            mContainer = (LinearLayout) LayoutInflater.from(context).inflate(
                    R.layout.ios_listview_header, null);
            addView(mContainer, lp);
            setGravity(Gravity.BOTTOM);

            mArrowImageView = (ImageView) findViewById(R.id.ios_listview_header_arrow);
            mHintTextView = (TextView) findViewById(R.id.ios_listview_header_hint_textview);
            mProgressBar = (ProgressBar) findViewById(R.id.ios_listview_header_progressbar);

            mRotateUpAnim = new RotateAnimation(0.0f, -180.0f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            mRotateUpAnim.setDuration(ROTATE_ANIM_DURATION);
            mRotateUpAnim.setFillAfter(true);
            mRotateDownAnim = new RotateAnimation(-180.0f, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            mRotateDownAnim.setDuration(ROTATE_ANIM_DURATION);
            mRotateDownAnim.setFillAfter(true);
        }

        /**
         * 更新状态显示
         * 
         * @param state
         */
        public void setState(int state) {
            if (state == mState)
                return;

            // 显示进度
            if (state == STATE_REFRESHING) {
                mArrowImageView.clearAnimation();
                mArrowImageView.setVisibility(View.INVISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
            }

            // 显示箭头图片
            else {
                mArrowImageView.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.INVISIBLE);
            }

            switch (state) {
            case STATE_NORMAL:
                if (mState == STATE_READY) {
                    mArrowImageView.startAnimation(mRotateDownAnim);
                }
                if (mState == STATE_REFRESHING) {
                    mArrowImageView.clearAnimation();
                }
                mHintTextView.setText("下拉刷新");
                break;
            case STATE_READY:
                if (mState != STATE_READY) {
                    mArrowImageView.clearAnimation();
                    mArrowImageView.startAnimation(mRotateUpAnim);
                    mHintTextView.setText("松开刷新数据");
                }
                break;
            case STATE_REFRESHING:
                mHintTextView.setText("");
                break;
            default:
            }

            mState = state;
        }

        public void setVisiableHeight(int height) {
            if (height < 0)
                height = 0;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mContainer
                    .getLayoutParams();
            lp.height = height;
            mContainer.setLayoutParams(lp);
        }

        public int getVisiableHeight() {
            return mContainer.getHeight();
        }
    }

    /**
     * 上拉加载更多
     */
    public class IOSListViewFooter extends LinearLayout {

        public final static int STATE_NORMAL = 0;

        public final static int STATE_READY = 1;

        public final static int STATE_LOADING = 2;

        private Context mContext;

        private View mContentView;

        private View mProgressBar;

        private TextView mHintView;

        public IOSListViewFooter(Context context) {
            super(context);
            initView(context);
        }

        public IOSListViewFooter(Context context, AttributeSet attrs) {
            super(context, attrs);
            initView(context);
        }

        private void initView(Context context) {
            mContext = context;
            LinearLayout moreView = (LinearLayout) LayoutInflater
                    .from(mContext).inflate(R.layout.ios_listview_footer, null);
            addView(moreView);
            moreView.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

            mContentView = moreView
                    .findViewById(R.id.ios_listview_footer_content);
            mProgressBar = moreView
                    .findViewById(R.id.ios_listview_footer_progressbar);
            mHintView = (TextView) moreView
                    .findViewById(R.id.ios_listview_footer_hint_textview);
        }

        /**
         * 更新状态显示
         * 
         * @param state
         */
        public void setState(int state) {
            mHintView.setVisibility(View.INVISIBLE);
            mProgressBar.setVisibility(View.INVISIBLE);
            mHintView.setVisibility(View.INVISIBLE);
            if (state == STATE_READY) {
                mHintView.setVisibility(View.VISIBLE);
                // mHintView.setText("松开载入更多");
            } else if (state == STATE_LOADING) {
                mProgressBar.setVisibility(View.VISIBLE);
            } else {
                mHintView.setVisibility(View.VISIBLE);
                // mHintView.setText("点击查看更多");
            }
        }

        public void setBottomMargin(int height) {
            if (height < 0)
                return;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mContentView
                    .getLayoutParams();
            lp.bottomMargin = height;
            mContentView.setLayoutParams(lp);
        }

        public int getBottomMargin() {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mContentView
                    .getLayoutParams();
            return lp.bottomMargin;
        }

        /**
         * normal status
         */
        public void normal() {
            mHintView.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.GONE);
        }

        /**
         * loading status
         */
        public void loading() {
            mHintView.setVisibility(View.GONE);
            mProgressBar.setVisibility(View.VISIBLE);
        }

        /**
         * 当上拉加载更多不可用时，隐藏加载更多组件
         */
        public void hide() {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mContentView
                    .getLayoutParams();
            lp.height = 0;
            mContentView.setLayoutParams(lp);
        }

        public void show() {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mContentView
                    .getLayoutParams();
            lp.height = LayoutParams.WRAP_CONTENT;
            mContentView.setLayoutParams(lp);
        }
    }
}
