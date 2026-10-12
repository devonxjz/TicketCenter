package vn.ticketscenter.event.service;

public final class EventProblem extends RuntimeException {
    private final int status;
    private final String code;
    public EventProblem(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static EventProblem invalid(String message) { return new EventProblem(400,"INVALID_EVENT_INPUT",message); }
    public static EventProblem conflict(String message) { return new EventProblem(409,"EVENT_CONFLICT",message); }
    public static EventProblem unavailable() { return new EventProblem(503,"EVENT_DEPENDENCY_UNAVAILABLE","Chưa thể xử lý sự kiện. Vui lòng thử lại."); }
}
