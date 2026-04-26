package com.example.rag.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A Task - 一次 Agent 间协作的任务单元
 * 包含完整的思考过程数据：问题、检索片段、生成结果
 */
public class Task {

    public enum State { submitted, working, completed, failed, canceled }

    private final String id;
    private final String skillId;
    private State state;
    private String result;
    private String error;
    private long createdAt;

    /** 原始问题 */
    private String question;
    /** 检索到的相关文档片段 (index, text, source, rrfScore, vectorScore) */
    private List<Map<String, Object>> sources;

    public Task(String id, String skillId) {
        this.id = id;
        this.skillId = skillId;
        this.state = State.submitted;
        this.createdAt = System.currentTimeMillis();
    }

    public void complete(String result) {
        this.state = State.completed;
        this.result = result;
    }

    public void completeWithDetails(String result, String question, List<Map<String, Object>> sources) {
        this.state = State.completed;
        this.result = result;
        this.question = question;
        this.sources = sources;
    }

    public void fail(String error) {
        this.state = State.failed;
        this.error = error;
    }

    // Getters
    public String getId() { return id; }
    public String getSkillId() { return skillId; }
    public State getState() { return state; }
    public String getResult() { return result; }
    public String getError() { return error; }
    public long getCreatedAt() { return createdAt; }
    public String getQuestion() { return question; }
    public List<Map<String, Object>> getSources() { return sources; }
}
