package vn.ticketscenter.event.service;

import vn.ticketscenter.config.error.AppException;

public final class EventProblem extends AppException {
    public EventProblem(int status, String code, String message) { super(status, code, message); }
    public static EventProblem invalid(String message) { return new EventProblem(400,"INVALID_EVENT_INPUT",message); }
    public static EventProblem conflict(String message) { return new EventProblem(409,"EVENT_CONFLICT",message); }
    public static EventProblem unavailable() { return new EventProblem(503,"EVENT_DEPENDENCY_UNAVAILABLE","Chưa thể xử lý sự kiện. Vui lòng thử lại."); }
}
