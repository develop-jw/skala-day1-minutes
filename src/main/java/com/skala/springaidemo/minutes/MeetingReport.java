package com.skala.springaidemo.minutes;

import java.util.List;

public record MeetingReport(
    String title,
    String summary,
    List<String> decisions,
    List<ActionItem> actionItems) {

    public record ActionItem(String owner, String task, String dueDate) {
        
    }
}
