"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { webcrypto } = require("node:crypto");
const { validateFile, fieldsForRequest, cloudCover, retryKey, fingerprint, editorMode, saveRequest } =
    require("../../main/webapp/assets/js/event-images.js");

const firstKey = "11111111-1111-4111-8111-111111111111";
const secondKey = "22222222-2222-4222-8222-222222222222";
const values = {
    title: " Chương trình A ", description: "Giới thiệu", categoryId: firstKey,
    venueName: "Nhà hát", venueAddress: "Hà Nội",
    saleStart: "2026-10-10T08:00:00+07:00", saleEnd: "2026-10-11T08:00:00+07:00",
    startTime: "2026-10-11T09:00:00+07:00", endTime: "2026-10-11T11:00:00+07:00"
};
function storage() {
    const map = new Map();
    return { getItem: key => map.get(key) || null, setItem: (key, value) => map.set(key, value) };
}
function file(bytes, type = "image/png") {
    return { type, size: bytes.length, arrayBuffer: async () => Uint8Array.from(bytes).buffer };
}

test("published editor changes only the cover with a version guard", () => {
    const current = { id: firstKey, status: "PUBLISHED", version: 4 };
    assert.equal(editorMode(current.status), "cover");
    assert.deepEqual(saveRequest(current, secondKey, { title: "Ignored change" }, file([1])), {
        path: "/api/events/" + firstKey + "/cover", fields: { expectedVersion: "4" }
    });
    assert.throws(() => saveRequest(current, secondKey, values, null), /Chọn ảnh mới/);
    assert.equal(editorMode("CANCELLED"), "readonly");
    assert.throws(() => saveRequest({ ...current, status: "PENDING_APPROVAL" }, secondKey, values, file([1])), /không cho phép/);
});

test("draft edit without a file keeps the image and uses the edit endpoint", () => {
    const result = saveRequest({ id: firstKey, status: "DRAFT", version: 2 }, secondKey, values, null);
    assert.equal(result.path, "/api/events/" + firstKey + "/edit");
    assert.equal(result.fields.expectedVersion, "2");
    assert.equal(result.fields.title, "Chương trình A");
});

test("editor converts local dates to UTC and sends only agreed Event fields", () => {
    assert.deepEqual(fieldsForRequest({ ...values, organizationId: secondKey, injected: "ignored" }), {
        title: "Chương trình A", description: "Giới thiệu", categoryId: firstKey,
        venueName: "Nhà hát", venueAddress: "Hà Nội",
        saleStart: "2026-10-10T01:00:00.000Z", saleEnd: "2026-10-11T01:00:00.000Z",
        startTime: "2026-10-11T02:00:00.000Z", endTime: "2026-10-11T04:00:00.000Z"
    });
});

test("invalid sale ordering is rejected before a request", () => {
    assert.throws(() => fieldsForRequest({ ...values, saleEnd: values.saleStart }),
        /Lịch phải/);
    assert.equal(fieldsForRequest({ ...values, saleEnd: values.startTime }).saleEnd,
        "2026-10-11T02:00:00.000Z");
});

test("same payload retains its retry key across reconstructed editor state", async () => {
    const store = storage();
    const fields = fieldsForRequest(values);
    const initial = await fingerprint(fields, file([137, 80, 78, 71]), webcrypto);
    assert.equal(retryKey(store, "create", initial, () => firstKey), firstKey);
    const reloaded = await fingerprint(fieldsForRequest(values), file([137, 80, 78, 71]), webcrypto);
    assert.equal(retryKey(store, "create", reloaded, () => secondKey), firstKey);
});

test("different image bytes, fields, and versions each rotate the retry key", async () => {
    const store = storage();
    const fields = fieldsForRequest(values);
    const initial = await fingerprint(fields, file([1, 2]), webcrypto);
    assert.equal(retryKey(store, "edit", initial, () => firstKey), firstKey);
    const changedImage = await fingerprint(fields, file([1, 3]), webcrypto);
    assert.equal(retryKey(store, "edit", changedImage, () => secondKey), secondKey);
    const changedFields = await fingerprint({ ...fields, title: "Chương trình B" }, file([1, 3]), webcrypto);
    assert.equal(retryKey(store, "edit", changedFields, () => firstKey), firstKey);
    const changedVersion = await fingerprint({ ...fields, expectedVersion: "8" }, file([1, 3]), webcrypto);
    assert.equal(retryKey(store, "edit", changedVersion, () => secondKey), secondKey);
});

test("no selected file produces a different request from a previous replacement", async () => {
    const fields = fieldsForRequest(values);
    const store = storage();
    assert.equal(retryKey(store, "edit", await fingerprint(fields, null, webcrypto), () => firstKey), firstKey);
    assert.equal(retryKey(store, "edit", await fingerprint(fields, file([1]), webcrypto), () => secondKey), secondKey);
});

test("cover renderer accepts only HTTPS Cloudinary image URLs", () => {
    assert.equal(cloudCover("https://res.cloudinary.com/demo/image/upload/v1/event.png"), true);
    for (const url of [null, "", "javascript:alert(1)", "http://res.cloudinary.com/demo/image/upload/x",
        "https://res.cloudinary.com.evil.test/demo/image/upload/x", "https://images.unsplash.com/x",
        "https://res.cloudinary.com/demo/raw/upload/x", "https://user:password@res.cloudinary.com/demo/image/upload/x"]) {
        assert.equal(cloudCover(url), false);
    }
});

test("image picker rejects wrong type, empty files, and oversized files", () => {
    assert.throws(() => validateFile({ type: "image/svg+xml", size: 10 }), /PNG hoặc JPEG/);
    assert.throws(() => validateFile({ type: "image/png", size: 0 }), /không có nội dung/);
    assert.throws(() => validateFile({ type: "image/jpeg", size: 5242881 }), /5 MiB/);
    assert.equal(validateFile({ type: "image/jpeg", size: 5242880 }), undefined);
});
