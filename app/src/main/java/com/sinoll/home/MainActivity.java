package com.sinoll.home;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.Toast;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.navigation.NavigationView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.sinoll.home.data.AuthApi;
import com.sinoll.home.data.AuthSession;
import com.sinoll.home.data.ImageLoader;
import com.sinoll.home.databinding.ActivityMainBinding;
import com.sinoll.home.databinding.NavHeaderMainBinding;
import com.sinoll.home.ui.MessageNotifier;
import com.sinoll.home.ui.auth.LoginActivity;
import com.sinoll.home.ui.search.SearchViewModel;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private NavHeaderMainBinding navHeader;
    private MessageNotifier messages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        // 模拟器带刘海（高 136px），允许内容延伸到刘海区，否则窗口 frame 被压在刘海下方
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        // 拦截 DecorView 默认的 insets padding，改为手动分发：
        // AppBarLayout 顶部 padding=状态栏高度，内容容器底部 padding=导航栏高度
        final View contentContainer = binding.appBarMain.contentContainer.getRoot();
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            // 沉浸式会派发一次 top=0，状态栏高度本身不变，留住真实高度供退出沉浸时用
            if (top > 0) {
                statusBarInset = top;
            }
            applyAppBarTopPadding();
            applyNavHeaderTopPadding();
            contentContainer.setPadding(contentContainer.getPaddingLeft(), 0,
                    contentContainer.getPaddingRight(), bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        getWindow().getDecorView().post(() -> setVideoImmersive(true));

        setSupportActionBar(binding.appBarMain.toolbar);
        // 标题栏状态只认 AppBarLayout 的真实偏移：列表在顶部下拉时栏是 Behavior 自己放出来的，
        // 滚动回调收不到通知，标记一旦失真下一次上滑就收不起来。
        // 透明度同步跟着偏移走：状态栏那段 padding 不参与折叠，不收淡标题会压在状态栏图标上。
        binding.appBarMain.appBar.addOnOffsetChangedListener((appBar, verticalOffset) -> {
            int range = appBar.getTotalScrollRange();
            if (range > 0) {
                barCollapsed = -verticalOffset >= range;
                binding.appBarMain.toolbar.setAlpha((range + verticalOffset) / (float) range);
            }
        });
        binding.appBarMain.fab.setOnClickListener(view -> openMessages());
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_search, R.id.nav_gallery, R.id.nav_slideshow)
                .setDrawerLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        // 动态/图文进来是收起态，切到别的页面得把标题栏还回去，否则那些页没有标题栏可用
        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> setBarCollapsed(false));
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
        bindNavHeader(navigationView);
        setupSearchBar();
        messages = new MessageNotifier(this, binding.getRoot(), this::openMessages);
    }

    /** 消息只有登录用户才有：没登录时先进登录页，回来再点一次就行。 */
    private void openMessages() {
        if (!AuthSession.get(this).isSignedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        Navigation.findNavController(this, R.id.nav_host_fragment_content_main)
                .navigate(R.id.nav_messages);
    }

    private void setupSearchBar() {
        binding.appBarMain.searchOpen.setOnClickListener(view -> expandSearch());
        binding.appBarMain.searchClose.setOnClickListener(view -> collapseSearch());
        binding.appBarMain.searchInput.setOnEditorActionListener((textView, actionId, event) -> {
            boolean submitted = actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (submitted) {
                submitSearch();
            }
            return submitted;
        });
    }

    private void expandSearch() {
        binding.appBarMain.searchOpen.setVisibility(View.GONE);
        binding.appBarMain.searchBar.setVisibility(View.VISIBLE);
        // 标题让位，输入框才能占满整条标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.appBarMain.searchInput.requestFocus();
        keyboard(true);
    }

    private void collapseSearch() {
        keyboard(false);
        binding.appBarMain.searchInput.setText("");
        binding.appBarMain.searchBar.setVisibility(View.GONE);
        binding.appBarMain.searchOpen.setVisibility(View.VISIBLE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }

    private void submitSearch() {
        String query = binding.appBarMain.searchInput.getText().toString().trim();
        collapseSearch();
        if (query.isEmpty()) {
            return;
        }
        new ViewModelProvider(this).get(SearchViewModel.class).setQuery(query);
        Bundle args = new Bundle();
        args.putString(SearchViewModel.ARG_QUERY, query);
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .build();
        Navigation.findNavController(this, R.id.nav_host_fragment_content_main)
                .navigate(R.id.nav_search, args, options);
    }

    private void keyboard(boolean show) {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager == null) {
            return;
        }
        if (show) {
            manager.showSoftInput(binding.appBarMain.searchInput,
                    InputMethodManager.SHOW_IMPLICIT);
        } else {
            manager.hideSoftInputFromWindow(
                    binding.appBarMain.searchInput.getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (binding != null && binding.appBarMain.searchBar.getVisibility() == View.VISIBLE) {
            collapseSearch();
            return;
        }
        super.onBackPressed();
    }

    private void bindNavHeader(NavigationView navigationView) {
        navHeader = NavHeaderMainBinding.bind(navigationView.getHeaderView(0));
        navHeader.btnLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        navHeader.btnLogout.setOnClickListener(v -> {
            navHeader.btnLogout.setEnabled(false);
            AuthApi.logout(this, response -> {
                if (navHeader == null) {
                    return;
                }
                navHeader.btnLogout.setEnabled(true);
                renderNavHeader();
                Toast.makeText(this, R.string.toast_logout_ok, Toast.LENGTH_SHORT).show();
            });
        });
        applyNavHeaderTopPadding();
        renderNavHeader();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 登录/注册页返回后同步头部，省掉跨页面广播
        renderNavHeader();
        messages.refresh();
    }

    private void renderNavHeader() {
        AuthSession session = AuthSession.get(this);
        boolean signedIn = session.isSignedIn();
        navHeader.btnLogin.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        navHeader.loggedInActions.setVisibility(signedIn ? View.VISIBLE : View.GONE);

        if (!signedIn) {
            navHeader.tvUsername.setText(R.string.nav_user_name);
            navHeader.tvUseremail.setText(R.string.nav_user_email);
            showAvatarPlaceholder();
            return;
        }

        navHeader.tvUsername.setText(session.displayName());
        String email = session.email();
        navHeader.tvUseremail.setText(TextUtils.isEmpty(email)
                ? getString(R.string.nav_user_handle, session.username()) : email);

        String avatarUrl = session.avatarUrl();
        if (TextUtils.isEmpty(avatarUrl)) {
            showAvatarInitial(session.initial());
            return;
        }
        int size = getResources().getDimensionPixelSize(R.dimen.nav_avatar_size);
        ImageLoader.loadCircular(avatarUrl, size, bitmap -> {
            if (navHeader == null) {
                return;
            }
            if (bitmap == null) {
                showAvatarInitial(session.initial());
                return;
            }
            navHeader.tvAvatarInitial.setVisibility(View.GONE);
            navHeader.ivAvatar.setVisibility(View.VISIBLE);
            navHeader.ivAvatar.setBackgroundResource(android.R.color.transparent);
            navHeader.ivAvatar.setImageTintList(null);
            navHeader.ivAvatar.setPadding(0, 0, 0, 0);
            navHeader.ivAvatar.setImageBitmap(bitmap);
        });
    }

    private void showAvatarPlaceholder() {
        navHeader.tvAvatarInitial.setVisibility(View.GONE);
        navHeader.ivAvatar.setVisibility(View.VISIBLE);
        navHeader.ivAvatar.setBackgroundResource(R.drawable.bg_avatar_placeholder);
        navHeader.ivAvatar.setImageTintList(
                ColorStateList.valueOf(0xE6FFFFFF));
        int inset = (int) dp(14f);
        navHeader.ivAvatar.setPadding(inset, inset, inset, inset);
        navHeader.ivAvatar.setImageResource(R.drawable.ic_field_person);
    }

    private void showAvatarInitial(String initial) {
        navHeader.ivAvatar.setVisibility(View.GONE);
        navHeader.tvAvatarInitial.setText(initial);
        navHeader.tvAvatarInitial.setVisibility(View.VISIBLE);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private boolean videoImmersive;
    private boolean chromeRevealed;
    private boolean barCollapsed;
    private int statusBarInset;
    private final Handler chromeHandler = new Handler(Looper.getMainLooper());
    private final Runnable chromeHideTask = this::hideAppBarForVideo;

    private void applyAppBarTopPadding() {
        if (binding == null) {
            return;
        }
        AppBarLayout appBar = binding.appBarMain.appBar;
        int top = (videoImmersive && !chromeRevealed) ? 0 : statusBarInset;
        if (appBar.getPaddingTop() != top) {
            appBar.setPadding(appBar.getPaddingLeft(), top,
                    appBar.getPaddingRight(), appBar.getPaddingBottom());
        }
    }

    /** 抽屉头部自己吞掉状态栏高度：装饰性内容要铺到顶，文字不能被状态栏压住。 */
    private void applyNavHeaderTopPadding() {
        if (navHeader == null) {
            return;
        }
        View header = navHeader.getRoot();
        int top = statusBarInset
                + getResources().getDimensionPixelSize(R.dimen.nav_header_padding_top);
        if (header.getPaddingTop() != top) {
            header.setPadding(header.getPaddingLeft(), top,
                    header.getPaddingRight(), header.getPaddingBottom());
        }
    }

    public void setVideoImmersive(boolean immersive) {
        if (binding == null) {
            return;
        }
        videoImmersive = immersive;
        chromeHandler.removeCallbacks(chromeHideTask);
        // AppCompat 1.3.1 会重置平台 InsetsController 的设置，这里用传统 systemUiVisibility
        int base = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(
                immersive ? base | View.SYSTEM_UI_FLAG_FULLSCREEN : base);
        applyAppBarTopPadding();
        if (immersive) {
            hideAppBarForVideo();
            setFabVisible(false);
        } else {
            showAppBar();
            setFabVisible(true);
        }
    }

    public void revealChromeTemporarily() {
        if (!videoImmersive || binding == null) {
            return;
        }
        chromeRevealed = true;
        applyAppBarTopPadding();
        showAppBar();
        chromeHandler.removeCallbacks(chromeHideTask);
        chromeHandler.postDelayed(chromeHideTask, 2500);
    }

    /** 列表上滑收标题栏、下滑放回来。视频页的沉浸由 setVideoImmersive 负责，这里不抢它的活。 */
    public void setBarCollapsed(boolean collapsed) {
        if (binding == null || videoImmersive || barCollapsed == collapsed) {
            return;
        }
        barCollapsed = collapsed;
        binding.appBarMain.appBar.setExpanded(!collapsed, true);
    }

    /** 进动态/图文页：标题栏直接是收起态，不做滑出动画，否则从视频页切回来会闪一下。 */
    public void collapseBarForListPage() {
        if (binding == null || videoImmersive || barCollapsed) {
            return;
        }
        barCollapsed = true;
        binding.appBarMain.appBar.setExpanded(false, false);
    }

    /**
     * Toolbar 不折叠，视频页要满屏时直接把 AppBarLayout 的高度置 0。
     * 用 setVisibility(GONE) 不行：CoordinatorLayout 取的是它最后一次测量出来的高度，
     * 内容会被这段过期高度顶下来，视频上方多出一条空白。
     */
    private void setAppBarHidden(boolean hidden) {
        AppBarLayout appBar = binding.appBarMain.appBar;
        appBar.animate().cancel();
        // 列表页可能已经把标题栏折叠出一段偏移，先归零再改高度，否则回到视频页会残留半条栏
        barCollapsed = false;
        appBar.setExpanded(true, false);
        binding.appBarMain.toolbar.animate().cancel();
        binding.appBarMain.toolbar.setAlpha(1f);
        appBar.setAlpha(hidden ? 0f : 1f);
        int height = hidden ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (appBar.getLayoutParams().height != height) {
            appBar.getLayoutParams().height = height;
            appBar.requestLayout();
        }
    }

    private void showAppBar() {
        setAppBarHidden(false);
    }

    private void hideAppBarForVideo() {
        if (!videoImmersive || binding == null) {
            return;
        }
        chromeRevealed = false;
        setAppBarHidden(true);
    }

    private void setFabVisible(boolean visible) {
        if (binding == null) {
            return;
        }
        View fab = binding.appBarMain.fab;
        if (visible) {
            fab.setVisibility(View.VISIBLE);
            fab.animate().alpha(1f).setDuration(180).start();
        } else {
            fab.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> fab.setVisibility(View.GONE))
                    .start();
        }
    }
}