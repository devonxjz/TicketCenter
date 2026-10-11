package vn.ticketscenter.payment.dto;

import org.json.JSONObject;

public record VnpayIpnResult(String rspCode, String message) {
    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("RspCode", rspCode);
        json.put("Message", message);
        return json.toString();
    }

    public static VnpayIpnResult success() {
        return new VnpayIpnResult("00", "Confirm Success");
    }

    public static VnpayIpnResult orderNotFound() {
        return new VnpayIpnResult("01", "Order Not Found");
    }

    public static VnpayIpnResult orderAlreadyConfirmed() {
        return new VnpayIpnResult("02", "Order already confirmed");
    }

    public static VnpayIpnResult invalidAmount() {
        return new VnpayIpnResult("04", "Invalid Amount");
    }

    public static VnpayIpnResult invalidChecksum() {
        return new VnpayIpnResult("97", "Invalid Checksum");
    }
}
