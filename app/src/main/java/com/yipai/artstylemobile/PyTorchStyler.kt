package com.yipai.artstylemobile


import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * PyTorch 风格迁移单例类
 * 用于在 Android 上运行 Fast Neural Style Transfer 模型
 */
object PyTorchStyler {

    private const val TAG = "PyTorchStyler"

    // 私有属性：加载的模型模块
    private var module: Module? = null

    // 线程安全锁
    private val lock = ReentrantLock()

    // 模型加载状态
    private var isModelLoaded = false

    /**
     * 从 assets 目录加载 .ptl 模型文件
     *
     * @param assetManager Android AssetManager
     * @param modelName 模型文件名（如 "mosaic.ptl"）
     * @return 加载成功返回 true，失败返回 false
     */
    fun loadModel(context: Context, modelName: String): Boolean {
        return lock.withLock {
            try {
                Log.d(TAG, "开始加载模型：$modelName")

                // 从 assets 复制模型文件到本地缓存目录
                val modelFile = File(context.cacheDir, modelName)
                context.assets.open(modelName).use { inputStream ->
                    modelFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 使用本地文件路径加载模型
                module = Module.load(modelFile.absolutePath)

                isModelLoaded = module != null

                if (isModelLoaded) {
                    Log.d(TAG, "模型加载成功：$modelName")
                } else {
                    Log.e(TAG, "模型加载失败：module 为 null")
                }

                isModelLoaded

            } catch (e: Exception) {
                Log.e(TAG, "加载模型时发生异常：${e.message}", e)
                module = null
                isModelLoaded = false
                false
            }
        }
    }


    /**
     * 对输入 Bitmap 进行风格化处理
     *
     * @param bitmap 输入图像（ARGB_8888 格式）
     * @return 风格化后的 Bitmap，失败返回 null
     */
    fun stylize(bitmap: Bitmap): Bitmap? {
        // 检查模型是否已加载
        if (!isModelLoaded || module == null) {
            Log.e(TAG, "模型未加载，无法进行风格化")
            return null
        }

        // 检查 Bitmap 格式
        if (bitmap.config != Bitmap.Config.ARGB_8888) {
            Log.w(TAG, "Bitmap 格式不是 ARGB_8888，尝试转换")
            val converted = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            return stylizeInternal(converted)
        }

        return stylizeInternal(bitmap)
    }

    /**
     * 内部风格化处理逻辑
     */
    private fun stylizeInternal(bitmap: Bitmap): Bitmap? {
        return lock.withLock {
            try {
                val width = bitmap.width
                val height = bitmap.height

                Log.d(TAG, "开始风格化处理，图像尺寸：${width}x${height}")

                // ========== 步骤 1: Bitmap 转 Tensor (CHW, float32) ==========
                val tensor = bitmapToTensor(bitmap)

                // ========== 步骤 2: 预处理 - 像素值乘以 255.0 ==========
                val tensorData = tensor.dataAsFloatArray
                for (i in tensorData.indices) {
                    tensorData[i] *= 255.0f
                }
                val scaledTensor = Tensor.fromBlob(tensorData, tensor.shape())

                // ========== 步骤 3: 添加 batch 维度 [1, 3, H, W] ==========
                // 创建新的数组并调整形状
                val width1 = bitmap.width
                val height1 = bitmap.height
                val batchedData = FloatArray(3 * height1 * width1) // batch 维度为 1，所以数据量不变
                System.arraycopy(tensorData, 0, batchedData, 0, tensorData.size)
                val batchedTensor = Tensor.fromBlob(batchedData, longArrayOf(1, 3, height1.toLong(), width1.toLong()))

                // ========== 步骤 4: 模型推理 ==========
                val outputIValue = module!!.forward(IValue.from(batchedTensor))
                val outputTensor = outputIValue.toTensor()

                // ========== 步骤 5: 后处理 - clamp(0, 255) 并转回 Bitmap ==========
                val resultBitmap = tensorToBitmap(outputTensor, width, height)



                Log.d(TAG, "风格化处理完成")
                resultBitmap

            } catch (e: Exception) {
                Log.e(TAG, "风格化处理时发生异常：${e.message}", e)
                null
            }
        }
    }

    /**
     * 将 Bitmap 转换为 PyTorch Tensor (CHW 格式，float32)
     * 保持原始尺寸
     */
    private fun bitmapToTensor(bitmap: Bitmap): Tensor {
        val width = bitmap.width
        val height = bitmap.height

        // 创建 3 x H x W 的 float 数组（CHW 格式）
        val data = FloatArray(3 * height * width)

        // 遍历每个像素，提取 RGB 通道值并归一化到 [0, 1]
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = Color.red(pixel) / 255.0f
                val g = Color.green(pixel) / 255.0f
                val b = Color.blue(pixel) / 255.0f

                // CHW 格式：先存所有 R，再存所有 G，最后存所有 B
                data[0 * height * width + y * width + x] = r
                data[1 * height * width + y * width + x] = g
                data[2 * height * width + y * width + x] = b
            }
        }

        return Tensor.fromBlob(data, longArrayOf(3, height.toLong(), width.toLong()))
    }

    /**
     * 将 PyTorch Tensor 转换回 Bitmap
     * 输入 Tensor 格式：[1, 3, H, W] 或 [3, H, W]，值范围 [0, 255]
     */
    private fun tensorToBitmap(tensor: Tensor, width: Int, height: Int): Bitmap {
        // 获取 Tensor 数据
        val data = tensor.dataAsFloatArray

        // 创建输出 Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x

                // 从 CHW 格式读取 RGB 值
                val r = data[0 * height * width + idx]
                val g = data[1 * height * width + idx]
                val b = data[2 * height * width + idx]

                // Clamp 到 [0, 255] 并转换为 int
                val rClamped = r.coerceIn(0f, 255f).toInt()
                val gClamped = g.coerceIn(0f, 255f).toInt()
                val bClamped = b.coerceIn(0f, 255f).toInt()

                // 转换为 ARGB 像素值
                pixels[idx] = Color.rgb(rClamped, gClamped, bClamped)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean = isModelLoaded

    /**
     * 释放模型资源
     */
    fun release() {
        lock.withLock {
            module?.destroy()
            module = null
            isModelLoaded = false
            Log.d(TAG, "模型资源已释放")
        }
    }
}