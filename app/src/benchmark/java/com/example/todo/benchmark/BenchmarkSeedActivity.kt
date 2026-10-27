package com.example.todo.benchmark

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import androidx.room.withTransaction
import com.example.todo.App
import com.example.todo.data.local.TodoEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Present only in the isolated .benchmark APK, never in debug or release. */
class BenchmarkSeedActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.example.todo.benchmark")
        val label = TextView(this).apply { text = "准备性能测试数据…" }
        setContentView(label)
        scope.launch {
            val db = (application as App).container.database
            db.withTransaction {
                db.todos().observeAll().first().filter { it.title.startsWith("性能样例 ") }.forEach { db.todos().delete(it.id) }
                repeat(40) { index -> db.todos().insert(TodoEntity(title = "性能样例 %02d".format(index + 1),
                    note = "仅供性能测试的合成事项", location = "会议室 A", createdAt = System.currentTimeMillis())) }
            }
            label.text = "性能测试数据已就绪"
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
