package com.personal.todo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Tags and the task/tag links.
 *
 * Linking and unlinking are explicit: [link] ignores a pair that already exists,
 * and removing a tag clears its links first, so no row can ever point at a tag
 * that is gone.
 */
@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, name ASC")
    fun observeTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): Tag?

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Tag?

    @Insert
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    /** Removes the tag itself; callers clear its links in the same transaction. */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    /** Every link, so the UI can group tasks by tag without a join per query. */
    @Query("SELECT * FROM task_tags")
    fun observeAllLinks(): Flow<List<TaskTag>>

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN task_tags tt ON tt.tagId = t.id
        WHERE tt.taskId = :taskId
        ORDER BY t.sortOrder ASC, t.name ASC
        """
    )
    fun observeTagsOf(taskId: Long): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: TaskTag)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun unlink(taskId: Long, tagId: Long)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun clearLinksOfTask(taskId: Long)

    @Query("DELETE FROM task_tags WHERE tagId = :tagId")
    suspend fun clearLinksOfTag(tagId: Long)
}
