package mr.x.md3lb;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.MaskableFrameLayout;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Md3lbt
 *
 * Material Design 3 JSON 轮播图控件
 *
 * 功能：
 * 1. 直接继承 RecyclerView
 * 2. 支持 JSON 字符串加载
 * 3. 支持 raw JSON 文件加载
 * 4. 支持字符串数组和对象数组
 * 5. 网络图片异步加载
 * 6. LruCache 图片缓存
 * 7. 多线程图片加载
 * 8. MD3 Carousel 遮罩效果
 * 9. 圆角图片
 * 10. 自动循环播放
 * 11. 用户操作自动暂停
 *
 * Author: Md3lbt
 */
public class Md3lbt extends RecyclerView {

    // ==============================
    // 基础配置
    // ==============================

    private static final long AUTO_PLAY_INTERVAL = 3000;

    private static final int ITEM_WIDTH_DP = 220;

    private static final int CORNER_RADIUS_DP = 16;

    private static final int ITEM_MARGIN_DP = 8;

    // ==============================
    // 自动播放
    // ==============================

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private Runnable autoPlayRunnable;

    private boolean autoPlayEnabled = true;

    private int currentPosition = 0;

    // ==============================
    // 图片加载
    // ==============================

    private final ExecutorService imageExecutor =
            Executors.newFixedThreadPool(3);

    private final LruCache<String, Bitmap> imageCache;

    // ==============================
    // 轮播组件
    // ==============================

    private CarouselSnapHelper snapHelper;

    private CarouselAdapter adapter;

    private boolean released = false;

    // ==============================
    // 构造方法
    // ==============================

    public Md3lbt(Context context) {
        super(context);
        imageCache = createImageCache();
        init();
    }

    public Md3lbt(Context context, AttributeSet attrs) {
        super(context, attrs);
        imageCache = createImageCache();
        init();
    }

    public Md3lbt(
            Context context,
            AttributeSet attrs,
            int defStyleAttr) {

        super(context, attrs, defStyleAttr);
        imageCache = createImageCache();
        init();
    }

    /**
     * 创建图片缓存
     */
    private LruCache<String, Bitmap> createImageCache() {

        int maxMemory = (int) (
                Runtime.getRuntime().maxMemory() / 1024
        );

        int cacheSize = maxMemory / 8;

        return new LruCache<String, Bitmap>(cacheSize) {

            @Override
            protected int sizeOf(String key, Bitmap bitmap) {

                return bitmap.getByteCount() / 1024;
            }
        };
    }

    // ==============================
    // 初始化
    // ==============================

    private void init() {

        setClipChildren(false);

        setClipToPadding(false);

        setHasFixedSize(true);

        // 初始化吸附助手
        snapHelper = new CarouselSnapHelper();

        snapHelper.attachToRecyclerView(this);

        // 监听用户滑动
        addOnScrollListener(new OnScrollListener() {

            @Override
            public void onScrollStateChanged(
                    RecyclerView recyclerView,
                    int newState) {

                if (newState == SCROLL_STATE_DRAGGING) {

                    // 用户正在拖动
                    stopAutoPlay();

                } else if (newState == SCROLL_STATE_IDLE) {

                    // 滑动完成
                    updateCurrentPosition();

                    // 恢复自动播放
                    startAutoPlay();
                }
            }
        });
    }

    // ==============================
    // JSON 加载入口
    // ==============================

    /**
     * 加载 JSON 字符串
     *
     * @param layoutManager MD3 布局管理器
     * @param json JSON 数据
     */
    public void qd(
            CarouselLayoutManager layoutManager,
            String json) {

        if (released) {
            throw new IllegalStateException(
                    "Md3lbt 已释放，不能继续使用"
            );
        }

        if (layoutManager == null) {
            throw new IllegalArgumentException(
                    "CarouselLayoutManager 不能为空"
            );
        }

        List<ImageItem> items = parseJson(json);

        setLayoutManager(layoutManager);

        adapter = new CarouselAdapter(
                getContext(),
                items,
                imageCache,
                imageExecutor
        );

        setAdapter(adapter);

        currentPosition = 0;

        startAutoPlay();
    }

    /**
     * 使用默认 MD3 布局管理器加载
     *
     * @param json JSON 数据
     */
    public void qd(String json) {

        qd(new CarouselLayoutManager(), json);
    }

    /**
     * 从 res/raw 读取 JSON 文件
     *
     * @param layoutManager MD3 布局管理器
     * @param rawResId R.raw 文件资源 ID
     */
    public void qdFromRaw(
            CarouselLayoutManager layoutManager,
            int rawResId) {

        String json = readRawJson(rawResId);

        qd(layoutManager, json);
    }

    /**
     * 使用默认布局管理器读取 raw JSON
     */
    public void qdFromRaw(int rawResId) {

        qdFromRaw(
                new CarouselLayoutManager(),
                rawResId
        );
    }

    // ==============================
    // JSON 解析
    // ==============================

    /**
     * 解析 JSON
     *
     * 支持：
     *
     * ["url1", "url2"]
     *
     * [{"url":"url1"}, {"url":"url2"}]
     *
     * {"images":[{"url":"url1"}]}
     */
    private List<ImageItem> parseJson(String json) {

        if (json == null || json.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "JSON 数据不能为空"
            );
        }

        List<ImageItem> result = new ArrayList<>();

        try {

            String content = json.trim();

            JSONArray array;

            if (content.startsWith("{")) {

                JSONObject root = new JSONObject(content);

                array = root.optJSONArray("images");

                if (array == null) {
                    array = root.optJSONArray("data");
                }

                if (array == null) {

                    throw new IllegalArgumentException(
                            "JSON 对象必须包含 images 或 data 数组"
                    );
                }

            } else {

                array = new JSONArray(content);
            }

            for (int i = 0; i < array.length(); i++) {

                Object object = array.opt(i);

                String imageUrl = null;

                String title = "";

                if (object instanceof String) {

                    imageUrl = (String) object;

                } else if (object instanceof JSONObject) {

                    JSONObject item = (JSONObject) object;

                    // 兼容多种图片地址字段
                    imageUrl = item.optString("url", "");

                    if (imageUrl.isEmpty()) {
                        imageUrl = item.optString("imageUrl", "");
                    }

                    if (imageUrl.isEmpty()) {
                        imageUrl = item.optString("image", "");
                    }

                    title = item.optString("title", "");
                }

                if (imageUrl != null
                        && !imageUrl.trim().isEmpty()) {

                    result.add(
                            new ImageItem(
                                    imageUrl.trim(),
                                    title
                            )
                    );
                }
            }

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "JSON 解析失败：" + e.getMessage(),
                    e
            );
        }

        return result;
    }

    /**
     * 读取 raw JSON 文件
     */
    private String readRawJson(int rawResId) {

        try (InputStream input =
                     getResources().openRawResource(rawResId);

             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];

            int length;

            while ((length = input.read(buffer)) != -1) {

                output.write(buffer, 0, length);
            }

            return output.toString("UTF-8");

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "读取 raw JSON 失败：" + e.getMessage(),
                    e
            );
        }
    }

    // ==============================
    // 图片数据模型
    // ==============================

    private static class ImageItem {

        final String url;

        final String title;

        ImageItem(String url, String title) {

            this.url = url;

            this.title = title;
        }
    }

    // ==============================
    // 自动播放
    // ==============================

    /**
     * 开始自动播放
     */
    public void startAutoPlay() {

        stopAutoPlay();

        if (released || !autoPlayEnabled) {
            return;
        }

        if (adapter == null || adapter.getItemCount() <= 1) {
            return;
        }

        autoPlayRunnable = new Runnable() {

            @Override
            public void run() {

                if (released || !autoPlayEnabled) {
                    return;
                }

                if (adapter == null) {
                    return;
                }

                int count = adapter.getItemCount();

                if (count <= 1) {
                    return;
                }

                // 获取当前中心位置
                updateCurrentPosition();

                // 计算下一页
                int nextPosition =
                        (currentPosition + 1) % count;

                // 平滑滚动
                smoothScrollToPosition(nextPosition);

                currentPosition = nextPosition;

                // 继续轮播
                handler.postDelayed(
                        this,
                        AUTO_PLAY_INTERVAL
                );
            }
        };

        handler.postDelayed(
                autoPlayRunnable,
                AUTO_PLAY_INTERVAL
        );
    }

    /**
     * 停止自动播放
     */
    public void stopAutoPlay() {

        if (autoPlayRunnable != null) {

            handler.removeCallbacks(autoPlayRunnable);

            autoPlayRunnable = null;
        }
    }

    /**
     * 设置自动播放开关
     */
    public void setAutoPlayEnabled(boolean enabled) {

        autoPlayEnabled = enabled;

        if (enabled) {

            startAutoPlay();

        } else {

            stopAutoPlay();
        }
    }

    /**
     * 设置轮播间隔
     */
    public void setAutoPlayInterval(long interval) {

        // 此方法修改默认间隔
        // 使用 setAutoPlayEnabled 可重新启动轮播
        if (interval <= 0) {
            throw new IllegalArgumentException(
                    "轮播间隔必须大于 0"
            );
        }

        // 由实例字段保存
        autoPlayInterval = interval;

        startAutoPlay();
    }

    private long autoPlayInterval = AUTO_PLAY_INTERVAL;

    // ==============================
    // 当前页码
    // ==============================

    /**
     * 更新中心位置
     */
    private void updateCurrentPosition() {

        if (snapHelper == null || getLayoutManager() == null) {
            return;
        }

        View snapView = snapHelper.findSnapView(
                getLayoutManager()
        );

        if (snapView != null) {

            int position = getChildAdapterPosition(snapView);

            if (position != NO_POSITION) {

                currentPosition = position;
            }
        }
    }

    /**
     * 获取当前页码
     */
    public int getCurrentPosition() {

        updateCurrentPosition();

        return currentPosition;
    }

    /**
     * 手动切换页面
     */
    public void setCurrentPosition(int position) {

        if (adapter == null) {
            return;
        }

        if (position < 0
                || position >= adapter.getItemCount()) {
            return;
        }

        currentPosition = position;

        smoothScrollToPosition(position);
    }

    // ==============================
    // 缓存管理
    // ==============================

    /**
     * 清空图片缓存
     */
    public void clearImageCache() {

        imageCache.evictAll();

        if (adapter != null) {

            adapter.notifyDataSetChanged();
        }
    }

    /**
     * 获取缓存图片数量
     */
    public int getImageCacheCount() {

        return imageCache.size();
    }

    // ==============================
    // 生命周期
    // ==============================

    @Override
    protected void onDetachedFromWindow() {

        stopAutoPlay();

        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {

        super.onAttachedToWindow();

        if (adapter != null && autoPlayEnabled) {

            startAutoPlay();
        }
    }

    /**
     * 完全释放控件
     *
     * Activity 销毁时调用
     */
    public void release() {

        if (released) {
            return;
        }

        released = true;

        stopAutoPlay();

        handler.removeCallbacksAndMessages(null);

        if (snapHelper != null) {

            snapHelper.attachToRecyclerView(null);

            snapHelper = null;
        }

        setAdapter(null);

        adapter = null;

        imageExecutor.shutdownNow();

        imageCache.evictAll();

        currentPosition = 0;
    }

    // ==============================
    // RecyclerView 适配器
    // ==============================

    private static class CarouselAdapter
            extends RecyclerView.Adapter<CarouselAdapter.ViewHolder> {

        private final Context context;

        private final List<ImageItem> items;

        private final LruCache<String, Bitmap> cache;

        private final ExecutorService executor;

        private final int itemWidthPx;

        CarouselAdapter(
                Context context,
                List<ImageItem> items,
                LruCache<String, Bitmap> cache,
                ExecutorService executor) {

            this.context = context;

            this.items = items;

            this.cache = cache;

            this.executor = executor;

            itemWidthPx = dpToPx(context, ITEM_WIDTH_DP);
        }

        @Override
        public ViewHolder onCreateViewHolder(
                ViewGroup parent,
                int viewType) {

            // MD3 遮罩容器
            MaskableFrameLayout frame =
                    new MaskableFrameLayout(context);

            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(
                            itemWidthPx,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );

            int margin = dpToPx(context, ITEM_MARGIN_DP);

            params.setMargins(
                    margin,
                    0,
                    margin,
                    0
            );

            frame.setLayoutParams(params);

            // 设置圆角
            float radius = dpToPx(
                    context,
                    CORNER_RADIUS_DP
            );

            ShapeAppearanceModel shape =
                    ShapeAppearanceModel.builder()
                            .setAllCorners(
                                    CornerFamily.ROUNDED,
                                    radius
                            )
                            .build();

            frame.setShapeAppearanceModel(shape);

            // 创建图片控件
            ImageView image = new ImageView(context);

            image.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            image.setLayoutParams(
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );

            // 图片圆角
            image.setOutlineProvider(
                    new ViewOutlineProvider() {

                        @Override
                        public void getOutline(
                                View view,
                                Outline outline) {

                            outline.setRoundRect(
                                    0,
                                    0,
                                    view.getWidth(),
                                    view.getHeight(),
                                    radius
                            );
                        }
                    }
            );

            image.setClipToOutline(true);

            frame.addView(image);

            return new ViewHolder(frame, image);
        }

        @Override
        public void onBindViewHolder(
                ViewHolder holder,
                int position) {

            ImageItem item = items.get(position);

            String imageUrl = item.url;

            // 记录当前 URL，防止图片错位
            holder.imageView.setTag(imageUrl);

            // 设置占位背景
            holder.imageView.setImageDrawable(null);

            holder.imageView.setBackgroundColor(0xFFEAEAEA);

            // 先读取缓存
            Bitmap bitmap = cache.get(imageUrl);

            if (bitmap != null) {

                holder.imageView.setImageBitmap(bitmap);

                holder.imageView.setBackgroundColor(0x00000000);

                return;
            }

            // 异步加载图片
            executor.execute(() -> {

                Bitmap result = loadBitmap(imageUrl);

                if (result == null) {
                    return;
                }

                // 写入缓存
                cache.put(imageUrl, result);

                // 回到主线程更新界面
                holder.imageView.post(() -> {

                    Object tag = holder.imageView.getTag();

                    if (imageUrl.equals(tag)) {

                        holder.imageView.setImageBitmap(result);

                        holder.imageView.setBackgroundColor(
                                0x00000000
                        );
                    }
                });
            });
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {

            holder.imageView.setTag(null);

            holder.imageView.setImageDrawable(null);

            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {

            return items.size();
        }

        // ==============================
        // 图片加载
        // ==============================

        /**
         * 加载网络图片
         */
        private static Bitmap loadBitmap(String imageUrl) {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(imageUrl);

                connection = (HttpURLConnection)
                        url.openConnection();

                connection.setRequestMethod("GET");

                connection.setConnectTimeout(8000);

                connection.setReadTimeout(10000);

                connection.setInstanceFollowRedirects(true);

                connection.setDoInput(true);

                connection.connect();

                if (connection.getResponseCode()
                        != HttpURLConnection.HTTP_OK) {

                    return null;
                }

                try (InputStream input =
                             connection.getInputStream()) {

                    return BitmapFactory.decodeStream(input);
                }

            } catch (Exception e) {

                e.printStackTrace();

                return null;

            } finally {

                if (connection != null) {

                    connection.disconnect();
                }
            }
        }

        /**
         * dp 转 px
         */
        private static int dpToPx(
                Context context,
                int dp) {

            float density = context.getResources()
                    .getDisplayMetrics().density;

            return (int) (
                    dp * density + 0.5f
            );
        }

        // ==============================
        // ViewHolder
        // ==============================

        static class ViewHolder extends RecyclerView.ViewHolder {

            final MaskableFrameLayout frame;

            final ImageView imageView;

            ViewHolder(
                    MaskableFrameLayout frame,
                    ImageView imageView) {

                super(frame);

                this.frame = frame;

                this.imageView = imageView;
            }
        }
    }
}
