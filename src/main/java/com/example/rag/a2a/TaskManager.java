package com.example.rag.a2a;

import java.util.*;

/**
 * A2A Task Manager - 管理 Task 的生命周期
 */
public class TaskManager {

    private final Map<String, Task> tasks = new LinkedHashMap<>();

    public Task create(String taskId, String skillId) {
        Task task = new Task(taskId, skillId);
        tasks.put(taskId, task);
        return task;
    }

    public Task get(String taskId) {
        return tasks.get(taskId);
    }

    public void complete(String taskId, String result) {
        Task task = tasks.get(taskId);
        if (task != null) task.complete(result);
    }

    public void fail(String taskId, String error) {
        Task task = tasks.get(taskId);
        if (task != null) task.fail(error);
    }

    public List<Task> listRecent(int limit) {
        List<Task> all = new ArrayList<>(tasks.values());
        if (all.size() <= limit) return all;
        return all.subList(all.size() - limit, all.size());
    }

    public int totalTasks() {
        return tasks.size();
    }
}
