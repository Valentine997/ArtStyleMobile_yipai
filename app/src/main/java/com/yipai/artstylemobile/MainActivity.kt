package com.yipai.artstylemobile

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PICK_IMAGE = 1001
        private const val REQUEST_PERMISSION = 1002
    }

    // 视图绑定
    private lateinit var imageView: ImageView
    private lateinit var styleSpinner: Spinner
    private lateinit var generateButton: Button
    private lateinit var saveButton: Button

    // 状态变量
    private var currentBitmap: Bitmap? = null
    private var stylizedBitmap: Bitmap? = null
    private var currentStyleIndex: Int = 0
    private var isModelLoaded: Boolean = false
    private var currentModelName: String = ""

    // 协程作用域
    private val mainScope = MainScope()

    // 风格数组资源
    private val styleNames: Array<String> by lazy {
        resources.getStringArray(R.array.style_names)
    }

    private val styleFiles: Array<String> by lazy {
        resources.getStringArray(R.array.style_files)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        initViews()

        // 初始化 Spinner
        initSpinner()

        // 设置点击事件
        setupClickListeners()

        // 检查并请求权限
        checkPermissions()
    }

    /**
     * 初始化视图绑定
     */
    private fun initViews() {
        imageView = findViewById(R.id.imageView)
        styleSpinner = findViewById(R.id.styleSpinner)
        generateButton = findViewById(R.id.generateButton)
        saveButton = findViewById(R.id.saveButton)

        // 初始状态：禁用生成和保存按钮
        generateButton.isEnabled = false
        saveButton.isEnabled = false
    }

    /**
     * 初始化 Spinner
     */
    private fun initSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            styleNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        styleSpinner.adapter = adapter

        // 设置选中监听
        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentStyleIndex = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                currentStyleIndex = 0
            }
        }
    }

    /**
     * 设置点击事件
     */
    private fun setupClickListeners() {
        // 点击 ImageView 选择图片
        imageView.setOnClickListener {
            pickImageFromGallery()
        }

        // 生成风格图按钮
        generateButton.setOnClickListener {
            generateStyleImage()
        }

        // 保存按钮
        saveButton.setOnClickListener {
            saveStylizedImage()
        }
    }

    /**
     * 检查并请求存储权限
     */
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    REQUEST_PERMISSION
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12 (API 29-32)
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_PERMISSION
                )
            }
        } else {
            // Android 9 及以下
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val missingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    REQUEST_PERMISSION
                )
            }
        }
    }

    /**
     * 权限请求结果回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSION) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(
                    this,
                    "需要存储权限才能保存图片",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 从相册选择图片
     */
    private fun pickImageFromGallery() {
        Log.d("DEBUG", "Android 版本: ${Build.VERSION.SDK_INT}")
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            val chooser = Intent.createChooser(intent, "选择图片")
            startActivityForResult(chooser, REQUEST_PICK_IMAGE)
            Log.d(TAG, "图片选择器已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动图片选择器失败: ${e.message}", e)
            Toast.makeText(this, "无法打开图片选择器", Toast.LENGTH_SHORT).show()
        }
        startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    /**
     * 处理选择图片的结果
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                loadBitmapFromUri(uri)
            }
        }
    }

    /**
     * 从 Uri 加载 Bitmap
     */
    private fun loadBitmapFromUri(uri: Uri) {
        try {
            // 使用 ContentResolver 打开输入流
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)

                if (bitmap != null) {
                    currentBitmap = bitmap
                    stylizedBitmap = null

                    // 显示原图
                    imageView.setImageBitmap(bitmap)

                    // 启用生成按钮
                    generateButton.isEnabled = true
                    generateButton.text = "生成风格图"

                    // 禁用保存按钮
                    saveButton.isEnabled = false

                    Toast.makeText(this, "图片已加载", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: IOException) {
            Toast.makeText(this, "加载图片失败：${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "发生错误：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 生成风格图
     */
    private fun generateStyleImage() {
        // 检查是否有图片
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查模型是否需要加载
        val modelName = styleFiles[currentStyleIndex]

        // 显示加载状态
        generateButton.isEnabled = false
        generateButton.text = "处理中..."

        // 在后台线程执行风格化
        mainScope.launch {
            try {
                // 检查是否需要重新加载模型
                if (!isModelLoaded || modelName != currentModelName) {
                    withContext(Dispatchers.IO) {
                        isModelLoaded = PyTorchStyler.loadModel(this@MainActivity, modelName)
                        currentModelName = modelName
                    }

                    if (!isModelLoaded) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "模型加载失败：$modelName",
                                Toast.LENGTH_SHORT
                            ).show()
                            generateButton.isEnabled = true
                            generateButton.text = "生成风格图"
                        }
                        return@launch
                    }
                }

                // 执行风格化推理
                val result = withContext(Dispatchers.IO) {
                    PyTorchStyler.stylize(bitmap)
                }

                if (result != null) {
                    stylizedBitmap = result

                    withContext(Dispatchers.Main) {
                        // 显示风格化后的图片
                        imageView.setImageBitmap(result)

                        // 启用保存按钮
                        saveButton.isEnabled = true

                        Toast.makeText(
                            this@MainActivity,
                            "风格化完成",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "风格化处理失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "发生错误：${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    generateButton.isEnabled = true
                    generateButton.text = "生成风格图"
                }
            }
        }
    }

    /**
     * 保存风格化图片到相册
     */
    private fun saveStylizedImage() {
        val bitmap = stylizedBitmap
        if (bitmap == null) {
            Toast.makeText(this, "没有可保存的图片", Toast.LENGTH_SHORT).show()
            return
        }

        mainScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    saveBitmapToGallery(bitmap)
                }

                withContext(Dispatchers.Main) {
                    if (saved) {
                        Toast.makeText(this@MainActivity, "已保存到相册", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "保存出错：${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 保存 Bitmap 到系统相册（使用 MediaStore）
     */
    private fun saveBitmapToGallery(bitmap: Bitmap): Boolean {
        return try {
            val fileName = "StyleTransfer_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    put(MediaStore.MediaColumns.DATA,
                        "${Environment.getExternalStorageDirectory()}/${Environment.DIRECTORY_PICTURES}/$fileName")
                }
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }

            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 释放资源
     */
    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        PyTorchStyler.release()
    }
}