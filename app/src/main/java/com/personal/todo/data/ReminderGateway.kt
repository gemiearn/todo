package com.personal.todo.data

import com.personal.todo.data.db.Task

/**
 * 提醒调度的出口。
 *
 * 仓库在每次写完一条任务之后调它一次，把这个功能里最容易漏掉的部分收在一处：
 * 新建成、编辑完、勾完、删掉、从回收站恢复——这些路径都从 [TaskRepository] 走，
 * 所以只要在这里挂钩，就不必让每个界面自己记得去设闹钟或取消。
 *
 * 实现放在 `notification` 包里（那边才知道 Android 的 `AlarmManager`），数据层只认
 * 这个接口。这样仓库不必依赖 Android 的闹钟 API，单测里也能塞一个什么都不做的实现。
 */
interface ReminderGateway {

    /**
     * 按任务**当前的状态**决定「设一个闹钟」还是「撤掉闹钟」。
     *
     * 幂等：已完成、已删除、没有提醒、时刻已过，都是取消。所以调用点不必先自己判断，
     * 重复调用也不会出问题。
     */
    fun sync(task: Task)

    /** 撤掉这条任务的闹钟。任务已经不在了（或被删了）时也安全。 */
    fun cancel(taskId: Long)
}

/** 不做任何事的实现：没有注入调度器时（例如单测）用它。 */
object NoReminderGateway : ReminderGateway {
    override fun sync(task: Task) = Unit

    override fun cancel(taskId: Long) = Unit
}
