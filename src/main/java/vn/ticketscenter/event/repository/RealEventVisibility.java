package vn.ticketscenter.event.repository;

import vn.ticketscenter.config.ServerConfig;

/** Excludes explicit synthetic-data labels; absence of a label does not prove real provenance. */
public final class RealEventVisibility {
    private RealEventVisibility() { }

    public static String predicate(String alias) {
        if (!alias.matches("[A-Za-z][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid SQL alias");
        if (ServerConfig.isDemoData()) return "1=1";
        String text = "CONCAT(COALESCE(" + alias + ".title,N''),N' ',COALESCE("
                + alias + ".description,N'')) COLLATE Latin1_General_100_CI_AI";
        return java.util.stream.Stream.of("giả lập", "dữ liệu test", "dữ liệu mẫu", "dữ liệu demo",
                        "demo_data_created", "[demo]", "demo data", "simulated data", "synthetic data")
                .map(marker -> "CHARINDEX(N'" + marker + "'," + text + ")=0")
                .collect(java.util.stream.Collectors.joining(" AND ", "(", ")"));
    }
}
