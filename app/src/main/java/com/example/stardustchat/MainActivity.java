package com.example.stardustchat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    // 聊天界面相关控件
    private EditText messageInput;// 消息输入框
    private Button sendButton;// 发送按钮
    private RecyclerView recyclerView;// 消息列表视图
    private ChatAdapter adapter;// 聊天适配器
    private List<ChatMessage> messageList = new ArrayList<>();// 消息列表数据

    // 导航抽屉相关控件
    private DrawerLayout drawerLayout;// 抽屉布局
    private NavigationView navigationView;// 导航视图
    private Toolbar toolbar;// 工具栏

    // 历史记录管理
    private SharedPreferences chatPrefs;// 聊天偏好设置（用于存储聊天历史）
    private String currentChatId;// 当前聊天会话ID

    //用于本轮聊天的 historyId（int 类型）
    private int currentHistoryId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 SharedPreferences 用于存储聊天历史
        chatPrefs = getSharedPreferences("ChatHistory", MODE_PRIVATE);
        // 修正：恢复/新建 currentHistoryId
        currentHistoryId = chatPrefs.getInt("last_history_id", -1);
        if (currentHistoryId == -1) {
            currentHistoryId = (int) (System.currentTimeMillis() / 1000);
            chatPrefs.edit().putInt("last_history_id", currentHistoryId).apply();
            // 此处建议新建一条 ChatHistory 记录
        }

        currentChatId = "chat_" + System.currentTimeMillis();// 生成当前聊天会话的唯一ID（基于当前时间戳）

        // 初始化控件
        initViews();// 初始化视图控件
        setupToolbar();// 设置工具栏
        setupNavigationDrawer();// 设置导航抽屉
        setupRecyclerView();// 设置消息列表

        // 处理从Welcome页面传递过来的历史记录加载请求
        String loadHistory = getIntent().getStringExtra("load_history");
        if (loadHistory != null) {
            // 延迟加载历史记录，确保UI已经初始化完成
            recyclerView.post(() -> loadHistoryByPeriod(loadHistory));
        }


        // 隐藏ActionBar标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // 处理Welcome消息
        handleWelcomeMessage();

        // 处理历史记录加载请求
        handleHistoryLoadRequest();

        // 设置发送按钮
        setupSendButton();
    }

    // 每次切换会话时记得保存
    private void switchHistory(int newHistoryId) {
        currentHistoryId = newHistoryId;
        chatPrefs.edit().putInt("last_history_id", currentHistoryId).apply();
    }

    // ========== 历史记录处理方法 ==========
    /**
     * 处理历史记录加载请求
     * 从 Intent 中获取历史加载参数并执行相应操作
     */
    private void handleHistoryLoadRequest() {
        String loadHistory = getIntent().getStringExtra("load_history");
        if (loadHistory != null && !loadHistory.isEmpty()) {
            // 使用 post() 延迟执行，确保UI初始化完成
            recyclerView.post(new Runnable() {
                @Override
                public void run() {
                    loadHistoryByPeriod(loadHistory);
                }// 根据时间段加载历史记录
            });
        }
    }

    // 添加加载历史记录的方法
    private void loadHistoryByPeriod(String period) {
        long startTime = 0;
        long endTime = System.currentTimeMillis();

        // 计算时间范围
        Calendar calendar = Calendar.getInstance();
        switch (period) {
            case "today":
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startTime = calendar.getTimeInMillis();
                break;
            // 你可以根据yesterday、week、older补充其它case
            case "yesterday":
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                endTime = calendar.getTimeInMillis();
                calendar.add(Calendar.DAY_OF_MONTH, -1);
                startTime = calendar.getTimeInMillis();
                break;
            case "week":
                calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startTime = calendar.getTimeInMillis();
                break;
            case "older":
                // 7天前的 0 点
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                calendar.add(Calendar.DAY_OF_MONTH, -7);
                endTime = calendar.getTimeInMillis();
                startTime = 0;
                break;
        }

        // 查询Room数据库
        AppDatabase db = AppDatabase.getInstance(this);

        List<ChatMessage> messages = db.chatMessageDao().getMessagesByTime(String.valueOf(currentHistoryId), startTime, endTime);

        // 刷新消息列表
        messageList.clear();
        messageList.addAll(messages);
        adapter.notifyDataSetChanged();
    }

    /**
     * 将时间段标识转换为中文显示名称
     * period 时间段标识
     * 中文显示名称
     */
    private String getPeriodDisplayName(String period) {
        switch (period) {
            case "today":
                return "今天";
            case "yesterday":
                return "昨天";
            case "week":
                return "本周";
            case "older":
                return "更早";
            default:
            return period;// 如果不匹配任何已知标识，直接返回原值
        }
    }

    // ========== UI 初始化方法 ==========
    /**
     * 初始化所有视图控件
     * 通过 findViewById 获取布局中定义的控件引用
     */
    private void initViews() {
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        recyclerView = findViewById(R.id.recyclerView);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
    }

    /**
     * 设置工具栏
     * 将自定义工具栏设置为 ActionBar
     */
    private void setupToolbar() {
        setSupportActionBar(toolbar);// 设置工具栏为 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);// 显示返回按钮
            getSupportActionBar().setTitle("StardustChat");// 设置标题
        }
    }

    /**
     * 设置导航抽屉
     * 配置抽屉的开关动画和菜单项选择监听
     */
    private void setupNavigationDrawer() {
        // 创建抽屉切换器，用于处理汉堡菜单图标的动画
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);// 开关描述字符串
        drawerLayout.addDrawerListener(toggle);// 添加抽屉监听器
        toggle.syncState();

        // 设置导航视图的菜单项选择监听器
        navigationView.setNavigationItemSelectedListener(this);
        // 默认选中"当前对话"菜单项
        navigationView.setCheckedItem(R.id.nav_current_chat);
    }

    /**
     * 设置消息列表（RecyclerView）
     * 配置适配器和布局管理器
     */
    private void setupRecyclerView() {
        adapter = new ChatAdapter(messageList);// 创建聊天适配器
        recyclerView.setLayoutManager(new LinearLayoutManager(this));// 设置线性布局管理器
        recyclerView.setAdapter(adapter);// 设置适配器
    }

    /**
     * 处理欢迎消息
     * 如果从其他页面传入了初始消息，则自动发送
     */
    private void handleWelcomeMessage() {
        String firstMsg = getIntent().getStringExtra("first_message");// 获取初始消息
        if (!TextUtils.isEmpty(firstMsg)) {
            addMessage(firstMsg, ChatMessage.TYPE_USER);// 添加用户消息
            int thinkingIndex = addMessage("AI正在思考...", ChatMessage.TYPE_BOT);// 添加AI思考提示
            sendUserMessage(firstMsg, thinkingIndex);// 发送消息到AI服务
        }
    }

    /**
     * 设置发送按钮的点击事件
     */
    private void setupSendButton() {
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userMessage = messageInput.getText().toString().trim();// 获取输入的消息并去除首尾空格
                if (!TextUtils.isEmpty(userMessage)) {// 检查消息不为空
                    addMessage(userMessage, ChatMessage.TYPE_USER);// 添加用户消息到列表
                    int thinkingIndex = addMessage("AI正在思考...", ChatMessage.TYPE_BOT);// 添加AI思考提示
                    messageInput.setText("");// 清空输入框
                    sendUserMessage(userMessage, thinkingIndex);// 发送消息到AI服务
                }
            }
        });
    }

    //实现编辑发送新消息
    public void sendEditedUserMessage(String editedText) {
        // 添加用户消息到消息列表
        addMessage(editedText, ChatMessage.TYPE_USER);
        // 添加 AI 思考提示
        int thinkingIndex = addMessage("AI正在思考...", ChatMessage.TYPE_BOT);
        // 发送到 AI 服务
        sendUserMessage(editedText, thinkingIndex);
    }



    // ========== 消息处理方法 ==========

    /**
     * 添加消息到聊天列表
     *content 消息内容
     * type 消息类型（用户消息或机器人消息）
     * 新添加消息的索引位置
     */
    private int addMessage(String content, int type) {
        ChatMessage newMessage = new ChatMessage(content, type);
        messageList.add(newMessage);
        int pos = messageList.size() - 1;
        adapter.notifyItemInserted(pos);
        recyclerView.scrollToPosition(pos);

        // 保存聊天历史到本地存储
        saveChatHistory(newMessage,currentHistoryId);

        return pos; // 返回消息位置，用于后续更新
    }

    /**
     * 保存聊天历史到本地存储
     * 使用 SharedPreferences 和 JSON 格式存储
     * message 要保存的消息
     */
    private void saveChatHistory(ChatMessage message, int historyId) {
        AppDatabase db = AppDatabase.getInstance(this);

        // 1. 保存消息
        message.historyId = String.valueOf(historyId);
        message.timestamp = System.currentTimeMillis();
        db.chatMessageDao().insert(message);

        // 2. 如果是用户消息，同时更新聊天摘要信息
        if (message.type == ChatMessage.TYPE_USER) {
            ChatHistory history = db.chatHistoryDao().getHistoryById(historyId);
            if (history != null) {
                // 聊天标题：取消息前20个字符
                String title = message.content.length() > 20
                        ? message.content.substring(0, 20) + "..."
                        : message.content;
                history.title = title;
                history.timestamp = message.timestamp;
                // 获取当前消息数
                int msgCount = db.chatMessageDao().getMessageCount(String.valueOf(historyId));
                history.messageCount = msgCount;
                db.chatHistoryDao().update(history);
            }
        }
    }

    /**
     * 发送用户消息到 AI 服务
     * 使用 OkHttp 发送 HTTP 请求到 AI API
     * message 用户输入的消息
     * thinkingIndex AI思考提示消息的索引位置
     */
    private void sendUserMessage(String message, int thinkingIndex) {
        // 构造 JSON 请求体
        String json = "{"
                + "\"model\":\"zai-org/GLM-4.5V\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + message + "\"}],"
                + "\"tokens\": 6400"
                + "}";

        // 创建请求体
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);

        // 构建 HTTP 请求
        Request request = new Request.Builder()
                .url("https://api.siliconflow.cn/v1/chat/completions")
                .post(body)
                .addHeader("", "")
                .build();

        // 创建 HTTP 客户端，设置超时时间
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // 异步执行请求
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 请求失败时的处理
                runOnUiThread(() -> {
                    // 检查索引是否有效，避免数组越界
                    if (thinkingIndex < messageList.size()) {// 更新思考提示为错误信息
                        messageList.get(thinkingIndex).setContent("请求失败：" + e.getMessage());
                        adapter.notifyItemChanged(thinkingIndex);// 通知适配器更新
                        saveChatHistory(messageList.get(thinkingIndex),currentHistoryId);// 保存到历史记录
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // 请求成功时的处理
                String resp = response.body() != null ? response.body().string() : "";
                String resultText = "";// 用于存储解析后的AI回复
                try {// 解析 JSON 响应
                    JSONObject jsonObject = new JSONObject(resp);
                    JSONArray choices = jsonObject.getJSONArray("choices");// 获取回复选项
                    if (choices.length() > 0) {
                        // 获取AI的回复内容
                        JSONObject messageObj = choices.getJSONObject(0).getJSONObject("message");
                        resultText = messageObj.getString("content");

                        // 清理AI回复中的格式标记
                        resultText = resultText.replaceAll("(?m)^\\s*#+\\s*", "");// 移除标题标记 #
                        resultText = resultText.replaceAll("(?m)^\\s*\\*\\s*", "");// 移除列表标记 *
                        resultText = resultText.replaceAll("\\*+", "");// 移除强调标记 **
                        resultText = resultText.replaceAll("`+", "");// 移除代码标记 `
                        resultText = resultText.replaceAll("(?m)^[ \\t]*\\r?\\n", "");// 移除空行
                    } else {
                        resultText = "AI无回复";// 没有回复选项时的默认提示
                    }
                } catch (Exception e) {
                    resultText = "解析失败: " + e.getMessage();// JSON解析失败时的错误提示
                }

                final String showText = resultText; // 声明为 final 以便在内部类中使用
                runOnUiThread(() -> {
                    if (thinkingIndex < messageList.size()) {
                        // 更新思考提示为AI的实际回复
                        messageList.get(thinkingIndex).setContent(showText);
                        adapter.notifyItemChanged(thinkingIndex);// 通知适配器更新
                        saveChatHistory(messageList.get(thinkingIndex),currentHistoryId);// 保存到历史记录
                    }
                });
            }
        });
    }

    // ========== 导航菜单处理方法 ==========

    /**
     * 处理导航菜单项的选择
     * item 被选择的菜单项
     * 是否处理了该菜单项
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();// 获取菜单项ID

        // 根据不同的菜单项执行相应操作
        if (id == R.id.nav_current_chat) {
            Toast.makeText(this, "当前对话", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_today) {
            loadHistoryByPeriod("today");
        } else if (id == R.id.nav_yesterday) {
            loadHistoryByPeriod("yesterday");
        } else if (id == R.id.nav_this_week) {
            loadHistoryByPeriod("week");
        } else if (id == R.id.nav_older) {
            loadHistoryByPeriod("older");
        } else if (id == R.id.nav_settings) {
            Toast.makeText(this, "设置", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_theme) {
            Toast.makeText(this, "主题", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_about) {
            showAbout();// 显示关于对话框
        }

        drawerLayout.closeDrawer(GravityCompat.START);// 关闭导航抽屉
        return true;// 返回 true 表示已处理该菜单项
    }

    /**
     * 显示关于应用的对话框
     */
    private void showAbout() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("关于 StardustChat");// 对话框标题
        builder.setMessage("StardustChat v1.0\n\n一个基于AI的智能聊天应用");// 对话框内容
        builder.setPositiveButton("确定", null);// 确定按钮
        builder.show();// 显示对话框
    }

    /**
     * 处理返回键按下事件
     * 如果导航抽屉是打开的，先关闭抽屉；否则执行默认返回操作
     */
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);// 关闭导航抽屉
        } else {
            super.onBackPressed();// 执行默认返回操作
        }
    }
}
